/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Grimmory contributors
 * Copyright (C) 2025-2026 Booklore contributors
 */
package org.grimmory.epub4j.native_parsing;

import java.io.*;
import java.lang.foreign.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Panama FFM bindings for epub4j-native C++ library.
 *
 * This class provides high-level utilities on top of the jextract-generated
 * {@link EpubNativeHeaders} low-level bindings.
 *
 * <p>Memory management:</p>
 * <ul>
 *     <li>Native allocations are managed through Arena</li>
 *     <li>Strings returned from native code must be freed with nativeStringFree()</li>
 *     <li>Document objects must be freed with their respective free() methods</li>
 * </ul>
 *
 * <h2>Breaking API Changes (jextract migration)</h2>
 * <ul>
 *     <li>Error constants moved to {@link EpubNativeHeaders} constants class</li>
 *     <li>Function handles accessed via {@code EpubNativeHeaders.epub_native_*}$handle()}</li>
 *     <li>Direct function calls via {@code EpubNativeHeaders.epub_native_*()}</li>
 *     <li>TOC reference struct now uses {@link EpubNativeTOCReference} class</li>
 * </ul>
 */
public class PanamaConstants {

    private static volatile boolean loaded;
    private static final Object LOAD_LOCK = new Object();
    private static final long MAX_C_STRING_BYTES = Long.getLong("epub4j.native.maxCStringBytes", 4L * 1024L * 1024L);

    private PanamaConstants() {
        // Utility class - should not be instantiated
    }

    // Load native library (required before jextract bindings can work)
    static {
        loadNativeLibrary();
    }

    private static void loadNativeLibrary() {
        if (loaded) {
            return;
        }
        synchronized (LOAD_LOCK) {
            if (loaded) {
                return;
            }
            doLoadNativeLibrary();
            loaded = true;
        }
    }

    private static void doLoadNativeLibrary() {

        String configuredNativePath = System.getProperty("epub4j.native.path");
        if (configuredNativePath == null || configuredNativePath.isBlank()) {
            configuredNativePath = System.getenv("EPUB4J_NATIVE_PATH");
        }

        if (configuredNativePath != null && !configuredNativePath.isBlank()) {
            try {
                System.load(configuredNativePath);
                return;
            } catch (UnsatisfiedLinkError e) {
                throw new RuntimeException("Failed to load native library from configured path: " + configuredNativePath, e);
            }
        }

        UnsatisfiedLinkError systemLoadError;
        try {
            System.loadLibrary("epub4j_native");
            return;
        } catch (UnsatisfiedLinkError e) {
            systemLoadError = e;
        }

        String libName = mapLibraryName();
        List<String> resourceCandidates = getPlatformResourceDirs();
        for (String platformDir : resourceCandidates) {
            if (tryLoadFromClasspath(platformDir, libName)) {
                return;
            }
        }

        throw new RuntimeException(
            "Failed to load native library: " + libName +
            " for platform candidates " + resourceCandidates +
            ". Install a system copy (e.g. in Docker/Alpine) or provide -Depub4j.native.path.",
            systemLoadError
        );
    }

