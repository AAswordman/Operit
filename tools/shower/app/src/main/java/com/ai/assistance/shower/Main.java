package com.ai.assistance.shower;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaFormat;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.SystemClock;
import android.view.Surface;

import com.ai.assistance.shower.shell.FakeContext;
import com.ai.assistance.shower.shell.Workarounds;
import com.ai.assistance.shower.wrappers.ServiceManager;
import com.ai.assistance.shower.wrappers.WindowManager;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local WebSocket server which can create a virtual display via reflection and
 * stream frames back to the client.
 */
public class Main {

    private static final String TAG = "ShowerMain";
    private static final int DEFAULT_PORT = 8986;
    private static final int DEFAULT_BIT_RATE = 4_000_000;
    private static final int CODEC_SIZE_ALIGNMENT = 16;

    private static final String ACTION_SHOWER_BINDER_READY = "com.ai.assistance.operit.action.SHOWER_BINDER_READY";
    private static final String EXTRA_BINDER_CONTAINER = "binder_container";

    private static volatile String sTargetPackageName;

    private static ArrayList<String> getTargetPackages() {
        ArrayList<String> packages = new ArrayList<>();
        String raw = sTargetPackageName;
        if (raw == null || raw.trim().isEmpty()) {
            return packages;
        }

        String[] parts = raw.split(",");
        for (String part : parts) {
            if (part == null) {
                continue;
            }
            String pkg = part.trim();
            if (pkg.isEmpty()) {
                continue;
            }
            if (!packages.contains(pkg)) {
                packages.add(pkg);
            }
        }
        return packages;
    }

    private static final int VIRTUAL_DISPLAY_FLAG_PUBLIC = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
    private static final int VIRTUAL_DISPLAY_FLAG_PRESENTATION = DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION;
    private static final int VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY;
    private static final int VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH = 1 << 6;
    private static final int VIRTUAL_DISPLAY_FLAG_ROTATES_WITH_CONTENT = 1 << 7;
    private static final int VIRTUAL_DISPLAY_FLAG_DESTROY_CONTENT_ON_REMOVAL = 1 << 8;
    private static final int VIRTUAL_DISPLAY_FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS = 1 << 9;
    private static final int VIRTUAL_DISPLAY_FLAG_TRUSTED = 1 << 10;
    private static final int VIRTUAL_DISPLAY_FLAG_OWN_DISPLAY_GROUP = 1 << 11;
    private static final int VIRTUAL_DISPLAY_FLAG_ALWAYS_UNLOCKED = 1 << 12;
    private static final int VIRTUAL_DISPLAY_FLAG_TOUCH_FEEDBACK_DISABLED = 1 << 13;
    private static final int VIRTUAL_DISPLAY_FLAG_OWN_FOCUS = 1 << 14;
    private static final int VIRTUAL_DISPLAY_FLAG_DEVICE_DISPLAY_GROUP = 1 << 15;

    private static Main sInstance;

    private final Context appContext;
    private final InputController mainDisplayInputController;

    // Map of displayId -> DisplaySession
    private final Map<Integer, DisplaySession> displays = new ConcurrentHashMap<>();

    // A main-screen task has no video sink. Its Binder token must keep the input process alive
    // while the controller is planning, even when no RPC has been sent for more than 15 seconds.
    private final Map<IBinder, IBinder.DeathRecipient> inputClients = new ConcurrentHashMap<>();
    private static final long CLIENT_IDLE_TIMEOUT_MS = 15_000L;
    private volatile long lastClientActiveTime = SystemClock.elapsedRealtime();
    private Thread idleWatcherThread;

    private static PrintWriter fileLog;
    private static final SimpleDateFormat LOG_TIME_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
    // 当通过 main(String...) 以 CLI 方式启动时为 true，用于在服务停止后退出整个进程
    private static volatile boolean sExitOnStop = false;

