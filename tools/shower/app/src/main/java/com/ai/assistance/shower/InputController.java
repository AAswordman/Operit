package com.ai.assistance.shower;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.Random;

class InputController {

    private static final String TAG = "ShowerInput";

    private static final float DEFAULT_TAP_POSITION_JITTER_PX = 1.5f;
    private static final float DEFAULT_MOVE_POSITION_JITTER_PX = 1.0f;
    private static final float DEFAULT_PRESSURE_JITTER = 0.06f;
    private static final float DEFAULT_SIZE_JITTER = 0.06f;
    private static final long DEFAULT_TAP_UP_DELAY_MIN_MS = 50L;
    private static final long DEFAULT_TAP_UP_DELAY_MAX_MS = 80L;
    private static final long DEFAULT_SWIPE_DURATION_JITTER_MS = 45L;
    private static final int DEFAULT_SWIPE_STEPS_MIN = 8;
    private static final int DEFAULT_SWIPE_STEPS_MAX = 14;

    private final Random random = new Random();

    private volatile boolean fuzzingEnabled = true;
    private volatile float tapPositionJitterPx = DEFAULT_TAP_POSITION_JITTER_PX;
    private volatile float movePositionJitterPx = DEFAULT_MOVE_POSITION_JITTER_PX;
    private volatile float pressureJitter = DEFAULT_PRESSURE_JITTER;
    private volatile float sizeJitter = DEFAULT_SIZE_JITTER;
    private volatile long tapUpDelayMinMs = DEFAULT_TAP_UP_DELAY_MIN_MS;
    private volatile long tapUpDelayMaxMs = DEFAULT_TAP_UP_DELAY_MAX_MS;
    private volatile long swipeDurationJitterMs = DEFAULT_SWIPE_DURATION_JITTER_MS;
    private volatile int swipeStepsMin = DEFAULT_SWIPE_STEPS_MIN;
    private volatile int swipeStepsMax = DEFAULT_SWIPE_STEPS_MAX;

    private final Object inputManager;
    private final Method injectInputEventMethod;
    private final Method setDisplayIdMethod;
    private int displayId;
    private boolean touchActive;
    private long touchDownTime;

    InputController() {
        try {
            Class<?> clazz = Class.forName("android.hardware.input.InputManager");
            Method getInstance = clazz.getDeclaredMethod("getInstance");
            getInstance.setAccessible(true);
            inputManager = getInstance.invoke(null);

            injectInputEventMethod = clazz.getDeclaredMethod("injectInputEvent", InputEvent.class, int.class);
            injectInputEventMethod.setAccessible(true);

            Method m;
            try {
                m = InputEvent.class.getMethod("setDisplayId", int.class);
            } catch (NoSuchMethodException e) {
                m = null;
            }
            setDisplayIdMethod = m;
            displayId = 0;
            Main.logToFile("InputController initialized, setDisplayIdMethod=" + (setDisplayIdMethod != null), null);
        } catch (Exception e) {
            throw new RuntimeException("Init InputController failed", e);
        }
    }

    void setDisplayId(int displayId) {
        this.displayId = displayId;
        Main.logToFile("InputController.setDisplayId: " + displayId, null);
    }

    void setFuzzingEnabled(boolean enabled) {
        fuzzingEnabled = enabled;
        Main.logToFile("InputController.setFuzzingEnabled: " + enabled, null);
    }

