package com.operit.apkreverse.runtime;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

final class JadxBridgeSupport {
    private static final long LOW_MEMORY_FLOOR_BYTES = 48L * 1024L * 1024L;
    private static final int GC_HINT_CLASS_INTERVAL = 64;

    private JadxBridgeSupport() {
    }

    static String decompileApk(
            String inputApkPath,
            String outputDir,
            Integer jobs,
            boolean deobf,
            boolean showInconsistentCode
    ) throws Exception {
        return decompileApk(inputApkPath, outputDir, jobs, deobf, showInconsistentCode, 0);
    }

    static String decompileApk(
            String inputApkPath,
            String outputDir,
            Integer jobs,
            boolean deobf,
            boolean showInconsistentCode,
            int startIndex
    ) throws Exception {
        File inputApk = SearchSupport.requireExistingFile(inputApkPath, "input_apk_path");
        File outputDirectory = new File(outputDir);
        if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
            throw new IllegalStateException("Failed to create output directory: " + outputDirectory.getAbsolutePath());
        }

        Object args = ReflectionSupport.newInstance("jadx.api.JadxArgs");
        try {
            ReflectionSupport.invoke(args, "setInputFile", inputApk);
            ReflectionSupport.invoke(args, "setOutDir", outputDirectory);
            ReflectionSupport.invoke(args, "setThreadsCount", jobs != null ? jobs : 1);
            if (deobf) {
                ReflectionSupport.invoke(args, "setDeobfuscationOn", true);
            }
            if (showInconsistentCode) {
                ReflectionSupport.invoke(args, "setShowInconsistentCode", true);
            }
            configureAndroidCompatibleSecurity(args);
            configureStreamingCodeCache(args);

            Object decompiler = ReflectionSupport.newInstance("jadx.api.JadxDecompiler", args);
            try {
                return runWithDecompilerContextClassLoader(decompiler, () -> {
                    ReflectionSupport.invoke(decompiler, "load");

                    JSONObject payload = new JSONObject();
                    payload.put("inputApkPath", inputApk.getAbsolutePath());
                    payload.put("outputDir", outputDirectory.getAbsolutePath());

                    JSONObject resourceStage = saveResourcesStage(decompiler);
                    payload.put("resourceStage", resourceStage);

                    JSONObject sourceStage = saveSourcesStreaming(decompiler, outputDirectory, startIndex);
                    payload.put("sourceStage", sourceStage);

                    payload.put("classCount", sourceStage.optInt("totalClasses", 0));
                    payload.put("resourceCount", resourceStage.optInt("resourceCount", 0));
                    payload.put("errorsCount", numberOf(ReflectionSupport.invoke(decompiler, "getErrorsCount")));
                    payload.put("warnsCount", numberOf(ReflectionSupport.invoke(decompiler, "getWarnsCount")));
                    payload.put("memoryStrategy", "streaming-per-class-unload");
                    return payload.toString();
                });
            } finally {
                ReflectionSupport.invoke(decompiler, "close");
            }
        } finally {
            ReflectionSupport.invoke(args, "close");
        }
    }

    private static void configureStreamingCodeCache(Object args) throws Exception {
        Object noOpCache = ReflectionSupport.newInstance("jadx.api.impl.NoOpCodeCache");
        ReflectionSupport.invoke(args, "setCodeCache", noOpCache);
    }

    private static JSONObject saveResourcesStage(Object decompiler) throws Exception {
        JSONObject stage = new JSONObject();
        int resourceCount;
        try {
            resourceCount = sizeOf(ReflectionSupport.invoke(decompiler, "getResources"));
        } catch (Exception ignored) {
            resourceCount = 0;
        }
        ReflectionSupport.invoke(decompiler, "saveResources");
        hintGarbageCollection();
        stage.put("resourceCount", resourceCount);
        stage.put("completed", true);
        return stage;
    }

    private static JSONObject saveSourcesStreaming(Object decompiler, File outputDirectory, int startIndex) throws Exception {
        File sourcesRoot = new File(outputDirectory, "sources");
        if (!sourcesRoot.exists() && !sourcesRoot.mkdirs()) {
            throw new IllegalStateException("Failed to create sources directory: " + sourcesRoot.getAbsolutePath());
        }

        Object rawClasses = ReflectionSupport.invoke(decompiler, "getClasses");
        if (!(rawClasses instanceof List<?>)) {
            throw new IllegalStateException("JadxDecompiler.getClasses() did not return a List");
        }
        List<?> classes = (List<?>) rawClasses;

        int totalClasses = classes.size();
        int writtenClasses = 0;
        int skippedClasses = 0;
        int failedClasses = 0;
        long writtenBytes = 0L;
        long minFreeBytesSeen = Long.MAX_VALUE;
        boolean truncatedByMemory = false;
        int oomAtIndex = -1;
        String oomMessage = null;

        int firstIndex = startIndex < 0 ? 0 : startIndex;
        if (firstIndex > totalClasses) {
            firstIndex = totalClasses;
        }
        int lastVisitedIndex = firstIndex - 1;
        for (int index = firstIndex; index < totalClasses; index += 1) {
            lastVisitedIndex = index;
            Object javaClass = classes.get(index);
            try {
            try {
                String code = extractCodeString(javaClass);
                if (code == null || code.isEmpty()) {
                    skippedClasses += 1;
                    continue;
                }
                String relativePath = resolveClassFilePath(javaClass);
                if (relativePath == null || relativePath.isEmpty()) {
                    skippedClasses += 1;
                    continue;
                }
                File targetFile = new File(sourcesRoot, relativePath);
                File parent = targetFile.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new IllegalStateException("Failed to create directory: " + parent.getAbsolutePath());
                }
                writtenBytes += writeUtf8(targetFile, code);
                writtenClasses += 1;
            } catch (OutOfMemoryError memoryError) {
                failedClasses += 1;
                unloadClassCode(javaClass);
                hintGarbageCollection();
                truncatedByMemory = true;
                oomAtIndex = index;
                oomMessage = String.valueOf(memoryError.getMessage());
                break;
            } catch (Exception classError) {
                failedClasses += 1;
            } finally {
                unloadClassCode(javaClass);
            }
            } catch (OutOfMemoryError loopMemoryError) {
                // Includes allocations made by the finally block above.
                hintGarbageCollection();
                truncatedByMemory = true;
                oomAtIndex = index;
                oomMessage = String.valueOf(loopMemoryError.getMessage());
                break;
            }

            long freeBytes = estimateFreeHeapBytes();
            if (freeBytes < minFreeBytesSeen) {
                minFreeBytesSeen = freeBytes;
            }
            boolean periodicHint = (index + 1) % GC_HINT_CLASS_INTERVAL == 0;
            if (periodicHint || freeBytes < LOW_MEMORY_FLOOR_BYTES) {
                hintGarbageCollection();
            }
        }

        JSONObject stage = new JSONObject();
        stage.put("totalClasses", totalClasses);
        stage.put("writtenClasses", writtenClasses);
        stage.put("skippedClasses", skippedClasses);
        stage.put("failedClasses", failedClasses);
        stage.put("writtenBytes", writtenBytes);
        stage.put("sourcesDir", sourcesRoot.getAbsolutePath());
        stage.put("minFreeHeapBytes", minFreeBytesSeen == Long.MAX_VALUE ? -1L : minFreeBytesSeen);
        stage.put("truncatedByMemory", truncatedByMemory);
        stage.put("startIndex", firstIndex);
        stage.put("nextStartIndex", truncatedByMemory ? oomAtIndex : totalClasses);
        stage.put("remainingClasses", truncatedByMemory ? (totalClasses - oomAtIndex) : 0);
        stage.put("lastVisitedIndex", lastVisitedIndex);
        stage.put("oomAtClassIndex", oomAtIndex);
        if (oomMessage != null) {
            stage.put("oomMessage", oomMessage);
        }
        stage.put("childMaxHeapBytes", Runtime.getRuntime().maxMemory());
        stage.put("completed", !truncatedByMemory);
        return stage;
    }

    private static String extractCodeString(Object javaClass) throws Exception {
        Object codeInfo;
        try {
            codeInfo = ReflectionSupport.invoke(javaClass, "getCodeInfo");
        } catch (Exception ignored) {
            codeInfo = ReflectionSupport.invoke(javaClass, "getCode");
        }
        if (codeInfo == null) {
            return null;
        }
        if (codeInfo instanceof String) {
            return (String) codeInfo;
        }
        Object codeString = ReflectionSupport.invoke(codeInfo, "getCodeStr");
        return codeString == null ? null : String.valueOf(codeString);
    }

    private static String resolveClassFilePath(Object javaClass) {
        try {
            Object classNode = ReflectionSupport.invoke(javaClass, "getClassNode");
            if (classNode != null) {
                Object classInfo = ReflectionSupport.invoke(classNode, "getClassInfo");
                if (classInfo != null) {
                    Object aliasPath = ReflectionSupport.invoke(classInfo, "getAliasFullPath");
                    if (aliasPath != null) {
                        String path = String.valueOf(aliasPath);
                        if (!path.isEmpty()) {
                            return path + ".java";
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // fall through to the full-name based path
        }
        try {
            Object fullName = ReflectionSupport.invoke(javaClass, "getFullName");
            if (fullName != null) {
                String name = String.valueOf(fullName);
                if (!name.isEmpty()) {
                    return name.replace('.', '/') + ".java";
                }
            }
        } catch (Exception ignored) {
            // no usable path
        }
        return null;
    }

    private static void unloadClassCode(Object javaClass) {
        // Device-verified API surface of this jadx build:
        //   jadx.api.JavaClass          -> unload()
        //   jadx.core.dex.nodes.ClassNode -> deepUnload(), unloadCode(), unloadFromCache()
        // The parsed graph is held by ClassNode, so reach through getClassNode()
        // and call the deep variant first. Calling "unloadCode" on JavaClass, as
        // the previous implementation did, always threw NoSuchMethodException and
        // silently degraded to the shallow unload().
        try {
            Object classNode = ReflectionSupport.invoke(javaClass, "getClassNode");
            if (classNode != null) {
                ReflectionSupport.invoke(classNode, "deepUnload");
                return;
            }
        } catch (Exception ignored) {
            // fall through to the shallow path
        }
        try {
            ReflectionSupport.invoke(javaClass, "unload");
        } catch (Exception alsoIgnored) {
            // nothing else to release
        }
    }

    private static long writeUtf8(File targetFile, String code) throws Exception {
        long written = 0L;
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(targetFile), StandardCharsets.UTF_8)) {
            writer.write(code);
            written = code.length();
        }
        return written;
    }

    private static long estimateFreeHeapBytes() {
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        return runtime.maxMemory() - used;
    }

    private static void hintGarbageCollection() {
        System.gc();
    }

    @SuppressWarnings("unchecked")
    private static void configureAndroidCompatibleSecurity(Object args) throws Exception {
        Object rawFlags = ReflectionSupport.invokeStatic("jadx.api.security.JadxSecurityFlag", "all");
        if (!(rawFlags instanceof Set<?>)) {
            throw new IllegalStateException("jadx.api.security.JadxSecurityFlag.all() did not return a Set");
        }
        Set<Object> flags = (Set<Object>) rawFlags;
        Object secureXmlParserFlag =
                ReflectionSupport.getStaticField("jadx.api.security.JadxSecurityFlag", "SECURE_XML_PARSER");
        flags.remove(secureXmlParserFlag);
        Object security = ReflectionSupport.newInstance("jadx.api.security.impl.JadxSecurity", flags);
        ReflectionSupport.invoke(args, "setSecurity", security);
    }

    private static <T> T runWithDecompilerContextClassLoader(
            Object decompiler,
            ThrowingSupplier<T> action
    ) throws Exception {
        Thread currentThread = Thread.currentThread();
        ClassLoader previousClassLoader = currentThread.getContextClassLoader();
        ClassLoader decompilerClassLoader = decompiler.getClass().getClassLoader();
        boolean changed = decompilerClassLoader != null && decompilerClassLoader != previousClassLoader;
        if (changed) {
            currentThread.setContextClassLoader(decompilerClassLoader);
        }
        try {
            return action.get();
        } finally {
            if (changed) {
                currentThread.setContextClassLoader(previousClassLoader);
            }
        }
    }

    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private static int sizeOf(Object value) throws Exception {
        if (value instanceof java.util.Collection<?>) {
            return ((java.util.Collection<?>) value).size();
        }
        Object result = ReflectionSupport.invoke(value, "size");
        return numberOf(result);
    }

    private static int numberOf(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(String.valueOf(value));
    }
}