    static synchronized void logToFile(String msg, Throwable t) {
        try {
            if (fileLog == null) {
                File logFile = new File("/data/local/tmp/shower.log");
                fileLog = new PrintWriter(new FileWriter(logFile, true), true);
            }
            long now = System.currentTimeMillis();
            String timestamp = LOG_TIME_FORMAT.format(new Date(now));
            String line = timestamp + " " + msg;
            fileLog.println(line);
            if (t != null) {
                t.printStackTrace(fileLog);
            }
        } catch (IOException e) {
            // For debugging: also print to stderr so we can see why the log file is not created.
            e.printStackTrace();
        }
    }

    private class DisplaySession {
        final int displayId;
        final VirtualDisplay virtualDisplay;
        final MediaCodec videoEncoder;
        final Surface encoderSurface;
        final Thread encoderThread;
        volatile boolean encoderRunning;
        final InputController inputController;
        IShowerVideoSink videoSink;
        IBinder videoSinkBinder;
        IBinder.DeathRecipient videoSinkDeathRecipient;
        final Object lock = new Object();

        DisplaySession(int displayId, VirtualDisplay virtualDisplay, MediaCodec videoEncoder, Surface encoderSurface, InputController inputController) {
            this.displayId = displayId;
            this.virtualDisplay = virtualDisplay;
            this.videoEncoder = videoEncoder;
            this.encoderSurface = encoderSurface;
            this.inputController = inputController;
            this.encoderRunning = true;
            this.encoderThread = new Thread(this::encodeLoop, "ShowerEncoder-" + displayId);
            this.encoderThread.start();
        }

        void release() {
            logToFile("Releasing display session " + displayId, null);
            stopEncoder();
            
            if (virtualDisplay != null) {
                virtualDisplay.release();
            }
            
            if (inputController != null) {
                // Reset input controller display ID, though it matters less as we discard it
                inputController.setDisplayId(0);
            }
            
            setVideoSink(null);
        }

        private void stopEncoder() {
            encoderRunning = false;
            MediaCodec codec = videoEncoder;
            if (codec != null) {
                try {
                    codec.signalEndOfInputStream();
                } catch (Exception e) {
                    logToFile("signalEndOfInputStream failed: " + e.getMessage(), e);
                }
            }
            if (encoderThread != null) {
                try {
                    encoderThread.join(1000);
                } catch (InterruptedException e) {
                    logToFile("Encoder thread join interrupted: " + e.getMessage(), e);
                    Thread.currentThread().interrupt();
                }
            }
            if (codec != null) {
                try {
                    codec.stop();
                } catch (Exception e) {
                    logToFile("Error stopping codec: " + e.getMessage(), e);
                }
                codec.release();
            }
            if (encoderSurface != null) {
                encoderSurface.release();
            }
        }

        void setVideoSink(IBinder sink) {
            synchronized (lock) {
                if (videoSinkBinder != null && videoSinkBinder != sink && videoSinkDeathRecipient != null) {
                    try {
                        videoSinkBinder.unlinkToDeath(videoSinkDeathRecipient, 0);
                    } catch (Throwable t) {
                        logToFile("unlinkToDeath previous video sink failed: " + t.getMessage(), t);
                    }
                    videoSinkBinder = null;
                    videoSinkDeathRecipient = null;
                }
                if (sink == null) {
                    videoSink = null;
                    videoSinkBinder = null;
                    videoSinkDeathRecipient = null;
                    return;
                }
                videoSinkBinder = sink;
                videoSinkDeathRecipient = () -> {
                    synchronized (lock) {
                        logToFile("Video sink binder died, clearing sink for display " + displayId, null);
                        videoSink = null;
                        videoSinkBinder = null;
                        videoSinkDeathRecipient = null;
                    }
                };
                try {
                    sink.linkToDeath(videoSinkDeathRecipient, 0);
                } catch (Throwable t) {
                    logToFile("linkToDeath for video sink failed: " + t.getMessage(), t);
                }
                videoSink = IShowerVideoSink.Stub.asInterface(sink);
            }
        }