    /**
     * Loads native libraries from a classpath platform directory.
     * If a native-libs.txt manifest exists, loads transitive dependencies
     * in declared order before the main library.
     */
    private static boolean tryLoadFromClasspath(String platformDir, String mainLibName) {
        String mainResource = "/" + platformDir + "/" + mainLibName;
        try (InputStream probe = PanamaConstants.class.getResourceAsStream(mainResource)) {
            if (probe == null) {
                return false;
            }
        } catch (IOException e) {
            return false;
        }

        Path tempDir;
        try {
            tempDir = Files.createTempDirectory("epub4j_native-");
            // Clean up the whole directory on JVM exit
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    try (var walk = Files.walk(tempDir)) {
                        walk.sorted(Comparator.reverseOrder())
                            .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
                    }
                } catch (IOException ignored) {}
            }));
        } catch (IOException e) {
            return false;
        }

        List<String> manifest = readLibraryManifest(platformDir);
        // Load transitive dependencies first (manifest order), then the main library
        List<String> loadOrder = new ArrayList<>(manifest.stream()
                .filter(lib -> !lib.equals(mainLibName))
                .toList());
        loadOrder.add(mainLibName);

        try {
            for (String libFileName : loadOrder) {
                String resourcePath = "/" + platformDir + "/" + libFileName;
                try (InputStream in = PanamaConstants.class.getResourceAsStream(resourcePath)) {
                    if (in == null) {
                        continue;
                    }
                    Path tempLib = tempDir.resolve(libFileName);
                    Files.copy(in, tempLib, StandardCopyOption.REPLACE_EXISTING);
                    tempLib.toFile().setReadable(true, true);
                    tempLib.toFile().setExecutable(true, true);
                    System.load(tempLib.toString());
                }
            }
            return true;
        } catch (IOException | UnsatisfiedLinkError e) {
            return false;
        }
    }

    /**
     * Reads the native-libs.txt manifest listing library load order.
     * Lines starting with # and blank lines are skipped.
     * Returns empty list if no manifest exists.
     */
    private static List<String> readLibraryManifest(String platformDir) {
        String manifestPath = "/" + platformDir + "/native-libs.txt";
        try (InputStream in = PanamaConstants.class.getResourceAsStream(manifestPath)) {
            if (in == null) {
                return List.of();
            }
            try (var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return reader.lines()
                        .map(String::strip)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .toList();
            }
        } catch (IOException e) {
            return List.of();
        }
    }

    private static List<String> getPlatformResourceDirs() {
        String os = System.getProperty("os.name").toLowerCase();
        String arch = normalizeArch(System.getProperty("os.arch", "x86_64"));
        List<String> candidates = new ArrayList<>();

        if (os.contains("win")) {
            candidates.add("windows-" + arch);
        } else if (os.contains("mac")) {
            candidates.add("macos-" + arch);
        } else {
            // musl-linked binaries crash when loaded into a glibc process and vice-versa
            if (isMusl()) {
                candidates.add("linux-musl-" + arch);
            }
            candidates.add("linux-" + arch);
        }

        if (!arch.equals(System.getProperty("os.arch", ""))) {
            if (os.contains("win")) {
                candidates.add("windows-" + System.getProperty("os.arch", ""));
            } else if (os.contains("mac")) {
                candidates.add("macos-" + System.getProperty("os.arch", ""));
            } else {
                candidates.add("linux-" + System.getProperty("os.arch", ""));
            }
        }

        return candidates;
    }

    /**
     * Detects whether the current system uses musl libc (e.g. Alpine Linux).
     * Checks for the musl dynamic linker and /proc/self/maps entries.
     */
    static boolean isMusl() {
        // musl ships its own dynamic linker; its presence is the most reliable signal
        try {
            Path libDir = Path.of("/lib");
            if (Files.isDirectory(libDir)) {
                try (var stream = Files.list(libDir)) {
                    if (stream.anyMatch(p -> p.getFileName().toString().startsWith("ld-musl-"))) {
                        return true;
                    }
                }
            }
        } catch (IOException ignored) {
            // /lib may not be listable in some container sandboxes
        }

        // Fallback: the process memory map will reference musl's shared object
        try {
            Path maps = Path.of("/proc/self/maps");
            if (Files.isReadable(maps)) {
                String content = Files.readString(maps);
                if (content.contains("musl")) {
                    return true;
                }
            }
        } catch (IOException ignored) {
            // /proc may not be mounted (Docker scratch images, non-Linux hosts)
        }

        return false;
    }

    private static String normalizeArch(String arch) {
        String lower = arch.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "x86_64", "amd64" -> "x86_64";
            case "aarch64", "arm64" -> "aarch64";
            case "x86", "i386", "i486", "i586", "i686" -> "x86";
            default -> lower;
        };
    }

    private static String getLibraryExtension() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return ".dll";
        } else if (os.contains("mac")) {
            return ".dylib";
        } else {
            return ".so";
        }
    }

    private static String mapLibraryName() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return "epub4j_native" + ".dll";
        } else if (os.contains("mac")) {
            return "lib" + "epub4j_native" + ".dylib";
        } else {
            return "lib" + "epub4j_native" + ".so";
        }
    }

    /** Error code: Success */
    public static final int EPUB_NATIVE_SUCCESS = 0;

    /** Error code: Parse error */
    public static final int EPUB_NATIVE_ERROR_PARSE = 1;

    /** Error code: I/O error */
    public static final int EPUB_NATIVE_ERROR_IO = 2;

    /** Error code: Memory error */
    public static final int EPUB_NATIVE_ERROR_MEMORY = 3;

    /** Error code: Invalid argument */
    public static final int EPUB_NATIVE_ERROR_INVALID_ARG = 4;

    /** Error code: Not found */
    public static final int EPUB_NATIVE_ERROR_NOT_FOUND = 5;

    /** Error code: Namespace error */
    public static final int EPUB_NATIVE_ERROR_NAMESPACE = 6;

    /**
     * Convert Java string to native C string.
     */
    public static MemorySegment toNativeString(String str, Arena arena) {
        if (str == null) {
            return MemorySegment.NULL;
        }
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        MemorySegment nativeStr = arena.allocate(bytes.length + 1);
        nativeStr.copyFrom(MemorySegment.ofArray(bytes));
        nativeStr.set(ValueLayout.JAVA_BYTE, bytes.length, (byte) 0);
        return nativeStr;
    }

    /**
     * Convert native C string to Java string.
     * The MAX_C_STRING_BYTES cap (default 4 MB) limits the scan range for
     * null-terminated strings to prevent reading unbounded native memory.
     */
    public static String toJavaString(MemorySegment nativeStr) {
        if (nativeStr == null || nativeStr.equals(MemorySegment.NULL)) {
            return null;
        }
        MemorySegment stringView = nativeStr;
        if (nativeStr.byteSize() == 0) {
            stringView = nativeStr.reinterpret(MAX_C_STRING_BYTES);
        }
        return stringView.getString(0);
    }

    /**
     * Free native string returned by library
     */
    public static void nativeStringFree(MemorySegment str) {
        if (str != null && !str.equals(MemorySegment.NULL)) {
            try {
                EpubNativeHeaders.epub_native_string_free(str);
            } catch (Throwable e) {
                throw new RuntimeException("Failed to free native string", e);
            }
        }
    }

    /**
     * Get library version
     */
    public static String getVersion() {
        try {
            MemorySegment result = EpubNativeHeaders.epub_native_get_version();
            return toJavaString(result);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to get library version", e);
        }
    }

    /**
     * Get last error message
     */
    public static String getLastError() {
        try {
            MemorySegment result = EpubNativeHeaders.epub_native_get_last_error();
            return toJavaString(result);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to get last error", e);
        }
    }

    /**
     * Check error code and throw exception if not success
     */
    public static void checkError(int errorCode) {
        if (errorCode != EPUB_NATIVE_SUCCESS) {
            String errorMsg = getLastError();
            throw new EpubNativeException("Native error " + errorCode + ": " + errorMsg);
        }
    }

    /**
     * Exception thrown by native operations.
     */
    public static class EpubNativeException extends RuntimeException {
        public EpubNativeException(String message) {
            super(message);
        }

        public EpubNativeException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
         * Helper class for holding pointer references in method calls.
         * Used to pass pointer-by-reference to native functions.
         */
        public record PointerHolder(MemorySegment segment) {
            public static PointerHolder allocate(Arena arena) {
                return new PointerHolder(arena.allocate(ValueLayout.ADDRESS));
            }
        }
}
