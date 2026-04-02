/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Grimmory contributors
 * Copyright (C) 2025-2026 Booklore contributors
 */
package org.grimmory.epub4j.native_parsing;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.*;
import static org.grimmory.epub4j.native_parsing.PanamaConstants.*;

/**
 * Native encoding detector using ICU via Panama FFM.
 *
 * <p>Provides robust character encoding detection and conversion to UTF-8.
 * Uses ICU (International Components for Unicode) when available, with
 * graceful fallback to UTF-8 assumption when ICU is not loaded.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * try (NativeEncodingDetector detector = new NativeEncodingDetector()) {
 *     byte[] data = Files.readAllBytes(path);
 *     EncodingResult result = detector.detectEncoding(data);
 *     System.out.println("Detected: " + result.encoding() + " (" + result.confidence() + "%)");
 *
 *     byte[] utf8 = detector.convertToUtf8(data, result.encoding());
 *     String text = new String(utf8, StandardCharsets.UTF_8);
 * }
 * }</pre>
 *
 * <p>Memory management:</p>
 * <ul>
 *     <li>Implements AutoCloseable for proper resource cleanup</li>
 *     <li>Always use try-with-resources to ensure native memory is freed</li>
 * </ul>
 *
 * @author Grimmory
 */
public class NativeEncodingDetector implements AutoCloseable {

    private final Arena arena;
    private MemorySegment detectorPointer;
    private boolean closed = false;
    private boolean nativeAvailable = true;

    /**
     * Result of encoding detection.
     *
     * @param encoding   Detected encoding name (e.g., "UTF-8", "ISO-8859-1")
     * @param confidence Confidence score 0-100
     */
    public record EncodingResult(String encoding, int confidence) {}

    /**
     * Create a new encoding detector instance.
     *
     * @throws EpubNativeException if creation fails
     */
    public NativeEncodingDetector() {
        this.arena = Arena.ofConfined();
        try {
            PointerHolder detectorHolder = PointerHolder.allocate(arena);

            int errorCode = EpubNativeHeaders.epub_native_encoding_detector_create(
                    detectorHolder.segment()
            );

            checkError(errorCode);

            this.detectorPointer = detectorHolder.segment().get(ADDRESS, 0);

        } catch (UnsatisfiedLinkError e) {
            // Native library not available - will use fallback
            this.nativeAvailable = false;
            this.detectorPointer = MemorySegment.NULL;
        } catch (Throwable e) {
            arena.close();
            throw new EpubNativeException("Failed to create encoding detector", e);
        }
    }

    /**
     * Detect the encoding of byte data.
     *
     * @param data Input byte data
     * @return Encoding result with detected encoding and confidence score
     * @throws EpubNativeException if detection fails
     */
    public EncodingResult detectEncoding(byte[] data) {
        checkOpen();

        if (!nativeAvailable) {
            // Fallback: assume UTF-8 with medium confidence
            return new EncodingResult("UTF-8", 50);
        }

        try (Arena localArena = Arena.ofConfined()) {
            MemorySegment dataSegment = localArena.allocateFrom(JAVA_BYTE, data);
            PointerHolder encodingHolder = PointerHolder.allocate(localArena);
            MemorySegment confidenceHolder = localArena.allocate(JAVA_INT);

            int errorCode = EpubNativeHeaders.epub_native_detect_encoding(
                detectorPointer,
                dataSegment,
                data.length,
                    encodingHolder.segment(),
                confidenceHolder
            );

            checkError(errorCode);

            String encoding = toJavaString(encodingHolder.segment().get(ADDRESS, 0));
            int confidence = confidenceHolder.get(JAVA_INT, 0);

            // Free native string
            nativeStringFree(encodingHolder.segment().get(ADDRESS, 0));

            return new EncodingResult(encoding, confidence);

        } catch (Throwable e) {
            throw new EpubNativeException("Failed to detect encoding", e);
        }
    }

    /**
     * Convert data from a source encoding to UTF-8.
     *
     * @param data           Input byte data
     * @param sourceEncoding Source encoding name (e.g., "ISO-8859-1")
     * @return UTF-8 encoded byte array
     * @throws EpubNativeException if conversion fails
     */
    public byte[] convertToUtf8(byte[] data, String sourceEncoding) {
        checkOpen();

        if (!nativeAvailable) {
            // Fallback: return data as-is (assume already UTF-8)
            return data.clone();
        }

        try (Arena localArena = Arena.ofConfined()) {
            MemorySegment sourceEncodingSegment = toNativeString(sourceEncoding, localArena);
            MemorySegment sourceDataSegment = localArena.allocateFrom(JAVA_BYTE, data);
            PointerHolder utf8Holder = PointerHolder.allocate(localArena);
            MemorySegment utf8LengthHolder = localArena.allocate(JAVA_LONG);

            int errorCode = EpubNativeHeaders.epub_native_convert_to_utf8(
                sourceEncodingSegment,
                sourceDataSegment,
                data.length,
                    utf8Holder.segment(),
                utf8LengthHolder
            );

            checkError(errorCode);

            long utf8Length = utf8LengthHolder.get(JAVA_LONG, 0);
            MemorySegment utf8Ptr = utf8Holder.segment().get(ADDRESS, 0);

            byte[] utf8Data = utf8Ptr.reinterpret(utf8Length).toArray(JAVA_BYTE);

            // Free native string
            nativeStringFree(utf8Ptr);

            return utf8Data;

        } catch (Throwable e) {
            throw new EpubNativeException("Failed to convert to UTF-8", e);
        }
    }

    /**
     * Detect encoding and convert to UTF-8 in one step.
     *
     * @param data Input byte data
     * @return UTF-8 encoded byte array
     * @throws EpubNativeException if detection or conversion fails
     */
    public byte[] detectAndConvertToUtf8(byte[] data) {
        EncodingResult result = detectEncoding(data);
        return convertToUtf8(data, result.encoding);
    }

    /**
     * Check if native library is available.
     *
     * @return true if ICU-based native detection is available
     */
    public boolean isNativeAvailable() {
        return nativeAvailable;
    }

    private void checkOpen() {
        if (closed) {
            throw new IllegalStateException("EncodingDetector is closed");
        }
    }

    @Override
    public void close() {
        if (!closed) {
            if (detectorPointer != null && !detectorPointer.equals(MemorySegment.NULL)) {
                try {
                    EpubNativeHeaders.epub_native_encoding_detector_free(detectorPointer);
                } catch (Throwable e) {
                    // Log but don't throw
                }
            }
            arena.close();
            closed = true;
        }
    }
}