        private void encodeLoop() {
            MediaCodec codec = videoEncoder;
            if (codec == null) {
                return;
            }

            BufferInfo bufferInfo = new BufferInfo();

            while (encoderRunning) {
                int index;
                try {
                    index = codec.dequeueOutputBuffer(bufferInfo, 10_000);
                } catch (IllegalStateException e) {
                    logToFile("dequeueOutputBuffer failed: " + e.getMessage(), e);
                    break;
                }

                if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    continue;
                } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat format = codec.getOutputFormat();
                    trySendConfig(format);
                } else if (index >= 0) {
                    if (bufferInfo.size > 0) {
                        ByteBuffer outputBuffer = codec.getOutputBuffer(index);
                        if (outputBuffer != null) {
                            outputBuffer.position(bufferInfo.offset);
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                            byte[] data = new byte[bufferInfo.size];
                            outputBuffer.get(data);
                            sendVideoFrame(data);
                        }
                    }
                    codec.releaseOutputBuffer(index, false);

                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break;
                    }
                }
            }
        }

        private void trySendConfig(MediaFormat format) {
            ByteBuffer csd0 = format.getByteBuffer("csd-0");
            ByteBuffer csd1 = format.getByteBuffer("csd-1");
            sendVideoFrame(csd0);
            sendVideoFrame(csd1);
        }

        private void sendVideoFrame(ByteBuffer buffer) {
            if (buffer == null || !buffer.hasRemaining()) {
                return;
            }
            ByteBuffer dup = buffer.duplicate();
            dup.position(0);
            byte[] data = new byte[dup.remaining()];
            dup.get(data);
            sendVideoFrame(data);
        }

        private void sendVideoFrame(byte[] data) {
            IShowerVideoSink sink;
             synchronized (lock) {
                 sink = videoSink;
             }
            if (sink != null) {
                try {
                    sink.onVideoFrame(data);
                } catch (Exception e) {
                    // Client may have died, invalidate the sink.
                    synchronized (lock) {
                        videoSink = null;
                    }
                }
            }
        }
    }

    private byte[] captureScreenshotBytes(int displayId) {
        logToFile("captureScreenshotBytes using DisplayCapture for displayId=" + displayId, null);
        return DisplayCapture.captureDisplay(displayId);
    }


    private void markClientActive() {
        lastClientActiveTime = SystemClock.elapsedRealtime();
    }

    private void registerInputClient(IBinder client) throws RemoteException {
        if (client == null) throw new IllegalArgumentException("Input client token is required");
        synchronized (inputClients) {
            if (!inputClients.containsKey(client)) {
                IBinder.DeathRecipient recipient = () -> unregisterInputClient(client);
                client.linkToDeath(recipient, 0);
                inputClients.put(client, recipient);
            }
            markClientActive();
            logToFile("Input client registered, active=" + inputClients.size(), null);
        }
    }

    private void unregisterInputClient(IBinder client) {
        if (client == null) throw new IllegalArgumentException("Input client token is required");
        synchronized (inputClients) {
            IBinder.DeathRecipient recipient = inputClients.remove(client);
            if (recipient != null) client.unlinkToDeath(recipient, 0);
            markClientActive();
            logToFile("Input client released, active=" + inputClients.size(), null);
        }
    }

    private void startIdleWatcher() {
        idleWatcherThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(1000L);
                    } catch (InterruptedException e) {
                        return;
                    }
                    long now = SystemClock.elapsedRealtime();
                    // Check if *any* display has a sink
                    boolean hasActiveSink = false;
                    for (DisplaySession session : displays.values()) {
                        synchronized(session.lock) {
                            if (session.videoSink != null) {
                                hasActiveSink = true;
                                break;
                            }
                        }
                    }
                    
                    if (!hasActiveSink && now - lastClientActiveTime > CLIENT_IDLE_TIMEOUT_MS) {
                        synchronized (inputClients) {
                            if (inputClients.isEmpty() && SystemClock.elapsedRealtime() - lastClientActiveTime > CLIENT_IDLE_TIMEOUT_MS) {
                                logToFile("No active Binder clients for " + CLIENT_IDLE_TIMEOUT_MS + "ms, exiting", null);
                                System.exit(0);
                            }
                        }
                    }
                }
            }
        }, "ShowerIdleWatcher");
        idleWatcherThread.setDaemon(true);
        idleWatcherThread.start();
    }


    private static void prepareMainLooper() {
        Looper.prepare();
        synchronized (Looper.class) {
            try {
                Field field = Looper.class.getDeclaredField("sMainLooper");
                field.setAccessible(true);
                field.set(null, Looper.myLooper());
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }
    }

    public static void main(String... args) {
        sExitOnStop = true;
        if (args != null && args.length > 0 && args[0] != null && !args[0].trim().isEmpty()) {
            sTargetPackageName = args[0].trim();
            logToFile("Using target package arg from args: " + sTargetPackageName, null);
        }
        try {
            prepareMainLooper();
            logToFile("prepareMainLooper ok", null);
        } catch (Throwable t) {
            logToFile("prepareMainLooper failed: " + t.getMessage(), t);
        }

        try {
            Workarounds.apply();
            logToFile("Workarounds.apply ok", null);
        } catch (Throwable t) {
            logToFile("Workarounds.apply failed: " + t.getMessage(), t);
        }

        Context context = FakeContext.get();
        sInstance = new Main(context);
        logToFile("server started (Binder only mode)", null);

        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            logToFile("Main thread interrupted: " + e.getMessage(), e);
        }
    }

    public Main(Context context) {
        this.appContext = context.getApplicationContext();
        sInstance = this;
        InputController tmpMainInputController;
        try {
            tmpMainInputController = new InputController();
            tmpMainInputController.setDisplayId(0);
            logToFile("Main display InputController initialized", null);
        } catch (Throwable t) {
            tmpMainInputController = null;
            logToFile("Failed to init main display InputController: " + t.getMessage(), t);
        }
        this.mainDisplayInputController = tmpMainInputController;

        try {
            IShowerService service = new IShowerService.Stub() {
                @Override
                public int ensureDisplay(int width, int height, int dpi, int bitrateKbps) {
                    markClientActive();
                    int bitRate = bitrateKbps > 0 ? bitrateKbps * 1000 : DEFAULT_BIT_RATE;
                    return createVirtualDisplay(width, height, dpi, bitRate);
                }

                @Override
                public void destroyDisplay(int displayId) {
                    markClientActive();
                    releaseDisplay(displayId);
                }

                @Override
                public void launchApp(String packageName, int displayId) {
                    markClientActive();
                    if (packageName != null && !packageName.isEmpty()) {
                        launchPackageOnVirtualDisplay(packageName, displayId);
                    }
                }

                @Override
                public void registerInputClient(IBinder client) throws RemoteException {
                    Main.this.registerInputClient(client);
                }

                @Override
                public void unregisterInputClient(IBinder client) {
                    Main.this.unregisterInputClient(client);
                }

                @Override
                public void tap(int displayId, float x, float y) {
                    markClientActive();
                    InputController controller = requireInputController(displayId);
                    controller.injectTap(x, y);
                    logToFile("Binder TAP injected: " + x + "," + y + " on " + displayId, null);
                }

                @Override
                public void swipe(int displayId, float x1, float y1, float x2, float y2, long durationMs) {
                    markClientActive();
                    InputController controller = requireInputController(displayId);
                    controller.injectSwipe(x1, y1, x2, y2, durationMs);
                    logToFile("Binder SWIPE injected on " + displayId, null);
                }

                @Override
                public void touchDown(int displayId, float x, float y) {
                    markClientActive();
                    InputController controller = requireInputController(displayId);
                    controller.touchDown(x, y);
                }

                @Override
                public void touchMove(int displayId, float x, float y) {
                    markClientActive();
                    InputController controller = requireInputController(displayId);
                    controller.touchMove(x, y);
                }

                @Override
                public void touchUp(int displayId, float x, float y) {
                    markClientActive();
                    InputController controller = requireInputController(displayId);
                    controller.touchUp(x, y);
                }

                @Override
                public void injectTouchEvent(
                        int displayId,
                        int action,
                        float x,
                        float y,
                        long downTime,
                        long eventTime,
                        float pressure,
                        float size,
                        int metaState,
                        float xPrecision,
                        float yPrecision,
                        int deviceId,
                        int edgeFlags
                ) {
                    markClientActive();
                    InputController controller = requireInputController(displayId);
                    controller.injectTouchEventFull(
                            action,
                            x,
                            y,
                            downTime,
                            eventTime,
                            pressure,
                            size,
                            metaState,
                            xPrecision,
                            yPrecision,
                            deviceId,
                            edgeFlags
                    );
                }

                @Override
                public void injectKey(int displayId, int keyCode) {
                    markClientActive();
                    InputController controller = requireInputController(displayId);
                    controller.injectKey(keyCode);
                    logToFile("Binder KEY injected: " + keyCode + " on " + displayId, null);
                }

                @Override
                public void injectKeyWithMeta(int displayId, int keyCode, int metaState) {
                    markClientActive();
                    InputController controller = requireInputController(displayId);
                    controller.injectKeyWithMeta(keyCode, metaState);
                    logToFile(
                            "Binder KEY(meta=" + metaState + ") injected: " + keyCode + " on " + displayId,
                            null
                    );
                }

                @Override
                public byte[] requestScreenshot(int displayId) {
                    markClientActive();
                    return captureScreenshotBytes(displayId);
                }

                @Override
                public void setVideoSink(int displayId, IBinder sink) {
                    markClientActive();
                    DisplaySession session = displays.get(displayId);
                    if (session != null) {
                        session.setVideoSink(sink);
                    } else {
                        logToFile("setVideoSink for unknown displayId: " + displayId, null);
                    }
                }
            };
            logToFile("Skip ServiceManager.addService by design (Shizuku-style binder handoff)", null);

            sendBinderToApp(service);
        } catch (Throwable t) {
            logToFile("Failed to initialize Shower Binder service: " + t.getMessage(), t);
        }

        startIdleWatcher();
    }

    private InputController resolveInputController(int displayId) {
        if (displayId == 0) {
            return mainDisplayInputController;
        }
        DisplaySession session = displays.get(displayId);
        return session != null ? session.inputController : null;
    }

    private InputController requireInputController(int displayId) {
        InputController controller = resolveInputController(displayId);
        if (controller == null) {
            throw new IllegalStateException("No InputController for displayId=" + displayId);
        }
        return controller;
    }

    public static Main start(Context context) {
        if (context != null) {
            sTargetPackageName = context.getPackageName();
            logToFile("Using target package from context: " + sTargetPackageName, null);
        }
        Main main = new Main(context);
        logToFile("server started (Binder only mode via Activity)", null);
        return main;
    }


    private static Object[] buildBroadcastIntentArgs(Class<?>[] parameterTypes, Intent intent) {
        Object[] args = new Object[parameterTypes.length];
        int intentIndex = -1;
        for (int i = 0; i < parameterTypes.length; i++) {
            if (Intent.class.isAssignableFrom(parameterTypes[i])) {
                intentIndex = i;
                break;
            }
        }

        int intArgIndex = 0;
        boolean callingPackageSet = false;
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> type = parameterTypes[i];
            if (Intent.class.isAssignableFrom(type)) {
                args[i] = intent;
                continue;
            }

            if (type == String.class) {
                if (!callingPackageSet && intentIndex >= 0 && i < intentIndex) {
                    args[i] = FakeContext.PACKAGE_NAME;
                    callingPackageSet = true;
                } else {
                    args[i] = null;
                }
                continue;
            }

            if (type == int.class) {
                if (intArgIndex == 1) {
                    args[i] = -1; // appOp = OP_NONE
                } else if (intArgIndex >= 2) {
                    args[i] = -2; // userId = USER_CURRENT for tail int arguments
                } else {
                    args[i] = 0;
                }
                intArgIndex++;
                continue;
            }

            if (type == boolean.class) {
                args[i] = Boolean.FALSE;
                continue;
            }

            if (type == long.class) {
                args[i] = 0L;
                continue;
            }

            if (type == float.class) {
                args[i] = 0f;
                continue;
            }

            if (type == double.class) {
                args[i] = 0d;
                continue;
            }

            if (type == byte.class) {
                args[i] = (byte) 0;
                continue;
            }

            if (type == short.class) {
                args[i] = (short) 0;
                continue;
            }

            if (type == char.class) {
                args[i] = (char) 0;
                continue;
            }

            args[i] = null;
        }

        return args;
    }

    private boolean sendBinderReadyViaActivityManager(Intent intent) {
        try {
            Class<?> serviceManagerClass = Class.forName("android.os.ServiceManager");
            Method getServiceMethod = serviceManagerClass.getDeclaredMethod("getService", String.class);
            IBinder amBinder = (IBinder) getServiceMethod.invoke(null, "activity");
            if (amBinder == null) {
                logToFile("IActivityManager binder is null", null);
                return false;
            }

            Object activityManager = null;
            try {
                Class<?> iActivityManagerStubClass = Class.forName("android.app.IActivityManager$Stub");
                Method asInterfaceMethod = iActivityManagerStubClass.getDeclaredMethod("asInterface", IBinder.class);
                activityManager = asInterfaceMethod.invoke(null, amBinder);
            } catch (Throwable ignored) {
            }

            if (activityManager == null) {
                try {
                    Class<?> activityManagerNativeClass = Class.forName("android.app.ActivityManagerNative");
                    Method asInterfaceMethod = activityManagerNativeClass.getDeclaredMethod("asInterface", IBinder.class);
                    activityManager = asInterfaceMethod.invoke(null, amBinder);
                } catch (Throwable ignored) {
                }
            }

            if (activityManager == null) {
                logToFile("Unable to obtain IActivityManager instance", null);
                return false;
            }

            ArrayList<Method> candidates = new ArrayList<>();
            for (Method method : activityManager.getClass().getMethods()) {
                if (!"broadcastIntent".equals(method.getName())) {
                    continue;
                }
                Class<?>[] parameterTypes = method.getParameterTypes();
                boolean hasIntent = false;
                for (Class<?> parameterType : parameterTypes) {
                    if (Intent.class.isAssignableFrom(parameterType)) {
                        hasIntent = true;
                        break;
                    }
                }
                if (hasIntent) {
                    candidates.add(method);
                }
            }

            if (candidates.isEmpty()) {
                logToFile("No broadcastIntent method found on IActivityManager", null);
                return false;
            }

            Collections.sort(candidates, new Comparator<Method>() {
                @Override
                public int compare(Method a, Method b) {
                    return Integer.compare(b.getParameterCount(), a.getParameterCount());
                }
            });

            Throwable lastError = null;
            for (Method method : candidates) {
                try {
                    method.setAccessible(true);
                    Object[] args = buildBroadcastIntentArgs(method.getParameterTypes(), intent);
                    method.invoke(activityManager, args);
                    logToFile("Sent SHOWER_BINDER_READY via IActivityManager." + method.getParameterCount() + "args", null);
                    return true;
                } catch (InvocationTargetException ite) {
                    lastError = ite.getCause() != null ? ite.getCause() : ite;
                    logToFile(
                            "IActivityManager.broadcastIntent failed for " + method.getParameterCount() + " args: "
                                    + lastError.getMessage(),
                            lastError
                    );
                } catch (Throwable t) {
                    lastError = t;
                    logToFile(
                            "IActivityManager.broadcastIntent invoke error for " + method.getParameterCount() + " args: "
                                    + t.getMessage(),
                            t
                    );
                }
            }

            if (lastError != null) {
                logToFile("All IActivityManager.broadcastIntent attempts failed", lastError);
            }
            return false;
        } catch (Throwable t) {
            logToFile("sendBinderReadyViaActivityManager failed: " + t.getMessage(), t);
            return false;
        }
    }

    private static int alignToCodecBlockSize(int value) {
        return ((value + CODEC_SIZE_ALIGNMENT - 1) / CODEC_SIZE_ALIGNMENT) * CODEC_SIZE_ALIGNMENT;
    }


    private void sendBinderToApp(IShowerService service) {
        try {
            Intent baseIntent = new Intent(ACTION_SHOWER_BINDER_READY);
            baseIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            baseIntent.putExtra(EXTRA_BINDER_CONTAINER, new ShowerBinderContainer(service.asBinder()));

            ArrayList<String> targetPackages = getTargetPackages();
            if (targetPackages.isEmpty()) {
                boolean sent = sendBinderReadyViaActivityManager(baseIntent);
                if (!sent) {
                    logToFile("Failed to send SHOWER_BINDER_READY: ActivityManager path unavailable", null);
                }
                return;
            }

            boolean anySent = false;
            for (String pkg : targetPackages) {
                Intent targetedIntent = new Intent(baseIntent);
                targetedIntent.setPackage(pkg);
                boolean sent = sendBinderReadyViaActivityManager(targetedIntent);
                anySent = anySent || sent;
            }

            if (!anySent) {
                logToFile("Failed to send SHOWER_BINDER_READY to target packages: " + targetPackages, null);
            }
        } catch (Throwable t) {
            logToFile("Failed to send SHOWER_BINDER_READY broadcast: " + t.getMessage(), t);
        }
    }


    private synchronized int createVirtualDisplay(int width, int height, int dpi, int bitRate) {
        logToFile("ensureVirtualDisplay requested: " + width + "x" + height + " dpi=" + dpi + " bitRate=" + bitRate, null);
        
        // Removed check for existing display, we now support multiple.
        
        MediaCodec videoEncoder = null;
        Surface encoderSurface = null;
        VirtualDisplay virtualDisplay = null;
        InputController inputController = null;
        int virtualDisplayId = -1;

        // Use RGBA_8888 so that we can easily convert to Bitmap
        try {
            int actualBitRate = bitRate > 0 ? bitRate : DEFAULT_BIT_RATE;

            int alignedWidth = alignToCodecBlockSize(width);
            int alignedHeight = alignToCodecBlockSize(height);

            logToFile("createVirtualDisplay using aligned size: " + alignedWidth + "x" + alignedHeight, null);

            MediaFormat format = MediaFormat.createVideoFormat("video/avc", alignedWidth, alignedHeight);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE, actualBitRate);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

            videoEncoder = MediaCodec.createEncoderByType("video/avc");
            videoEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoderSurface = videoEncoder.createInputSurface();
            videoEncoder.start();

            int flags = VIRTUAL_DISPLAY_FLAG_PUBLIC
                    | VIRTUAL_DISPLAY_FLAG_PRESENTATION
                    | VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                    | VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH
                    | VIRTUAL_DISPLAY_FLAG_ROTATES_WITH_CONTENT
                    | VIRTUAL_DISPLAY_FLAG_DESTROY_CONTENT_ON_REMOVAL;

            if (Build.VERSION.SDK_INT >= 33) {
                flags |= VIRTUAL_DISPLAY_FLAG_TRUSTED
                        | VIRTUAL_DISPLAY_FLAG_OWN_DISPLAY_GROUP
                        | VIRTUAL_DISPLAY_FLAG_ALWAYS_UNLOCKED
                        | VIRTUAL_DISPLAY_FLAG_TOUCH_FEEDBACK_DISABLED;
            }

            if (Build.VERSION.SDK_INT >= 34) {
                flags |= VIRTUAL_DISPLAY_FLAG_OWN_FOCUS
                        | VIRTUAL_DISPLAY_FLAG_DEVICE_DISPLAY_GROUP;
            }

            java.lang.reflect.Constructor<DisplayManager> ctor = DisplayManager.class.getDeclaredConstructor(Context.class);
            ctor.setAccessible(true);
            DisplayManager dm = ctor.newInstance(FakeContext.get());

            virtualDisplay = dm.createVirtualDisplay(
                    "ShowerVirtualDisplay-" + System.currentTimeMillis() % 1000,
                    alignedWidth,
                    alignedHeight,
                    dpi,
                    encoderSurface,
                    flags
            );

            if (virtualDisplay != null && virtualDisplay.getDisplay() != null) {
                virtualDisplayId = virtualDisplay.getDisplay().getDisplayId();
                logToFile("Created virtual display id=" + virtualDisplayId, null);
                try {
                    WindowManager wm = ServiceManager.getWindowManager();
                    wm.setDisplayImePolicy(virtualDisplayId, WindowManager.DISPLAY_IME_POLICY_LOCAL);
                    logToFile("WindowManager.setDisplayImePolicy LOCAL for display=" + virtualDisplayId, null);
                } catch (Throwable t) {
                    logToFile("setDisplayImePolicy failed: " + t.getMessage(), t);
                }
            } else {
                logToFile("Failed to get virtual display id", null);
                virtualDisplayId = -1;
            }

            if (virtualDisplayId != -1) {
                try {
                     inputController = new InputController();
                     inputController.setDisplayId(virtualDisplayId);
                } catch (Throwable t) {
                    logToFile("Failed to init InputController: " + t.getMessage(), t);
                    inputController = null;
                }
                
                DisplaySession session = new DisplaySession(virtualDisplayId, virtualDisplay, videoEncoder, encoderSurface, inputController);
                displays.put(virtualDisplayId, session);
                logToFile("Registered DisplaySession for id=" + virtualDisplayId, null);
                return virtualDisplayId;
            }
            
            // Clean up if failure
            videoEncoder.stop();
            videoEncoder.release();
            encoderSurface.release();
            return -1;

        } catch (Exception e) {
            logToFile("Failed to create virtual display or encoder: " + e.getMessage(), e);
            if (videoEncoder != null) videoEncoder.release();
            if (encoderSurface != null) encoderSurface.release();
            return -1;
        }
    }


    private synchronized void releaseDisplay(int displayId) {
        DisplaySession session = displays.remove(displayId);
        if (session != null) {
             session.release();
        } else {
            logToFile("releaseDisplay ignored for unknown id=" + displayId, null);
        }
    }

    private void launchPackageOnVirtualDisplay(String packageName, int displayId) {
        logToFile("launchPackageOnVirtualDisplay: " + packageName + " on " + displayId, null);
        try {
            if (displayId == -1) {
                logToFile("launchPackageOnVirtualDisplay: invalid displayId", null);
                return;
            }

            PackageManager pm = appContext.getPackageManager();
            Intent intent = pm.getLaunchIntentForPackage(packageName);
            if (intent == null) {
                logToFile("launchPackageOnVirtualDisplay: no launch intent for " + packageName, null);
                return;
            }

            // Use the same task-preserving entry as the launcher on the selected display.
            intent.setAction(Intent.ACTION_MAIN);
            intent.removeCategory(Intent.CATEGORY_INFO);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

            android.os.Bundle options = null;
            if (Build.VERSION.SDK_INT >= 26) {
                android.app.ActivityOptions launchOptions = android.app.ActivityOptions.makeBasic();
                launchOptions.setLaunchDisplayId(displayId);
                options = launchOptions.toBundle();
            }

            com.ai.assistance.shower.wrappers.ActivityManager am = ServiceManager.getActivityManager();
            am.startActivity(intent, options);

            logToFile("launchPackageOnVirtualDisplay: started " + packageName + " on display " + displayId, null);
        } catch (Exception e) {
            logToFile("launchPackageOnVirtualDisplay failed: " + e.getMessage(), e);
        }
    }

}
