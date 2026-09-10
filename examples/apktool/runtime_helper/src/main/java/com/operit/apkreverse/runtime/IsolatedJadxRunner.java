package com.operit.apkreverse.runtime;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Step 5: run JADX in a separate Android process via {@code app_process}.
 *
 * <p>The child gets its own heap, so a 512 MB ceiling applies to JADX alone instead of being
 * shared with the whole host application. If the child dies the host stays alive and receives a
 * structured failure instead of a hard crash.</p>
 */
final class IsolatedJadxRunner {
    private static final long DEFAULT_TIMEOUT_MINUTES = 90L;
    private static final int MAX_DIAGNOSTIC_LINES = 40;
    private static final String READ_ONLY_DEX_DIR_NAME = "isolated-readonly-dex";
    private static final String CHILD_TMP_DIR_NAME = "isolated-jadx-tmp";
    private static final int DEFAULT_CHILD_HEAP_MB = 1536;
    private static final int MIN_CHILD_HEAP_MB = 256;
    private static final int MAX_CHILD_HEAP_MB = 4096;
    private static final String APP_PROCESS_PATH = "/system/bin/app_process";
    private static final String[] SHARED_STORAGE_PREFIXES = {
            "/storage/", "/sdcard", "/mnt/user/", "/mnt/runtime/", "/mnt/media_rw/"
    };

    private IsolatedJadxRunner() {
    }

    static String decompileInSeparateProcess(
            String inputApkPath,
            String outputDir,
            Integer jobs,
            boolean deobf,
            boolean showInconsistentCode,
            String helperRuntimeJarPath,
            String jadxRuntimeJarPath,
            String workingDirPath
    ) throws Exception {
        File inputApk = SearchSupport.requireExistingFile(inputApkPath, "input_apk_path");
        File helperJar = SearchSupport.requireExistingFile(helperRuntimeJarPath, "helper_runtime_jar_path");
        File jadxJar = SearchSupport.requireExistingFile(jadxRuntimeJarPath, "jadx_runtime_jar_path");
        File outputDirectory = new File(outputDir);
        if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
            throw new IllegalStateException("Failed to create output directory: " + outputDirectory.getAbsolutePath());
        }
        File workingDir = new File(workingDirPath == null || workingDirPath.trim().isEmpty()
                ? outputDirectory.getAbsolutePath()
                : workingDirPath.trim());
        if (!workingDir.exists() && !workingDir.mkdirs()) {
            throw new IllegalStateException("Failed to create working directory: " + workingDir.getAbsolutePath());
        }

        File appProcess = new File(APP_PROCESS_PATH);
        if (!appProcess.exists()) {
            throw new IllegalStateException(
                    "Isolated mode requires " + APP_PROCESS_PATH + ", which does not exist on this device");
        }

        // ART aborts with SecurityException("Writable dex file ... is not allowed") while
        // creating the system ClassLoader if any CLASSPATH entry is owner-writable, so the
        // child never reaches main(). Feed it read-only copies instead.
        // The staging directory must be private app storage: shared storage is FUSE-backed,
        // where chmod is emulated and every file stays mode 0660, so the write bit can never
        // actually be cleared and the child would abort with exit 134.
        File readOnlyDir = resolveReadOnlyStagingDir(helperJar, jadxJar);
        File readOnlyHelperJar = materializeReadOnlyDex(helperJar, readOnlyDir);
        File readOnlyJadxJar = materializeReadOnlyDex(jadxJar, readOnlyDir);

        // JadxDecompiler.load() calls Files.createTempDirectory(), which follows
        // java.io.tmpdir. A bare app_process child defaults to /tmp, which is not writable
        // on Android, so JADX dies with AccessDeniedException before decompiling anything.
        File childTmpDir = prepareChildTempDir(readOnlyDir.getParentFile());

