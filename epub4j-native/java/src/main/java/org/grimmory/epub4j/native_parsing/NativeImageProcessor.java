package org.grimmory.epub4j.native_parsing;

import static java.lang.foreign.ValueLayout.*;
import static org.grimmory.epub4j.native_parsing.PanamaConstants.*;

import java.lang.foreign.Arena;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;

/**
 * High-level Java wrapper for native image processing via libjpeg-turbo, libpng, and libwebp.
 *
 * <p>All operations are stateless (no native handle lifecycle). Output buffers are off-heap
 * and must be freed by closing the returned {@link ImageData}.
 *
 * <p>Callers should check {@link #isAvailable()} before use. When native image support
 * is not present, the EPUB optimization pipeline falls back to Java ImageIO.
 */
public final class NativeImageProcessor {

    /** Image formats matching the C constants in epub_native.h */
    public enum ImageFormat {
        UNKNOWN(0),
        JPEG(1),
        PNG(2),
        WEBP(3);

        private final int nativeValue;

        ImageFormat(int nativeValue) {
            this.nativeValue = nativeValue;
        }

        public int nativeValue() {
            return nativeValue;
        }

        public static ImageFormat fromNative(int value) {
            return switch (value) {
                case 1 -> JPEG;
                case 2 -> PNG;
                case 3 -> WEBP;
                default -> UNKNOWN;
            };
        }
    }

    /** Image dimensions and detected format. */
    public record ImageDimensions(int width, int height, ImageFormat format) {}

    /**
     * Native image data backed by an off-heap buffer.
     * Must be closed to free the native memory.
     */
    public static final class ImageData implements AutoCloseable {
        private MemorySegment pointer;
        private final long length;

        ImageData(MemorySegment pointer, long length) {
            this.pointer = pointer;
            this.length = length;
        }

        /** Copy native data to a Java byte array. */
        public byte[] toByteArray() {
            if (pointer == null || pointer.equals(MemorySegment.NULL)) {
                return new byte[0];
            }
            MemorySegment view = pointer.reinterpret(length);
            byte[] result = new byte[(int) length];
            MemorySegment.copy(view, JAVA_BYTE, 0, result, 0, (int) length);
            return result;
        }

        public long length() {
            return length;
        }

        @Override
        public void close() {
            if (pointer != null && !pointer.equals(MemorySegment.NULL)) {
                EpubNativeHeaders.epub_native_image_data_free(pointer);
                pointer = MemorySegment.NULL;
            }
        }
    }

    private static final boolean NATIVE_AVAILABLE;

    static {
        boolean available = false;
        try {
            // Trigger native library loading
            Class.forName("org.grimmory.epub4j.native_parsing.PanamaConstants");
            SymbolLookup lookup = SymbolLookup.loaderLookup()
                .or(Linker.nativeLinker().defaultLookup());
            // Probe for the image availability function
            available = lookup.find("epub_native_image_has_jpeg").isPresent();
        } catch (Throwable ignored) {
            // Native library not loaded or image symbols absent
        }
        NATIVE_AVAILABLE = available;
    }

    private NativeImageProcessor() {}

    /** Returns true if native image processing functions are available. */
    public static boolean isAvailable() {
        return NATIVE_AVAILABLE;
    }

    /** Returns true if native JPEG support (libjpeg-turbo) is compiled in. */
    public static boolean hasJpeg() {
        return NATIVE_AVAILABLE && EpubNativeHeaders.epub_native_image_has_jpeg() != 0;
    }

    /** Returns true if native PNG support (libpng) is compiled in. */
    public static boolean hasPng() {
        return NATIVE_AVAILABLE && EpubNativeHeaders.epub_native_image_has_png() != 0;
    }

    /** Returns true if native WebP support (libwebp) is compiled in. */
    public static boolean hasWebp() {
        return NATIVE_AVAILABLE && EpubNativeHeaders.epub_native_image_has_webp() != 0;
    }

