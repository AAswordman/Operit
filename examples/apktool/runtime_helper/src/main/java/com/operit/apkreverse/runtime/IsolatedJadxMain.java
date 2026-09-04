package com.operit.apkreverse.runtime;

import org.json.JSONObject;

import java.io.File;

/**
 * Step 5 entry point: this class is launched inside a dedicated Android process through
 * {@code app_process}, so JADX gets a fresh heap of its own and an OOM here can never take
 * the host application down with it.
 *
 * <p>Contract: the last line written to stdout is the payload line, prefixed with
 * {@link #PAYLOAD_PREFIX}. Everything else on stdout/stderr is diagnostic noise that the
 * caller may log but must not parse.</p>
 */
public final class IsolatedJadxMain {
    static final String PAYLOAD_PREFIX = "OPERIT_JADX_PAYLOAD:";
    static final String ERROR_PREFIX = "OPERIT_JADX_ERROR:";

    private IsolatedJadxMain() {
    }

    private static long heapBeforeClear = -1L;
    private static long heapAfterClear = -1L;
    private static String heapClearStatus = "not_attempted";

    /**
     * A bare app_process child does not inherit the host application's
     * largeHeap flag, so ART clamps Runtime.maxMemory() to
     * dalvik.vm.heapgrowthlimit (256 MB on this device) instead of
     * dalvik.vm.heapsize (512 MB). Passing -Xmx is parsed but has no effect on
     * the growth limit. VMRuntime.clearGrowthLimit() is exactly what the
     * platform itself calls for largeHeap applications, so invoke it here.
     */
    private static void raiseChildHeapLimit() {
        Runtime runtime = Runtime.getRuntime();
        heapBeforeClear = runtime.maxMemory();
        try {
            Class<?> vmRuntimeClass = Class.forName("dalvik.system.VMRuntime");
            Object vmRuntime = vmRuntimeClass.getDeclaredMethod("getRuntime").invoke(null);
            vmRuntimeClass.getDeclaredMethod("clearGrowthLimit").invoke(vmRuntime);
            heapClearStatus = "cleared";
        } catch (Throwable t) {
            heapClearStatus = "failed:" + t.getClass().getName() + ":" + t.getMessage();
        }
        heapAfterClear = runtime.maxMemory();
    }

    public static void main(String[] args) {
        if (args == null || args.length < 5) {
            System.out.println(ERROR_PREFIX + "expected 5 arguments: inputApk outputDir jobs deobf showInconsistentCode [startIndex]");
            System.out.flush();
            System.exit(2);
            return;
        }
        String inputApkPath = args[0];
        String outputDir = args[1];
        Integer jobs = parseJobs(args[2]);
        boolean deobf = Boolean.parseBoolean(args[3]);
        boolean showInconsistentCode = Boolean.parseBoolean(args[4]);
        int startIndex = 0;
        if (args.length >= 6) {
            try {
                startIndex = Integer.parseInt(args[5].trim());
            } catch (NumberFormatException badIndex) {
                System.out.println(ERROR_PREFIX + "startIndex is not an integer: " + args[5]);
                System.out.flush();
                System.exit(2);
                return;
            }
            if (startIndex < 0) {
                startIndex = 0;
            }
        }

        try {
            raiseChildHeapLimit();
        ensureWritableTempDir();
            String payload = JadxBridgeSupport.decompileApk(
                    inputApkPath,
                    outputDir,
                    jobs,
                    deobf,
                    showInconsistentCode,
                    startIndex
            );
            System.out.println(PAYLOAD_PREFIX + withRuntimeFacts(payload));
            System.out.flush();
            System.exit(0);
        } catch (OutOfMemoryError memoryError) {
            System.out.println(ERROR_PREFIX + describeWithRuntimeFacts(memoryError));
            System.out.flush();
            System.exit(3);
        } catch (Throwable error) {
            System.out.println(ERROR_PREFIX + describe(error));
            System.out.flush();
            System.exit(1);
        }
    }