        String classPath = readOnlyHelperJar.getAbsolutePath()
                + File.pathSeparator + readOnlyJadxJar.getAbsolutePath();
        List<String> command = new ArrayList<>();
        command.add(APP_PROCESS_PATH);
        command.add("-Djava.class.path=" + classPath);
        command.add("-Dandroid.dexpath=" + classPath);
        command.add("-Dos.name=Linux");
        command.add("-Dos.arch=aarch64");
        command.add("-Djava.io.tmpdir=" + childTmpDir.getAbsolutePath());
        command.add("-Duser.home=" + childTmpDir.getAbsolutePath());
        // A bare app_process child gets ~256 MB and does NOT inherit android:largeHeap,
        // so without this the isolated run has less memory than the host process.
        int childHeapMb = resolveChildHeapMb();
        command.add("-Xmx" + childHeapMb + "m");
        command.add("/system/bin");
        command.add(IsolatedJadxMain.class.getName());
        command.add(inputApk.getAbsolutePath());
        command.add(outputDirectory.getAbsolutePath());
        command.add(String.valueOf(jobs == null ? 1 : jobs.intValue()));
        command.add(String.valueOf(deobf));
        command.add(String.valueOf(showInconsistentCode));

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDir);
        builder.redirectErrorStream(true);
        builder.environment().put("CLASSPATH", classPath);
        builder.environment().put("TMPDIR", childTmpDir.getAbsolutePath());

        long startedAt = System.currentTimeMillis();
        Process process = builder.start();
        String payloadLine = null;
        String errorLine = null;
        List<String> diagnostics = new ArrayList<>();
        try (InputStream stream = process.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(IsolatedJadxMain.PAYLOAD_PREFIX)) {
                    payloadLine = line.substring(IsolatedJadxMain.PAYLOAD_PREFIX.length());
                } else if (line.startsWith(IsolatedJadxMain.ERROR_PREFIX)) {
                    errorLine = line.substring(IsolatedJadxMain.ERROR_PREFIX.length());
                } else if (diagnostics.size() < MAX_DIAGNOSTIC_LINES) {
                    diagnostics.add(line);
                }
            }
        }

        boolean finished = process.waitFor(DEFAULT_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException(
                    "Isolated JADX process exceeded " + DEFAULT_TIMEOUT_MINUTES + " minutes and was killed");
        }
        int exitCode = process.exitValue();
        long elapsedMs = System.currentTimeMillis() - startedAt;

        if (payloadLine == null) {
            JSONObject failure = new JSONObject();
            failure.put("isolatedProcess", true);
            failure.put("exitCode", exitCode);
            failure.put("elapsedMs", elapsedMs);
            failure.put("command", String.join(" ", command));
            failure.put("childError", errorLine == null ? "" : errorLine);
            failure.put("diagnostics", String.join("\n", diagnostics));
            failure.put("classPath", classPath);
            failure.put("classPathReadOnly", describeDexPermissions(readOnlyHelperJar, readOnlyJadxJar));
            failure.put("readOnlyStagingDir", readOnlyDir.getAbsolutePath());
            failure.put("childTmpDir", childTmpDir.getAbsolutePath());
            failure.put("childTmpDirWritable", childTmpDir.canWrite());
            failure.put("childHeapRequestedMb", childHeapMb);
            failure.put("hint", explainChildExit(exitCode));
            throw new IllegalStateException("Isolated JADX process produced no payload: " + failure);
        }

        JSONObject payload = new JSONObject(payloadLine);
        payload.put("isolatedProcess", true);
        payload.put("isolatedExitCode", exitCode);
        payload.put("isolatedElapsedMs", elapsedMs);
        payload.put("isolatedCommand", String.join(" ", command));
        payload.put("isolatedStagingDir", readOnlyDir.getAbsolutePath());
        payload.put("isolatedTmpDir", childTmpDir.getAbsolutePath());
        payload.put("isolatedHeapRequestedMb", childHeapMb);
        if (!diagnostics.isEmpty()) {
            payload.put("isolatedDiagnostics", String.join("\n", diagnostics));
        }
        return payload.toString();
    }

    /**
     * Pick a staging directory whose permission bits are actually honoured.
     *
     * <p>The runtime jars we are handed already live in the app's private cache, so their
     * parent directory is the natural choice; {@code java.io.tmpdir} is the fallback.
     * Anything under shared storage is rejected up front.</p>
     */
    private static File resolveReadOnlyStagingDir(File helperJar, File jadxJar) {
        List<File> candidates = new ArrayList<>();
        File helperParent = helperJar.getParentFile();
        if (helperParent != null) {
            candidates.add(new File(helperParent, READ_ONLY_DEX_DIR_NAME));
        }
        File jadxParent = jadxJar.getParentFile();
        if (jadxParent != null) {
            candidates.add(new File(jadxParent, READ_ONLY_DEX_DIR_NAME));
        }
        String tmpDir = System.getProperty("java.io.tmpdir");
        if (tmpDir != null && !tmpDir.trim().isEmpty()) {
            candidates.add(new File(tmpDir.trim(), READ_ONLY_DEX_DIR_NAME));
        }

        StringBuilder rejected = new StringBuilder();
        for (File candidate : candidates) {
            String reason = whyStagingDirUnusable(candidate);
            if (reason == null) {
                return candidate;
            }
            if (rejected.length() > 0) {
                rejected.append("; ");
            }
            rejected.append(candidate.getAbsolutePath()).append(" -> ").append(reason);
        }
        throw new IllegalStateException(
                "No private directory available for read-only dex staging [" + rejected + "]");
    }

    /**
     * Create a writable private temp directory for the child VM. JADX creates its own
     * scratch space under java.io.tmpdir during load().
     */
    private static File prepareChildTempDir(File privateRoot) {
        List<File> candidates = new ArrayList<>();
        if (privateRoot != null) {
            candidates.add(new File(privateRoot, CHILD_TMP_DIR_NAME));
        }
        String hostTmp = System.getProperty("java.io.tmpdir");
        if (hostTmp != null && !hostTmp.trim().isEmpty()) {
            candidates.add(new File(hostTmp.trim(), CHILD_TMP_DIR_NAME));
        }

        StringBuilder rejected = new StringBuilder();
        for (File candidate : candidates) {
            String absolutePath = candidate.getAbsolutePath();
            if (isSharedStoragePath(absolutePath)) {
                appendRejection(rejected, absolutePath, "shared storage");
                continue;
            }
            if (!candidate.isDirectory() && !candidate.mkdirs()) {
                appendRejection(rejected, absolutePath, "cannot create");
                continue;
            }
            if (!candidate.canWrite()) {
                appendRejection(rejected, absolutePath, "not writable");
                continue;
            }
            return candidate;
        }
        throw new IllegalStateException(
                "No writable private temp directory available for the isolated child ["
                        + rejected + "]");
    }

    /**
     * Allow the heap request to be tuned without rebuilding, but keep it inside sane bounds.
     */
    private static int resolveChildHeapMb() {
        String override = System.getProperty("operit.isolated.jadx.heapMb");
        if (override != null && !override.trim().isEmpty()) {
            try {
                int parsed = Integer.parseInt(override.trim());
                if (parsed >= MIN_CHILD_HEAP_MB && parsed <= MAX_CHILD_HEAP_MB) {
                    return parsed;
                }
            } catch (RuntimeException ignored) {
                // fall through to the default
            }
        }
        return DEFAULT_CHILD_HEAP_MB;
    }

    private static void appendRejection(StringBuilder builder, String path, String reason) {
        if (builder.length() > 0) {
            builder.append("; ");
        }
        builder.append(path).append(" -> ").append(reason);
    }

    private static String whyStagingDirUnusable(File candidate) {
        if (isSharedStoragePath(candidate.getAbsolutePath())) {
            return "shared storage cannot hold read-only dex files";
        }
        if (!candidate.isDirectory() && !candidate.mkdirs()) {
            return "cannot create directory";
        }
        if (!candidate.canWrite()) {
            return "directory is not writable";
        }
        return null;
    }

    private static boolean isSharedStoragePath(String absolutePath) {
        for (String prefix : SHARED_STORAGE_PREFIXES) {
            if (absolutePath.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Copy a dex-jar into a private directory and strip every write bit, because ART
     * refuses to start a VM whose CLASSPATH contains a writable dex file.
     */
    private static File materializeReadOnlyDex(File sourceJar, File targetDir) throws Exception {
        String stableName = shortDigest(sourceJar) + "-" + sourceJar.getName();
        File target = new File(targetDir, stableName);

        if (target.exists() && target.length() == sourceJar.length() && isReadOnlyByMode(target)) {
            return target;
        }

        if (target.exists()) {
            if (!target.setWritable(true, true) && !target.canWrite()) {
                throw new IllegalStateException(
                        "Cannot refresh read-only dex copy: " + target.getAbsolutePath());
            }
            if (!target.delete()) {
                throw new IllegalStateException(
                        "Cannot delete stale read-only dex copy: " + target.getAbsolutePath());
            }
        }

        Files.copy(sourceJar.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        makeDexReadOnly(target);
        return target;
    }

    /**
     * Clear every write bit. POSIX permissions are attempted first because they are the
     * only mechanism that reliably reaches mode 0444 on private app storage; the legacy
     * File setters are kept as a fallback for filesystems without POSIX views.
     */
    private static void makeDexReadOnly(File target) {
        // Legacy setters run first: they are only a fallback for filesystems without a
        // POSIX view, and they must never be able to re-add a write bit afterwards.
        boolean readable = target.setReadable(true, false);
        boolean clearedGroupAndOthers = target.setWritable(false, false);
        boolean clearedOwner = target.setWritable(false, true);

        // POSIX has the final word, so mode 0444 actually sticks.
        String posixOutcome;
        try {
            Set<PosixFilePermission> permissions = new HashSet<>();
            permissions.add(PosixFilePermission.OWNER_READ);
            permissions.add(PosixFilePermission.GROUP_READ);
            permissions.add(PosixFilePermission.OTHERS_READ);
            Files.setPosixFilePermissions(target.toPath(), permissions);
            posixOutcome = "applied";
        } catch (Exception posixError) {
            posixOutcome = posixError.getClass().getName() + ": " + posixError.getMessage();
        }

        // ART inspects the file mode, so verify the mode instead of asking whether the
        // current process happens to be able to write (root ignores permission bits, which
        // would make canWrite() report a false failure here).
        Set<PosixFilePermission> effective = readPosixPermissionsOrNull(target);
        boolean writableByMode;
        String modeText;
        if (effective != null) {
            writableByMode = effective.contains(PosixFilePermission.OWNER_WRITE)
                    || effective.contains(PosixFilePermission.GROUP_WRITE)
                    || effective.contains(PosixFilePermission.OTHERS_WRITE);
            modeText = describePosixMode(effective);
        } else {
            // No POSIX view available: fall back to the coarse check.
            writableByMode = target.canWrite();
            modeText = "posix-view-unavailable canWrite=" + target.canWrite();
        }

        if (writableByMode) {
            throw new IllegalStateException(
                    "Failed to clear write permission on " + target.getAbsolutePath()
                            + " [posix=" + posixOutcome
                            + " mode=" + modeText
                            + " clearedGroupAndOthers=" + clearedGroupAndOthers
                            + " clearedOwner=" + clearedOwner
                            + " readable=" + readable
                            + " sharedStorage=" + isSharedStoragePath(target.getAbsolutePath())
                            + "]. Shared storage is FUSE-backed and keeps mode 0660, so the "
                            + "staging directory must be private app storage.");
        }
    }

    private static boolean isReadOnlyByMode(File target) {
        Set<PosixFilePermission> permissions = readPosixPermissionsOrNull(target);
        if (permissions == null) {
            return !target.canWrite();
        }
        return !permissions.contains(PosixFilePermission.OWNER_WRITE)
                && !permissions.contains(PosixFilePermission.GROUP_WRITE)
                && !permissions.contains(PosixFilePermission.OTHERS_WRITE);
    }

    private static Set<PosixFilePermission> readPosixPermissionsOrNull(File target) {
        try {
            return Files.getPosixFilePermissions(target.toPath());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String describePosixMode(Set<PosixFilePermission> permissions) {
        StringBuilder builder = new StringBuilder();
        builder.append(permissions.contains(PosixFilePermission.OWNER_READ) ? 'r' : '-');
        builder.append(permissions.contains(PosixFilePermission.OWNER_WRITE) ? 'w' : '-');
        builder.append(permissions.contains(PosixFilePermission.OWNER_EXECUTE) ? 'x' : '-');
        builder.append(permissions.contains(PosixFilePermission.GROUP_READ) ? 'r' : '-');
        builder.append(permissions.contains(PosixFilePermission.GROUP_WRITE) ? 'w' : '-');
        builder.append(permissions.contains(PosixFilePermission.GROUP_EXECUTE) ? 'x' : '-');
        builder.append(permissions.contains(PosixFilePermission.OTHERS_READ) ? 'r' : '-');
        builder.append(permissions.contains(PosixFilePermission.OTHERS_WRITE) ? 'w' : '-');
        builder.append(permissions.contains(PosixFilePermission.OTHERS_EXECUTE) ? 'x' : '-');
        return builder.toString();
    }

    private static String shortDigest(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(file.getAbsolutePath().getBytes(StandardCharsets.UTF_8));
        digest.update(String.valueOf(file.length()).getBytes(StandardCharsets.UTF_8));
        digest.update(String.valueOf(file.lastModified()).getBytes(StandardCharsets.UTF_8));
        byte[] bytes = digest.digest();
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < 6 && index < bytes.length; index += 1) {
            builder.append(String.format("%02x", bytes[index]));
        }
        return builder.toString();
    }

    private static String describeDexPermissions(File... files) {
        StringBuilder builder = new StringBuilder();
        for (File file : files) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            Set<PosixFilePermission> permissions = readPosixPermissionsOrNull(file);
            builder.append(file.getName())
                    .append("[exists=").append(file.exists())
                    .append(" readable=").append(file.canRead())
                    .append(" mode=").append(permissions == null
                            ? ("canWrite=" + file.canWrite())
                            : describePosixMode(permissions))
                    .append(" size=").append(file.length())
                    .append("]");
        }
        return builder.toString();
    }

    private static String explainChildExit(int exitCode) {
        if (exitCode == 134) {
            return "exit 134 = 128 + SIGABRT: the ART runtime aborted before main() ran. "
                    + "Most often 'Writable dex file ... is not allowed' while creating the "
                    + "system ClassLoader, meaning a CLASSPATH entry is still writable.";
        }
        if (exitCode == 3) {
            return "exit 3: the child hit OutOfMemoryError inside JADX; the host survived.";
        }
        if (exitCode == 2) {
            return "exit 2: the child received the wrong argument count.";
        }
        if (exitCode == 1) {
            return "exit 1: the child threw an exception; see childError.";
        }
        if (exitCode == 127) {
            return "exit 127: command not found; " + APP_PROCESS_PATH + " may be unavailable.";
        }
        return "exit " + exitCode + ": no payload line was printed by the child.";
    }
}