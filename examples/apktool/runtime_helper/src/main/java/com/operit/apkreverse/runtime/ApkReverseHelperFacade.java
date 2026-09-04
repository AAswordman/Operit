package com.operit.apkreverse.runtime;

import android.content.Context;
import com.android.apksig.ApkSigner;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ApkReverseHelperFacade {
    private ApkReverseHelperFacade() {
    }

    public static String describeRuntime() throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("name", "apk_reverse_helper");
        payload.put("status", "ready");
        payload.put("capabilities", new JSONArray()
                .put("inspect_apk")
                .put("decode_apk")
                .put("build_apk")
                .put("decompile_jadx")
                .put("search_text")
                .put("search_address")
                .put("sign_apk"));
        return payload.toString();
    }

    public static String inspectApk(String inputApkPath) throws Exception {
        return ApkArchiveSupport.inspectApk(inputApkPath);
    }

    public static String searchText(
            String rootPath,
            String query,
            String scope,
            boolean regex,
            boolean caseInsensitive,
            int maxResults
    ) throws Exception {
        return SearchSupport.searchText(rootPath, query, scope, regex, caseInsensitive, maxResults);
    }

    public static String decodeApk(
            String inputApkPath,
            String outputDir,
            String frameworkJarPath,
            String apktoolVersion,
            Integer jobs,
            String framePath,
            String frameTag,
            boolean force,
            boolean noSrc,
            boolean noRes,
            boolean onlyManifest,
            boolean noAssets,
            boolean verbose,
            boolean quiet
    ) throws Exception {
        return ApktoolBridgeSupport.decodeApk(
                inputApkPath,
                outputDir,
                frameworkJarPath,
                apktoolVersion,
                jobs,
                framePath,
                frameTag,
                force,
                noSrc,
                noRes,
                onlyManifest,
                noAssets,
                verbose,
                quiet
        );
    }

    public static String buildApk(
            String decodedDir,
            String outputApkPath,
            String frameworkJarPath,
            String apktoolVersion,
            Integer jobs,
            String framePath,
            String frameTag,
            boolean force,
            boolean verbose,
            boolean quiet
    ) throws Exception {
        return ApktoolBridgeSupport.buildApk(
                decodedDir,
                outputApkPath,
                frameworkJarPath,
                apktoolVersion,
                jobs,
                framePath,
                frameTag,
                force,
                verbose,
                quiet
        );
    }

    public static String decompileJadx(
            String inputApkPath,
            String outputDir,
            Integer jobs,
            boolean deobf,
            boolean showInconsistentCode
    ) throws Exception {
        return JadxBridgeSupport.decompileApk(inputApkPath, outputDir, jobs, deobf, showInconsistentCode);
    }

    /**
     * Step 5: same contract as {@link #decompileJadx}, but the actual work happens in a separate
     * Android process so JADX gets its own heap and a child OOM cannot kill the host app.
     */
    public static String decompileJadxIsolated(
            String inputApkPath,
            String outputDir,
            Integer jobs,
            boolean deobf,
            boolean showInconsistentCode,
            String helperRuntimeJarPath,
            String jadxRuntimeJarPath,
            String workingDirPath
    ) throws Exception {
        return IsolatedJadxRunner.decompileInSeparateProcess(
                inputApkPath,
                outputDir,
                jobs,
                deobf,
                showInconsistentCode,
                helperRuntimeJarPath,
                jadxRuntimeJarPath,
                workingDirPath
        );
    }

    public static String searchAddress(
            String rootPath,
            String query,
            String scope,
            int maxResults
    ) throws Exception {
        return SearchSupport.searchAddress(rootPath, query, scope, maxResults);
    }

    public static String signApk(
            Context context,
            String inputApkPath,
            String outputApkPath,
            String signMode,
            String keystorePath,
            String storePassword,
            String alias,
            String keyPassword,
            int minSdkVersion
    ) throws Exception {
        File inputApk = SearchSupport.requireExistingFile(inputApkPath, "input_apk_path");
        File outputApk = new File(outputApkPath);
        File outputParent = outputApk.getParentFile();
        if (outputParent != null && !outputParent.exists() && !outputParent.mkdirs()) {
            throw new IllegalStateException("Failed to create output directory: " + outputParent.getAbsolutePath());
        }

        SignInputs inputs = resolveSignInputs(context, signMode, keystorePath, storePassword, alias, keyPassword);
        KeyStore keyStore = createKeyStore(inputs.keystoreType);
        try (InputStream input = new FileInputStream(inputs.keystoreFile)) {
            keyStore.load(input, inputs.storePassword.toCharArray());
        }

        if (!keyStore.containsAlias(inputs.alias)) {
            throw new IllegalStateException("Alias not found in keystore: " + inputs.alias);
        }

        Key key = keyStore.getKey(inputs.alias, inputs.keyPassword.toCharArray());
        if (!(key instanceof PrivateKey)) {
            throw new IllegalStateException("Alias does not resolve to a PrivateKey: " + inputs.alias);
        }

        Certificate[] certificateChain = keyStore.getCertificateChain(inputs.alias);
        if (certificateChain == null || certificateChain.length == 0) {
            throw new IllegalStateException("No certificate chain found for alias: " + inputs.alias);
        }

        List<X509Certificate> certificates = new ArrayList<>();
        for (Certificate certificate : certificateChain) {
            if (!(certificate instanceof X509Certificate)) {
                throw new IllegalStateException("Certificate chain contains non-X509 certificate");
            }
            certificates.add((X509Certificate) certificate);
        }

        int resolvedMinSdkVersion = minSdkVersion > 0 ? minSdkVersion : ApkArchiveSupport.resolveMinSdkVersion(inputApk, 26);
        invokeApkSigner(inputApk, outputApk, inputs.alias, (PrivateKey) key, certificates, resolvedMinSdkVersion);

        JSONObject payload = new JSONObject();
        payload.put("inputApkPath", inputApk.getAbsolutePath());
        payload.put("outputApkPath", outputApk.getAbsolutePath());
        payload.put("signMode", inputs.signMode);
        payload.put("keystorePath", inputs.keystoreFile.getAbsolutePath());
        payload.put("alias", inputs.alias);
        payload.put("minSdkVersion", resolvedMinSdkVersion);
        return payload.toString();
    }

    private static SignInputs resolveSignInputs(
            Context context,
            String signMode,
            String keystorePath,
            String storePassword,
            String alias,
            String keyPassword
    ) throws Exception {
        String normalizedMode = signMode == null ? "" : signMode.trim().toLowerCase(Locale.ROOT);
        if ("debug".equals(normalizedMode)) {
            File debugKeystore = (File) invokeKeyStoreHelper(
                    "getOrCreateKeystore",
                    new Class<?>[]{Context.class},
                    new Object[]{context}
            );
            return new SignInputs("debug", debugKeystore, "android", "androidkey", "android", "PKCS12");
        }
        if (!"keystore".equals(normalizedMode)) {
            throw new IllegalArgumentException("sign_mode must be debug or keystore");
        }
        File keystoreFile = SearchSupport.requireExistingFile(keystorePath, "keystore_path");
        String normalizedPath = keystoreFile.getName().toLowerCase(Locale.ROOT);
        String keystoreType = normalizedPath.endsWith(".jks") ? "JKS" : "PKCS12";
        return new SignInputs("keystore", keystoreFile, storePassword, alias, keyPassword, keystoreType);
    }

    private static KeyStore createKeyStore(String keystoreType) throws Exception {
        if ("PKCS12".equals(keystoreType)) {
            Security.removeProvider("BC");
            Security.insertProviderAt(new BouncyCastleProvider(), 1);
        }
        return KeyStore.getInstance(keystoreType);
    }

    private static Object invokeKeyStoreHelper(String methodName, Class<?>[] parameterTypes, Object[] args) throws Exception {
        Class<?> helperClass = Class.forName("com.ai.assistance.operit.core.subpack.KeyStoreHelper");
        Method method = helperClass.getMethod(methodName, parameterTypes);
        return method.invoke(null, args);
    }

    private static void invokeApkSigner(
            File inputApk,
            File outputApk,
            String alias,
            PrivateKey privateKey,
            List<X509Certificate> certificates,
            int minSdkVersion
    ) throws Exception {
        ApkSigner.SignerConfig signerConfig = new ApkSigner.SignerConfig.Builder(alias, privateKey, certificates).build();
        ApkSigner signer = new ApkSigner.Builder(java.util.Collections.singletonList(signerConfig))
                .setInputApk(inputApk)
                .setOutputApk(outputApk)
                .setMinSdkVersion(minSdkVersion)
                .setV4SigningEnabled(false)
                .setAlignFileSize(false)
                .build();
        signer.sign();
    }

    private static final class SignInputs {
        final String signMode;
        final File keystoreFile;
        final String storePassword;
        final String alias;
        final String keyPassword;
        final String keystoreType;

        SignInputs(String signMode, File keystoreFile, String storePassword, String alias, String keyPassword, String keystoreType) {
            this.signMode = signMode;
            this.keystoreFile = keystoreFile;
            this.storePassword = storePassword;
            this.alias = alias;
            this.keyPassword = keyPassword;
            this.keystoreType = keystoreType;
        }
    }
}