    /**
     * Read image dimensions and format without full decode.
     *
     * @param imageData raw image file bytes (JPEG, PNG, or WebP)
     * @return dimensions and detected format
     * @throws EpubNativeException on parse error or unsupported format
     */
    public static ImageDimensions getDimensions(byte[] imageData) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment dataSeg = arena.allocate(imageData.length);
            dataSeg.copyFrom(MemorySegment.ofArray(imageData));

            MemorySegment widthOut  = arena.allocate(JAVA_INT);
            MemorySegment heightOut = arena.allocate(JAVA_INT);
            MemorySegment formatOut = arena.allocate(JAVA_INT);

            int err = EpubNativeHeaders.epub_native_image_get_dimensions(
                dataSeg, imageData.length, widthOut, heightOut, formatOut);
            checkError(err);

            return new ImageDimensions(
                widthOut.get(JAVA_INT, 0),
                heightOut.get(JAVA_INT, 0),
                ImageFormat.fromNative(formatOut.get(JAVA_INT, 0)));
        }
    }

    /**
     * Read image dimensions from an off-heap buffer.
     *
     * @param data MemorySegment containing image bytes
     * @param length byte length of the image data
     * @return dimensions and detected format
     */
    public static ImageDimensions getDimensions(MemorySegment data, long length) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment widthOut  = arena.allocate(JAVA_INT);
            MemorySegment heightOut = arena.allocate(JAVA_INT);
            MemorySegment formatOut = arena.allocate(JAVA_INT);

            int err = EpubNativeHeaders.epub_native_image_get_dimensions(
                data, length, widthOut, heightOut, formatOut);
            checkError(err);

            return new ImageDimensions(
                widthOut.get(JAVA_INT, 0),
                heightOut.get(JAVA_INT, 0),
                ImageFormat.fromNative(formatOut.get(JAVA_INT, 0)));
        }
    }

    /**
     * Lossless JPEG optimization: progressive encoding + Huffman table optimization.
     * Operates at the DCT coefficient level, no generation loss.
     *
     * @param jpegData raw JPEG file bytes
     * @return optimized JPEG data (caller must close)
     */
    public static ImageData optimizeJpeg(byte[] jpegData) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment dataSeg = arena.allocate(jpegData.length);
            dataSeg.copyFrom(MemorySegment.ofArray(jpegData));

            PointerHolder outData = PointerHolder.allocate(arena);
            MemorySegment outLen = arena.allocate(JAVA_LONG);

            int err = EpubNativeHeaders.epub_native_jpeg_optimize(
                dataSeg, jpegData.length, outData.segment(), outLen);
            checkError(err);

            return new ImageData(
                outData.segment().get(ADDRESS, 0),
                outLen.get(JAVA_LONG, 0));
        }
    }

    /**
     * Lossy JPEG re-compression at a target quality level.
     *
     * @param jpegData raw JPEG file bytes
     * @param quality JPEG quality 1-100
     * @param progressive true for progressive JPEG output
     * @return compressed JPEG data (caller must close)
     */
    public static ImageData compressJpeg(byte[] jpegData, int quality, boolean progressive) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment dataSeg = arena.allocate(jpegData.length);
            dataSeg.copyFrom(MemorySegment.ofArray(jpegData));

            PointerHolder outData = PointerHolder.allocate(arena);
            MemorySegment outLen = arena.allocate(JAVA_LONG);

            int err = EpubNativeHeaders.epub_native_jpeg_compress(
                dataSeg, jpegData.length, quality, progressive ? 1 : 0,
                outData.segment(), outLen);
            checkError(err);

            return new ImageData(
                outData.segment().get(ADDRESS, 0),
                outLen.get(JAVA_LONG, 0));
        }
    }

    /**
     * Optimize a PNG image: strip ancillary chunks, maximize compression.
     *
     * @param pngData raw PNG file bytes
     * @param stripAncillary true to remove non-critical chunks (comments, timestamps, etc.)
     * @return optimized PNG data (caller must close)
     */
    public static ImageData optimizePng(byte[] pngData, boolean stripAncillary) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment dataSeg = arena.allocate(pngData.length);
            dataSeg.copyFrom(MemorySegment.ofArray(pngData));

            PointerHolder outData = PointerHolder.allocate(arena);
            MemorySegment outLen = arena.allocate(JAVA_LONG);

            int err = EpubNativeHeaders.epub_native_png_optimize(
                dataSeg, pngData.length, stripAncillary ? 1 : 0,
                outData.segment(), outLen);
            checkError(err);

            return new ImageData(
                outData.segment().get(ADDRESS, 0),
                outLen.get(JAVA_LONG, 0));
        }
    }

    /**
     * Encode raw RGBA pixels to WebP.
     *
     * @param rgbaPixels raw RGBA pixel data (4 bytes per pixel)
     * @param width image width in pixels
     * @param height image height in pixels
     * @param quality lossy quality 0-100, or -1 for lossless
     * @return WebP encoded data (caller must close)
     */
    public static ImageData encodeWebp(byte[] rgbaPixels, int width, int height, int quality) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment pixelSeg = arena.allocate(rgbaPixels.length);
            pixelSeg.copyFrom(MemorySegment.ofArray(rgbaPixels));

            PointerHolder outData = PointerHolder.allocate(arena);
            MemorySegment outLen = arena.allocate(JAVA_LONG);

            int stride = width * 4;
            int err = EpubNativeHeaders.epub_native_webp_encode(
                pixelSeg, width, height, stride, quality, outData.segment(), outLen);
            checkError(err);

            return new ImageData(
                outData.segment().get(ADDRESS, 0),
                outLen.get(JAVA_LONG, 0));
        }
    }

    /**
     * Resize an image with high-quality filtering (Mitchell/Catmull-Rom).
     * Decodes input automatically, resizes, and re-encodes to the target format.
     *
     * @param imageData raw image file bytes (JPEG, PNG, or WebP)
     * @param targetWidth target width in pixels
     * @param targetHeight target height in pixels
     * @param outputFormat output format
     * @param quality output quality for lossy formats (1-100)
     * @return resized image data (caller must close)
     */
    public static ImageData resize(byte[] imageData, int targetWidth, int targetHeight,
                                   ImageFormat outputFormat, int quality) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment dataSeg = arena.allocate(imageData.length);
            dataSeg.copyFrom(MemorySegment.ofArray(imageData));

            PointerHolder outData = PointerHolder.allocate(arena);
            MemorySegment outLen = arena.allocate(JAVA_LONG);

            int err = EpubNativeHeaders.epub_native_image_resize(
                dataSeg, imageData.length,
                targetWidth, targetHeight,
                outputFormat.nativeValue(), quality,
                outData.segment(), outLen);
            checkError(err);

            return new ImageData(
                outData.segment().get(ADDRESS, 0),
                outLen.get(JAVA_LONG, 0));
        }
    }

    /**
     * Resize an image from an off-heap buffer.
     *
     * @param data MemorySegment containing image bytes
     * @param length byte length of the image data
     * @param targetWidth target width in pixels
     * @param targetHeight target height in pixels
     * @param outputFormat output format
     * @param quality output quality for lossy formats (1-100)
     * @return resized image data (caller must close)
     */
    public static ImageData resize(MemorySegment data, long length,
                                   int targetWidth, int targetHeight,
                                   ImageFormat outputFormat, int quality) {
        try (Arena arena = Arena.ofConfined()) {
            PointerHolder outData = PointerHolder.allocate(arena);
            MemorySegment outLen = arena.allocate(JAVA_LONG);

            int err = EpubNativeHeaders.epub_native_image_resize(
                data, length,
                targetWidth, targetHeight,
                outputFormat.nativeValue(), quality,
                outData.segment(), outLen);
            checkError(err);

            return new ImageData(
                outData.segment().get(ADDRESS, 0),
                outLen.get(JAVA_LONG, 0));
        }
    }
}