    private float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }

    private float nextFloat(float min, float max) {
        if (max <= min) return min;
        return min + (random.nextFloat() * (max - min));
    }

    private long nextLong(long min, long max) {
        if (max <= min) return min;
        double r = random.nextDouble();
        return min + (long) Math.floor(r * (double) (max - min + 1));
    }

    private float jitter(float value, float range) {
        if (range <= 0f) return value;
        return value + nextFloat(-range, range);
    }

    private float randomPressure() {
        float base = 1.0f;
        float p = fuzzingEnabled ? jitter(base, pressureJitter) : base;
        return clamp(p, 0.05f, 1.0f);
    }

    private float randomSize() {
        float base = 1.0f;
        float s = fuzzingEnabled ? jitter(base, sizeJitter) : base;
        return clamp(s, 0.05f, 1.0f);
    }

    private static float smoothstep(float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        return t * t * (3f - 2f * t);
    }

    private void inject(InputEvent event) {
        try {
            int currentDisplayId = displayId;
            if (setDisplayIdMethod != null && currentDisplayId != 0) {
                try {
                    setDisplayIdMethod.invoke(event, currentDisplayId);
                } catch (Exception e) {
                    Log.e(TAG, "setDisplayId failed", e);
                    Main.logToFile("InputController.setDisplayId failed: " + e.getMessage(), e);
                }
            }

            boolean ok;
            try {
                ok = (Boolean) injectInputEventMethod.invoke(inputManager, event, 0);
            } catch (java.lang.reflect.InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof SecurityException) {
                    String msg = cause.getMessage();
                    Log.e(TAG, "inject SecurityException: " + msg, cause);
                    Main.logToFile("InputController.inject SecurityException: " + msg, cause);
                } else {
                    Log.e(TAG, "inject InvocationTargetException", e);
                    Main.logToFile("InputController.inject InvocationTargetException: " + e.getMessage(), e);
                }
                return;
            }

            if (!ok) {
                Log.e(TAG, "inject returned false");
                Main.logToFile("InputController.inject returned false for event=" + event, null);
            }
        } catch (Exception e) {
            Log.e(TAG, "inject failed", e);
            Main.logToFile("InputController.inject failed: " + e.getMessage(), e);
        }
    }

    void injectKey(int keyCode) {
        injectKeyWithMeta(keyCode, 0);
    }

    void injectKeyWithMeta(int keyCode, int metaState) {
        long now = SystemClock.uptimeMillis();
        KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, metaState);
        down.setSource(InputDevice.SOURCE_KEYBOARD);
        inject(down);

        KeyEvent up = new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, metaState);
        up.setSource(InputDevice.SOURCE_KEYBOARD);
        inject(up);
    }

    private void injectTapEvent(MotionEvent event) {
        try {
            if (setDisplayIdMethod != null) {
                setDisplayIdMethod.invoke(event, displayId);
            } else if (displayId != 0) {
                throw new IllegalStateException("Cannot assign tap to display " + displayId);
            }
            // WAIT_FOR_FINISH: do not acknowledge the Binder TAP before Android has finished
            // dispatching this event. An accepted injection still needs app-side verification.
            boolean confirmed = (Boolean) injectInputEventMethod.invoke(inputManager, event, 2);
            if (!confirmed) {
                throw new IllegalStateException("InputManager rejected tap action " + event.getAction());
            }
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw new IllegalStateException("Tap injection failed on display " + displayId, e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Tap injection failed on display " + displayId, e);
        } finally {
            event.recycle();
        }
    }

    private MotionEvent tapEvent(long downTime, int action, float x, float y, float pressure, float size) {
        MotionEvent event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action,
                x, y, pressure, size, 0, 1.0f, 1.0f, 0, 0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        return event;
    }

    synchronized void injectTap(float x, float y) {
        float px = fuzzingEnabled ? jitter(x, tapPositionJitterPx) : x;
        float py = fuzzingEnabled ? jitter(y, tapPositionJitterPx) : y;
        float pressure = randomPressure();
        float size = randomSize();
        long delayMin = Math.max(40L, tapUpDelayMinMs);
        long delayMax = Math.max(delayMin, tapUpDelayMaxMs);
        long pressDuration = fuzzingEnabled ? nextLong(delayMin, delayMax) : 60L;
        long downTime = SystemClock.uptimeMillis();
        injectTapEvent(tapEvent(downTime, MotionEvent.ACTION_DOWN, px, py, pressure, size));
        boolean released = false;
        try {
            // A backdated timestamp does not create a physical press interval. Keep the stream
            // down in real time, on this Binder thread, before releasing the same pointer.
            SystemClock.sleep(pressDuration);
            injectTapEvent(tapEvent(downTime, MotionEvent.ACTION_UP, px, py, 0f, size));
            released = true;
        } finally {
            if (!released) {
                try {
                    // Release an incomplete touch stream; this is not another click attempt.
                    injectTapEvent(tapEvent(downTime, MotionEvent.ACTION_CANCEL, px, py, 0f, size));
                } catch (RuntimeException cleanupError) {
                    Main.logToFile("Failed to cancel incomplete tap: " + cleanupError.getMessage(), cleanupError);
                }
            }
        }
    }

    void injectSwipe(float x1, float y1, float x2, float y2, long durationMs) {
        long end = SystemClock.uptimeMillis();

        long baseDuration = Math.max(1L, durationMs);
        long jitterMs = Math.max(0L, swipeDurationJitterMs);
        long actualDuration = fuzzingEnabled ? Math.max(1L, baseDuration + nextLong(-jitterMs, jitterMs)) : baseDuration;
        long start = Math.max(0L, end - actualDuration);

        int minSteps = Math.max(1, swipeStepsMin);
        int maxSteps = Math.max(minSteps, swipeStepsMax);
        int steps = fuzzingEnabled ? (int) nextLong(minSteps, maxSteps) : 10;
        if (steps < 1) steps = 1;

        float sx = fuzzingEnabled ? jitter(x1, tapPositionJitterPx) : x1;
        float sy = fuzzingEnabled ? jitter(y1, tapPositionJitterPx) : y1;
        float ex = fuzzingEnabled ? jitter(x2, tapPositionJitterPx) : x2;
        float ey = fuzzingEnabled ? jitter(y2, tapPositionJitterPx) : y2;

        // down
        MotionEvent down = MotionEvent.obtain(
                start,
                start,
                MotionEvent.ACTION_DOWN,
                sx,
                sy,
                randomPressure(),
                randomSize(),
                0,
                1.0f,
                1.0f,
                0,
                0
        );
        down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        inject(down);

        for (int i = 1; i <= steps; i++) {
            float t = i / (float) steps;
            float te = smoothstep(t);
            float x = sx + (ex - sx) * te;
            float y = sy + (ey - sy) * te;

            if (fuzzingEnabled) {
                float atten = 1f - Math.abs(2f * t - 1f);
                float jitterRange = movePositionJitterPx * atten;
                x = jitter(x, jitterRange);
                y = jitter(y, jitterRange);
            }

            long now = start + (long) ((end - start) * t);

            MotionEvent move = MotionEvent.obtain(
                    start,
                    now,
                    MotionEvent.ACTION_MOVE,
                    x,
                    y,
                    randomPressure(),
                    randomSize(),
                    0,
                    1.0f,
                    1.0f,
                    0,
                    0
            );
            move.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            inject(move);
        }

        long upTime = end;
        MotionEvent up = MotionEvent.obtain(
                start,
                upTime,
                MotionEvent.ACTION_UP,
                ex,
                ey,
                randomPressure(),
                randomSize(),
                0,
                1.0f,
                1.0f,
                0,
                0
        );
        up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        inject(up);
    }

    void injectTouchEventFull(
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
        long dt = Math.max(0L, downTime);
        long et = Math.max(dt, eventTime);
        MotionEvent event = MotionEvent.obtain(
                dt,
                et,
                action,
                x,
                y,
                pressure,
                size,
                metaState,
                xPrecision,
                yPrecision,
                deviceId,
                edgeFlags
        );
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        inject(event);
    }

    void touchDown(float x, float y) {
        long now = SystemClock.uptimeMillis();
        touchDownTime = now;
        touchActive = true;
        MotionEvent down = MotionEvent.obtain(
                touchDownTime,
                now,
                MotionEvent.ACTION_DOWN,
                x,
                y,
                1.0f,
                1.0f,
                0,
                1.0f,
                1.0f,
                0,
                0
        );
        down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        inject(down);
    }

    void touchMove(float x, float y) {
        if (!touchActive) {
            // If move is received without a prior down, start a new sequence.
            touchDown(x, y);
            return;
        }
        long now = SystemClock.uptimeMillis();
        MotionEvent move = MotionEvent.obtain(
                touchDownTime,
                now,
                MotionEvent.ACTION_MOVE,
                x,
                y,
                1.0f,
                1.0f,
                0,
                1.0f,
                1.0f,
                0,
                0
        );
        move.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        inject(move);
    }

    void touchUp(float x, float y) {
        if (!touchActive) {
            // No active touch: fall back to a simple tap at this position.
            long now = SystemClock.uptimeMillis();
            MotionEvent down = MotionEvent.obtain(
                    now,
                    now,
                    MotionEvent.ACTION_DOWN,
                    x,
                    y,
                    1.0f,
                    1.0f,
                    0,
                    1.0f,
                    1.0f,
                    0,
                    0
            );
            down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            inject(down);
            MotionEvent up = MotionEvent.obtain(
                    now,
                    now,
                    MotionEvent.ACTION_UP,
                    x,
                    y,
                    1.0f,
                    1.0f,
                    0,
                    1.0f,
                    1.0f,
                    0,
                    0
            );
            up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            inject(up);
            return;
        }
        long now = SystemClock.uptimeMillis();
        MotionEvent up = MotionEvent.obtain(
                touchDownTime,
                now,
                MotionEvent.ACTION_UP,
                x,
                y,
                1.0f,
                1.0f,
                0,
                1.0f,
                1.0f,
                0,
                0
        );
        up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        inject(up);
        touchActive = false;
    }
}
