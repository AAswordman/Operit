package com.operit.apkreverse.runtime;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class SearchSupport {
    static final long MAX_TEXT_FILE_BYTES = 2L * 1024L * 1024L;
    static final int MAX_BINARY_WINDOW_BYTES = 96;
    private static final int DEFAULT_MAX_RESULTS = 100;
    private static final int MAX_RESULTS = 1000;
    private static final int PRINTABLE_MIN_LENGTH = 4;

    private SearchSupport() {
    }

    static String searchText(
            String inputPath,
            String query,
            String scope,
            boolean regex,
            boolean caseInsensitive,
            int maxResults
    ) throws Exception {
        File input = requireExistingPath(inputPath, "input_path");
        int limit = normalizeMaxResults(maxResults);
        JSONArray matches = new JSONArray();
        if (input.isDirectory()) {
            searchTextInDirectory(input, query, scope, regex, caseInsensitive, limit, matches);
        } else {
            searchTextInApk(input, query, scope, regex, caseInsensitive, limit, matches);
        }

        JSONObject payload = new JSONObject();
        payload.put("matchCount", matches.length());
        payload.put("matches", matches);
        return payload.toString();
    }

    static String searchAddress(String inputPath, String query, String scope, int maxResults) throws Exception {
        File input = requireExistingPath(inputPath, "input_path");
        int limit = normalizeMaxResults(maxResults);
        JSONArray matches = new JSONArray();
        if (input.isDirectory()) {
            searchAddressInDirectory(input, query, scope, limit, matches);
        } else {
            searchAddressInApk(input, query, scope, limit, matches);
        }

        JSONObject payload = new JSONObject();
        payload.put("matchCount", matches.length());
        payload.put("matches", matches);
        return payload.toString();
    }

    static File requireExistingFile(String path, String parameterName) {
        File file = new File(path);
        if (!file.exists()) {
            throw new IllegalArgumentException(parameterName + " does not exist: " + file.getAbsolutePath());
        }
        if (!file.isFile()) {
            throw new IllegalArgumentException(parameterName + " is not a file: " + file.getAbsolutePath());
        }
        return file;
    }

    private static File requireExistingPath(String path, String parameterName) {
        File file = new File(path);
        if (!file.exists()) {
            throw new IllegalArgumentException(parameterName + " does not exist: " + file.getAbsolutePath());
        }
        return file;
    }

    private static int normalizeMaxResults(int maxResults) {
        return maxResults > 0 ? Math.min(maxResults, MAX_RESULTS) : DEFAULT_MAX_RESULTS;
    }

    private static void searchTextInDirectory(
            File rootDir,
            String query,
            String scope,
            boolean regex,
            boolean caseInsensitive,
            int maxResults,
            JSONArray matches
    ) throws Exception {
        if ("native_strings".equals(scope)) {
            searchNativeStrings(rootDir, query, caseInsensitive, maxResults, matches);
            return;
        }
        Pattern pattern = Pattern.compile(regex ? query : Pattern.quote(query), caseInsensitive ? Pattern.CASE_INSENSITIVE : 0);
        if ("all".equals(scope)) {
            searchTextFiles(rootDir, pattern, "all", maxResults, matches);
            searchNativeStrings(rootDir, query, caseInsensitive, maxResults, matches);
        } else {
            searchTextFiles(rootDir, pattern, scope, maxResults, matches);
        }
    }

    private static void searchAddressInDirectory(File rootDir, String query, String scope, int maxResults, JSONArray matches) throws Exception {
        Pattern textPattern = Pattern.compile(Pattern.quote(query.trim()), Pattern.CASE_INSENSITIVE);
        if ("resource_id".equals(scope) || "smali_ref".equals(scope) || "jadx_ref".equals(scope) || "all".equals(scope)) {
            String textScope =
                    "resource_id".equals(scope) ? "all" :
                            "smali_ref".equals(scope) ? "smali" :
                                    "jadx_ref".equals(scope) ? "jadx" : "all";
            searchTextFiles(rootDir, textPattern, textScope, maxResults, matches);
            if (!"all".equals(scope)) {
                rewriteScope(matches, scope);
            }
        }
        if ("native_symbol".equals(scope) || "all".equals(scope)) {
            searchNativeSymbols(rootDir, query, maxResults, matches);
        }
        if ("native_offset".equals(scope) || "all".equals(scope)) {
            searchNativeOffsets(rootDir, query, maxResults, matches);
        }
        if ("hex_bytes".equals(scope)) {
            searchHexBytes(rootDir, query, maxResults, matches);
        } else if ("all".equals(scope) && looksLikeHexPattern(query)) {
            searchHexBytes(rootDir, query, maxResults, matches);
        }
    }

    private static void searchTextInApk(
            File apkFile,
            String query,
            String scope,
            boolean regex,
            boolean caseInsensitive,
            int maxResults,
            JSONArray matches
    ) throws Exception {
        Pattern pattern = Pattern.compile(regex ? query : Pattern.quote(query), caseInsensitive ? Pattern.CASE_INSENSITIVE : 0);
        String normalizedQuery = caseInsensitive ? query.toLowerCase(Locale.ROOT) : query;
        try (ZipFile zipFile = new ZipFile(apkFile)) {
            if ("manifest".equals(scope) || "all".equals(scope)) {
                searchManifestPreview(zipFile, pattern, maxResults, matches, "manifest");
            }
            if ("res".equals(scope) || "all".equals(scope)) {
                searchResourceEntriesInApk(zipFile, pattern, maxResults, matches, "res");
            }
            if ("smali".equals(scope) || "jadx".equals(scope) || "all".equals(scope)) {
                String dexScope = "smali".equals(scope) ? "smali" : "jadx".equals(scope) ? "jadx" : "dex";
                searchDexEntriesForPrintableText(zipFile, pattern, maxResults, matches, dexScope);
            }
            if ("native_strings".equals(scope) || "all".equals(scope)) {
                searchNativeStringsInApk(zipFile, normalizedQuery, caseInsensitive, maxResults, matches);
            }
        }
    }

    private static void searchAddressInApk(File apkFile, String query, String scope, int maxResults, JSONArray matches) throws Exception {
        try (ZipFile zipFile = new ZipFile(apkFile)) {
            if ("resource_id".equals(scope) || "all".equals(scope)) {
                searchResourceIdInApk(zipFile, query, maxResults, matches);
            }
            if ("smali_ref".equals(scope) || "jadx_ref".equals(scope) || "all".equals(scope)) {
                String dexScope = "smali_ref".equals(scope) ? "smali_ref" : "jadx_ref".equals(scope) ? "jadx_ref" : "dex_ref";
                searchDexEntriesForPrintableText(zipFile, Pattern.compile(Pattern.quote(query.trim()), Pattern.CASE_INSENSITIVE), maxResults, matches, dexScope);
            }
            if ("native_symbol".equals(scope) || "all".equals(scope)) {
                searchNativeSymbolsInApk(zipFile, query, maxResults, matches);
            }
            if ("native_offset".equals(scope) || "all".equals(scope)) {
                searchNativeOffsetsInApk(zipFile, query, maxResults, matches);
            }
            if ("hex_bytes".equals(scope)) {
                searchHexBytesInApk(zipFile, query, maxResults, matches);
            } else if ("all".equals(scope) && looksLikeHexPattern(query)) {
                searchHexBytesInApk(zipFile, query, maxResults, matches);
            }
        }
    }

    private static void searchManifestPreview(ZipFile zipFile, Pattern pattern, int maxResults, JSONArray matches, String scope) throws Exception {
        String manifestText = ApkArchiveSupport.readManifestPreview(zipFile);
        searchTextContent("AndroidManifest.xml", manifestText, pattern, scope, maxResults, matches);
    }

    private static void searchResourceEntriesInApk(ZipFile zipFile, Pattern pattern, int maxResults, JSONArray matches, String scope) throws Exception {
        java.util.Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements() && matches.length() < maxResults) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (entry.isDirectory() || name == null) {
                continue;
            }
            if (!name.startsWith("res/") && !"resources.arsc".equals(name)) {
                continue;
            }
            searchZipEntryText(zipFile, entry, pattern, maxResults, matches, scope);
        }
    }

    private static void searchZipEntryText(ZipFile zipFile, ZipEntry entry, Pattern pattern, int maxResults, JSONArray matches, String scope) throws Exception {
        String name = entry.getName();
        if (entry.getSize() >= 0 && entry.getSize() <= MAX_TEXT_FILE_BYTES && isTextFileCandidate(name)) {
            byte[] bytes = ApkArchiveSupport.readAllBytes(zipFile, entry);
            searchTextContent(name, new String(bytes, StandardCharsets.UTF_8), pattern, scope, maxResults, matches);
            return;
        }
        try (InputStream input = zipFile.getInputStream(entry)) {
            scanPrintableStrings(input, PRINTABLE_MIN_LENGTH, (offset, text) -> {
                if (matches.length() >= maxResults) {
                    return false;
                }
                Matcher matcher = pattern.matcher(text);
                if (!matcher.find()) {
                    return true;
                }
                JSONObject item = new JSONObject();
                item.put("filePath", name);
                item.put("offset", offset);
                item.put("matchText", matcher.group());
                item.put("lineContent", text);
                item.put("scope", scope);
                matches.put(item);
                return matches.length() < maxResults;
            });
        }
    }

    private static void searchDexEntriesForPrintableText(ZipFile zipFile, Pattern pattern, int maxResults, JSONArray matches, String scope) throws Exception {
        java.util.Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements() && matches.length() < maxResults) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (entry.isDirectory() || name == null || !name.endsWith(".dex")) {
                continue;
            }
            try (InputStream input = zipFile.getInputStream(entry)) {
                scanPrintableStrings(input, PRINTABLE_MIN_LENGTH, (offset, text) -> {
                    if (matches.length() >= maxResults) {
                        return false;
                    }
                    Matcher matcher = pattern.matcher(text);
                    if (!matcher.find()) {
                        return true;
                    }
                    JSONObject item = new JSONObject();
                    item.put("filePath", name);
                    item.put("offset", offset);
                    item.put("matchText", matcher.group());
                    item.put("lineContent", text);
                    item.put("scope", scope);
                    matches.put(item);
                    return matches.length() < maxResults;
                });
            }
        }
    }

    private static void searchResourceIdInApk(ZipFile zipFile, String query, int maxResults, JSONArray matches) throws Exception {
        Long numericValue = ElfSupport.parseLongOffset(query);
        byte[] littleEndianPattern = null;
        if (numericValue != null && numericValue >= 0 && numericValue <= 0xffffffffL) {
            littleEndianPattern = new byte[]{
                    (byte) (numericValue & 0xff),
                    (byte) ((numericValue >> 8) & 0xff),
                    (byte) ((numericValue >> 16) & 0xff),
                    (byte) ((numericValue >> 24) & 0xff)
            };
        }
        Pattern textPattern = Pattern.compile(Pattern.quote(query.trim()), Pattern.CASE_INSENSITIVE);
        searchManifestPreview(zipFile, textPattern, maxResults, matches, "resource_id");
        java.util.Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements() && matches.length() < maxResults) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (entry.isDirectory() || name == null) {
                continue;
            }
            if (!name.startsWith("res/") && !"resources.arsc".equals(name) && !name.endsWith(".dex")) {
                continue;
            }
            if (littleEndianPattern != null) {
                try (InputStream input = zipFile.getInputStream(entry)) {
                    for (Long offset : findPatternOffsets(input, littleEndianPattern, maxResults - matches.length())) {
                        JSONObject item = new JSONObject();
                        item.put("filePath", name);
                        item.put("offset", "0x" + ElfSupport.toHex(offset, 8));
                        item.put("matchText", query.trim());
                        item.put("scope", "resource_id");
                        item.put("matchKind", "binary_le32");
                        matches.put(item);
                        if (matches.length() >= maxResults) {
                            return;
                        }
                    }
                }
            }
            searchZipEntryText(zipFile, entry, textPattern, maxResults, matches, "resource_id");
        }
    }

    private static void searchNativeStrings(File rootDir, String query, boolean caseInsensitive, int maxResults, JSONArray matches) throws Exception {
        String normalizedQuery = caseInsensitive ? query.toLowerCase(Locale.ROOT) : query;
        for (File file : collectFiles(rootDir)) {
            if (matches.length() >= maxResults) {
                return;
            }
            String relative = relativePath(rootDir, file);
            if (!isNativeLibrary(relative)) {
                continue;
            }
            try (InputStream input = Files.newInputStream(file.toPath())) {
                scanPrintableStrings(input, PRINTABLE_MIN_LENGTH, (offset, text) -> {
                    if (matches.length() >= maxResults) {
                        return false;
                    }
                    String haystack = caseInsensitive ? text.toLowerCase(Locale.ROOT) : text;
                    if (!haystack.contains(normalizedQuery)) {
                        return true;
                    }
                    JSONObject item = new JSONObject();
                    item.put("filePath", relative);
                    item.put("offset", offset);
                    item.put("matchText", text);
                    item.put("scope", "native_strings");
                    matches.put(item);
                    return matches.length() < maxResults;
                });
            }
        }
    }

    private static void searchNativeStringsInApk(ZipFile zipFile, String normalizedQuery, boolean caseInsensitive, int maxResults, JSONArray matches) throws Exception {
        java.util.Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements() && matches.length() < maxResults) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (entry.isDirectory() || name == null || !isNativeLibrary(name)) {
                continue;
            }
            try (InputStream input = zipFile.getInputStream(entry)) {
                scanPrintableStrings(input, PRINTABLE_MIN_LENGTH, (offset, text) -> {
                    if (matches.length() >= maxResults) {
                        return false;
                    }
                    String haystack = caseInsensitive ? text.toLowerCase(Locale.ROOT) : text;
                    if (!haystack.contains(normalizedQuery)) {
                        return true;
                    }
                    JSONObject item = new JSONObject();
                    item.put("filePath", name);
                    item.put("offset", offset);
                    item.put("matchText", text);
                    item.put("scope", "native_strings");
                    matches.put(item);
                    return matches.length() < maxResults;
                });
            }
        }
    }

    private static void searchNativeSymbols(File rootDir, String query, int maxResults, JSONArray matches) throws Exception {
        String normalizedQuery = query.trim().toLowerCase(Locale.ROOT);
        for (File file : collectFiles(rootDir)) {
            if (matches.length() >= maxResults) {
                return;
            }
            String relative = relativePath(rootDir, file);
            if (!isNativeLibrary(relative)) {
                continue;
            }
            byte[] bytes = Files.readAllBytes(file.toPath());
            addNativeSymbolMatches(bytes, relative, normalizedQuery, maxResults, matches);
        }
    }

    private static void searchNativeSymbolsInApk(ZipFile zipFile, String query, int maxResults, JSONArray matches) throws Exception {
        String normalizedQuery = query.trim().toLowerCase(Locale.ROOT);
        java.util.Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements() && matches.length() < maxResults) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (entry.isDirectory() || name == null || !isNativeLibrary(name)) {
                continue;
            }
            byte[] bytes = ApkArchiveSupport.readAllBytes(zipFile, entry);
            addNativeSymbolMatches(bytes, name, normalizedQuery, maxResults, matches);
        }
    }

    private static void addNativeSymbolMatches(byte[] bytes, String filePath, String normalizedQuery, int maxResults, JSONArray matches) throws Exception {
        ElfSupport.ElfMetadata elf = ElfSupport.parseElfMetadata(bytes);
        if (elf == null) {
            return;
        }
        for (ElfSupport.ElfSymbol symbol : elf.symbols) {
            if (matches.length() >= maxResults) {
                return;
            }
            if (!symbol.name.toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
                continue;
            }
            Long mappedOffset = elf.mapAddressToOffset(symbol.address);
            JSONObject item = new JSONObject();
            item.put("filePath", filePath);
            item.put("symbolName", symbol.name);
            item.put("address", "0x" + ElfSupport.toHex(symbol.address, elf.is64 ? 16 : 8));
            item.put("fileOffset", mappedOffset == null ? JSONObject.NULL : "0x" + ElfSupport.toHex(mappedOffset, 8));
            item.put("pseudoDisassembly", toJsonArray(ElfSupport.buildPseudoDisassemblyAround(bytes, mappedOffset, 16, 32)));
            item.put("scope", "native_symbol");
            matches.put(item);
        }
    }

    private static void searchNativeOffsets(File rootDir, String query, int maxResults, JSONArray matches) throws Exception {
        Long parsedOffset = ElfSupport.parseLongOffset(query);
        if (parsedOffset == null || parsedOffset < 0) {
            return;
        }
        for (File file : collectFiles(rootDir)) {
            if (matches.length() >= maxResults) {
                return;
            }
            String relative = relativePath(rootDir, file);
            if (!isNativeLibrary(relative)) {
                continue;
            }
            byte[] bytes = Files.readAllBytes(file.toPath());
            addNativeOffsetMatch(bytes, relative, parsedOffset, matches);
        }
    }

    private static void searchNativeOffsetsInApk(ZipFile zipFile, String query, int maxResults, JSONArray matches) throws Exception {
        Long parsedOffset = ElfSupport.parseLongOffset(query);
        if (parsedOffset == null || parsedOffset < 0) {
            return;
        }
        java.util.Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements() && matches.length() < maxResults) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (entry.isDirectory() || name == null || !isNativeLibrary(name)) {
                continue;
            }
            byte[] bytes = ApkArchiveSupport.readAllBytes(zipFile, entry);
            addNativeOffsetMatch(bytes, name, parsedOffset, matches);
        }
    }

    private static void addNativeOffsetMatch(byte[] bytes, String filePath, long parsedOffset, JSONArray matches) throws Exception {
        if (parsedOffset >= bytes.length) {
            return;
        }
        JSONObject item = new JSONObject();
        item.put("filePath", filePath);
        item.put("offset", "0x" + ElfSupport.toHex(parsedOffset, 8));
        item.put("windowStart", "0x" + ElfSupport.toHex(Math.max(0L, parsedOffset - 16L), 8));
        item.put("windowHex", ElfSupport.windowHex(bytes, parsedOffset, MAX_BINARY_WINDOW_BYTES));
        item.put("pseudoDisassembly", toJsonArray(ElfSupport.buildPseudoDisassemblyAround(bytes, parsedOffset, 16, MAX_BINARY_WINDOW_BYTES)));
        item.put("scope", "native_offset");
        matches.put(item);
    }

    private static void searchHexBytes(File rootDir, String query, int maxResults, JSONArray matches) throws Exception {
        byte[] pattern = ElfSupport.parseHexPattern(query);
        for (File file : collectFiles(rootDir)) {
            if (matches.length() >= maxResults) {
                return;
            }
            String relative = relativePath(rootDir, file);
            if (!isNativeLibrary(relative)) {
                continue;
            }
            byte[] bytes = Files.readAllBytes(file.toPath());
            addHexMatches(bytes, relative, pattern, maxResults, matches);
        }
    }

    private static void searchHexBytesInApk(ZipFile zipFile, String query, int maxResults, JSONArray matches) throws Exception {
        byte[] pattern = ElfSupport.parseHexPattern(query);
        java.util.Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements() && matches.length() < maxResults) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (entry.isDirectory() || name == null || !isNativeLibrary(name)) {
                continue;
            }
            byte[] bytes = ApkArchiveSupport.readAllBytes(zipFile, entry);
            addHexMatches(bytes, name, pattern, maxResults, matches);
        }
    }

    private static void addHexMatches(byte[] bytes, String filePath, byte[] pattern, int maxResults, JSONArray matches) throws Exception {
        for (Integer offset : ElfSupport.findHexPatternOffsets(bytes, pattern, maxResults - matches.length())) {
            JSONObject item = new JSONObject();
            item.put("filePath", filePath);
            item.put("offset", "0x" + ElfSupport.toHex(offset, 8));
            item.put("matchedHex", ElfSupport.hexBytes(pattern, 0, pattern.length));
            item.put("windowHex", ElfSupport.windowHex(bytes, offset, MAX_BINARY_WINDOW_BYTES));
            item.put("pseudoDisassembly", toJsonArray(ElfSupport.buildPseudoDisassemblyAround(bytes, offset.longValue(), 16, MAX_BINARY_WINDOW_BYTES)));
            item.put("scope", "hex_bytes");
            matches.put(item);
            if (matches.length() >= maxResults) {
                return;
            }
        }
    }

    private static void searchTextFiles(File rootDir, Pattern pattern, String scope, int maxResults, JSONArray matches) throws Exception {
        for (File file : collectFiles(rootDir)) {
            if (matches.length() >= maxResults) {
                return;
            }
            String relative = relativePath(rootDir, file);
            if (!matchesTextScope(relative, scope) || file.length() > MAX_TEXT_FILE_BYTES) {
                continue;
            }
            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            searchTextContent(relative, content, pattern, "all".equals(scope) ? inferScopeFromRelativePath(relative) : scope, maxResults, matches);
        }
    }

    private static void searchTextContent(String filePath, String content, Pattern pattern, String scope, int maxResults, JSONArray matches) throws Exception {
        String[] lines = content.split("\\r?\\n", -1);
        for (int lineIndex = 0; lineIndex < lines.length && matches.length() < maxResults; lineIndex += 1) {
            Matcher matcher = pattern.matcher(lines[lineIndex]);
            if (!matcher.find()) {
                continue;
            }
            JSONObject item = new JSONObject();
            item.put("filePath", filePath);
            item.put("lineNumber", lineIndex + 1);
            item.put("lineContent", lines[lineIndex]);
            item.put("matchText", matcher.group());
            item.put("scope", scope);
            matches.put(item);
        }
    }

    private static List<File> collectFiles(File rootDir) throws Exception {
        List<File> files = new ArrayList<>();
        try (Stream<java.nio.file.Path> stream = Files.walk(rootDir.toPath())) {
            stream.filter(Files::isRegularFile).forEach(path -> files.add(path.toFile()));
        }
        files.sort((left, right) -> relativePath(rootDir, left).compareTo(relativePath(rootDir, right)));
        return files;
    }

    private static JSONArray toJsonArray(List<String> values) {
        JSONArray array = new JSONArray();
        for (String value : values) {
            array.put(value);
        }
        return array;
    }

    private static void rewriteScope(JSONArray matches, String scope) throws Exception {
        for (int index = 0; index < matches.length(); index += 1) {
            matches.getJSONObject(index).put("scope", scope);
        }
    }

    private static boolean matchesTextScope(String relative, String scope) {
        if ("manifest".equals(scope)) return "AndroidManifest.xml".equals(relative);
        if ("res".equals(scope)) return relative.startsWith("res/");
        if ("smali".equals(scope)) return relative.startsWith("smali");
        if ("jadx".equals(scope)) return relative.startsWith("jadx/") || relative.endsWith(".java") || relative.endsWith(".kt");
        return "all".equals(scope) && isTextFileCandidate(relative);
    }

    private static boolean isTextFileCandidate(String relative) {
        String lower = relative.toLowerCase(Locale.ROOT);
        return lower.endsWith(".xml") || lower.endsWith(".smali") || lower.endsWith(".java") || lower.endsWith(".kt")
                || lower.endsWith(".txt") || lower.endsWith(".json") || lower.endsWith(".yml")
                || lower.endsWith(".yaml") || lower.endsWith(".properties") || lower.endsWith(".mf")
                || lower.endsWith(".html") || lower.endsWith(".js") || lower.endsWith(".css");
    }

    private static boolean isNativeLibrary(String relative) {
        return relative.replace("\\", "/").toLowerCase(Locale.ROOT).endsWith(".so");
    }

    private static boolean looksLikeHexPattern(String query) {
        String cleaned = query == null ? "" : query.trim().replaceAll("(?i)0x", "").replaceAll("[^0-9a-fA-F]", "");
        return !cleaned.isEmpty() && cleaned.length() % 2 == 0;
    }

    private static String inferScopeFromRelativePath(String relative) {
        String normalized = relative.replace("\\", "/");
        if ("AndroidManifest.xml".equals(normalized)) return "manifest";
        if (normalized.startsWith("jadx/")) return "jadx";
        if (normalized.startsWith("res/")) return "res";
        if (normalized.startsWith("smali")) return "smali";
        if (normalized.endsWith(".java") || normalized.endsWith(".kt")) return "jadx";
        if (normalized.endsWith(".so")) return "native";
        return "text";
    }

    private static String relativePath(File rootDir, File file) {
        return rootDir.toPath().toAbsolutePath().normalize().relativize(file.toPath().toAbsolutePath().normalize()).toString().replace("\\", "/");
    }

    private static void scanPrintableStrings(InputStream input, int minLength, PrintableStringVisitor visitor) throws Exception {
        byte[] buffer = new byte[16 * 1024];
        long absoluteOffset = 0L;
        long sequenceStart = -1L;
        StringBuilder builder = new StringBuilder();
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read == 0) {
                continue;
            }
            for (int index = 0; index < read; index += 1) {
                int value = buffer[index] & 0xff;
                if (value >= 32 && value <= 126) {
                    if (sequenceStart < 0) {
                        sequenceStart = absoluteOffset + index;
                    }
                    builder.append((char) value);
                    continue;
                }
                if (!flushPrintableString(builder, visitor, minLength, sequenceStart)) {
                    return;
                }
                builder.setLength(0);
                sequenceStart = -1L;
            }
            absoluteOffset += read;
        }
        flushPrintableString(builder, visitor, minLength, sequenceStart);
    }

    private static boolean flushPrintableString(StringBuilder builder, PrintableStringVisitor visitor, int minLength, long sequenceStart) throws Exception {
        if (sequenceStart >= 0 && builder.length() >= minLength) {
            return visitor.visit(sequenceStart, builder.toString());
        }
        return true;
    }

    private static List<Long> findPatternOffsets(InputStream input, byte[] pattern, int maxResults) throws Exception {
        List<Long> offsets = new ArrayList<>();
        if (pattern == null || pattern.length == 0 || maxResults <= 0) {
            return offsets;
        }
        byte[] buffer = new byte[16 * 1024];
        byte[] window = new byte[Math.max(0, pattern.length - 1)];
        int windowLength = 0;
        long absoluteOffset = 0L;
        int read;
        while ((read = input.read(buffer)) >= 0 && offsets.size() < maxResults) {
            if (read == 0) {
                continue;
            }
            byte[] combined = new byte[windowLength + read];
            if (windowLength > 0) {
                System.arraycopy(window, 0, combined, 0, windowLength);
            }
            System.arraycopy(buffer, 0, combined, windowLength, read);
            for (int index = 0; index <= combined.length - pattern.length && offsets.size() < maxResults; index += 1) {
                boolean matched = true;
                for (int patternIndex = 0; patternIndex < pattern.length; patternIndex += 1) {
                    if ((combined[index + patternIndex] & 0xff) != (pattern[patternIndex] & 0xff)) {
                        matched = false;
                        break;
                    }
                }
                if (matched) {
                    offsets.add(absoluteOffset - windowLength + index);
                }
            }
            windowLength = Math.min(pattern.length - 1, combined.length);
            if (windowLength > 0) {
                System.arraycopy(combined, combined.length - windowLength, window, 0, windowLength);
            }
            absoluteOffset += read;
        }
        return offsets;
    }

    @FunctionalInterface
    private interface PrintableStringVisitor {
        boolean visit(long offset, String text) throws Exception;
    }
}