    /**
     * JADX calls Files.createTempDirectory() during load(), which follows java.io.tmpdir.
     * If the parent forgot to pass one, or it is not usable, point it at a directory next
     * to our own dex files rather than letting it fail on /tmp.
     */
    private static void ensureWritableTempDir() {
        String configured = System.getProperty("java.io.tmpdir");
        if (configured != null && !configured.trim().isEmpty()) {
            File candidate = new File(configured.trim());
            if ((candidate.isDirectory() || candidate.mkdirs()) && candidate.canWrite()) {
                return;
            }
        }

        File fallback = new File(fallbackTempRoot(), "isolated-jadx-tmp");
        if (!fallback.isDirectory() && !fallback.mkdirs()) {
            throw new IllegalStateException(
                    "java.io.tmpdir is unusable (" + configured + ") and the fallback "
                            + fallback.getAbsolutePath() + " could not be created");
        }
        if (!fallback.canWrite()) {
            throw new IllegalStateException(
                    "java.io.tmpdir is unusable (" + configured + ") and the fallback "
                            + fallback.getAbsolutePath() + " is not writable");
        }
        System.setProperty("java.io.tmpdir", fallback.getAbsolutePath());
    }

    private static File fallbackTempRoot() {
        String classPath = System.getProperty("java.class.path");
        if (classPath != null && !classPath.trim().isEmpty()) {
            String first = classPath.split(File.pathSeparator)[0].trim();
            if (!first.isEmpty()) {
                File parent = new File(first).getParentFile();
                if (parent != null) {
                    return parent;
                }
            }
        }
        return new File(".").getAbsoluteFile();
    }

    private static Integer parseJobs(String raw) {
        try {
            int value = Integer.parseInt(raw.trim());
            return value < 1 ? Integer.valueOf(1) : Integer.valueOf(value);
        } catch (RuntimeException ignored) {
            return Integer.valueOf(1);
        }
    }

    /**
     * Emit the whole cause chain plus the top frames. A bare type+message is useless when
     * the throwable is a wrapper (InvocationTargetException has a null message).
     */
    private static String describe(Throwable error) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("errorType", error.getClass().getName());
            payload.put("errorMessage", error.getMessage() == null ? "" : error.getMessage());
            payload.put("errorString", String.valueOf(error));

            StringBuilder chain = new StringBuilder();
            Throwable current = error;
            int depth = 0;
            while (current != null && depth < 8) {
                if (chain.length() > 0) {
                    chain.append(" <- ");
                }
                chain.append(current.getClass().getName());
                if (current.getMessage() != null) {
                    chain.append(": ").append(current.getMessage());
                }
                Throwable next = current.getCause();
                if (next == current) {
                    break;
                }
                current = next;
                depth += 1;
            }
            payload.put("causeChain", chain.toString());

            Throwable root = error;
            int guard = 0;
            while (root.getCause() != null && root.getCause() != root && guard < 8) {
                root = root.getCause();
                guard += 1;
            }
            payload.put("rootCauseType", root.getClass().getName());
            payload.put("rootCauseMessage", root.getMessage() == null ? "" : root.getMessage());
            payload.put("stack", topFrames(root, 18));
        } catch (Exception ignored) {
            return error.getClass().getName() + ": " + error.getMessage();
        }
        return payload.toString();
    }

    /**
     * Attach the heap the child actually got, so an ignored -Xmx is immediately visible.
     */
    private static String withRuntimeFacts(String payloadJson) {
        try {
            JSONObject payload = new JSONObject(payloadJson);
            payload.put("childMaxHeapBytes", Runtime.getRuntime().maxMemory());
            payload.put("childHeapBeforeClear", heapBeforeClear);
            payload.put("childHeapAfterClear", heapAfterClear);
            payload.put("childHeapClearStatus", heapClearStatus);
            payload.put("childTmpDirUsed", String.valueOf(System.getProperty("java.io.tmpdir")));
            return payload.toString();
        } catch (Exception ignored) {
            return payloadJson;
        }
    }

    private static String describeWithRuntimeFacts(Throwable error) {
        String base = describe(error);
        try {
            JSONObject payload = new JSONObject(base);
            payload.put("childMaxHeapBytes", Runtime.getRuntime().maxMemory());
            payload.put("childHeapBeforeClear", heapBeforeClear);
            payload.put("childHeapAfterClear", heapAfterClear);
            payload.put("childHeapClearStatus", heapClearStatus);
            payload.put("childTmpDirUsed", String.valueOf(System.getProperty("java.io.tmpdir")));
            return payload.toString();
        } catch (Exception ignored) {
            return base;
        }
    }

    private static String topFrames(Throwable error, int limit) {
        StackTraceElement[] frames = error.getStackTrace();
        if (frames == null || frames.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        int count = Math.min(limit, frames.length);
        for (int index = 0; index < count; index += 1) {
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append("    at ").append(frames[index]);
        }
        return builder.toString();
    }
}