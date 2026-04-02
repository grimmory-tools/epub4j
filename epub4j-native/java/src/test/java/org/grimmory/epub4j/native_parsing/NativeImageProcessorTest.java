package org.grimmory.epub4j.native_parsing;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.grimmory.epub4j.native_parsing.NativeImageProcessor.ImageData;
import org.grimmory.epub4j.native_parsing.NativeImageProcessor.ImageDimensions;
import org.grimmory.epub4j.native_parsing.NativeImageProcessor.ImageFormat;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

class NativeImageProcessorTest {

    @BeforeAll
    static void requireNative() {
        assumeTrue(NativeImageProcessor.isAvailable(), "native image processing not available");
    }

    // Produce a small JPEG in-memory via ImageIO
    private static byte[] createTestJpeg(int width, int height) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                img.setRGB(x, y, (x * 3) << 16 | (y * 5) << 8 | 128);
            }
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpeg", baos);
        return baos.toByteArray();
    }

    // Produce a small PNG in-memory via ImageIO
    private static byte[] createTestPng(int width, int height) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                img.setRGB(x, y, 0xFF000000 | (x * 7) << 16 | (y * 3) << 8 | 64);
            }
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }

    @Test
    void hasJpegSupport() {
        assertTrue(NativeImageProcessor.hasJpeg());
    }

    @Test
    void hasPngSupport() {
        assertTrue(NativeImageProcessor.hasPng());
    }

    @Test
    void hasWebpSupport() {
        assertTrue(NativeImageProcessor.hasWebp());
    }

    @Test
    void jpegDimensions() throws IOException {
        byte[] jpeg = createTestJpeg(320, 240);
        ImageDimensions dims = NativeImageProcessor.getDimensions(jpeg);
        assertEquals(320, dims.width());
        assertEquals(240, dims.height());
        assertEquals(ImageFormat.JPEG, dims.format());
    }

    @Test
    void pngDimensions() throws IOException {
        byte[] png = createTestPng(64, 48);
        ImageDimensions dims = NativeImageProcessor.getDimensions(png);
        assertEquals(64, dims.width());
        assertEquals(48, dims.height());
        assertEquals(ImageFormat.PNG, dims.format());
    }

    @Test
    void jpegOptimize() throws IOException {
        byte[] jpeg = createTestJpeg(200, 150);
        try (ImageData optimized = NativeImageProcessor.optimizeJpeg(jpeg)) {
            assertNotNull(optimized);
            assertTrue(optimized.length() > 0);
            byte[] result = optimized.toByteArray();
            // Optimized output should still be valid JPEG (FFD8 header)
            assertEquals((byte) 0xFF, result[0]);
            assertEquals((byte) 0xD8, result[1]);
        }
    }

    @Test
    void jpegCompress() throws IOException {
        byte[] jpeg = createTestJpeg(200, 150);
        try (ImageData compressed = NativeImageProcessor.compressJpeg(jpeg, 75, true)) {
            assertNotNull(compressed);
            assertTrue(compressed.length() > 0);
            byte[] result = compressed.toByteArray();
            assertEquals((byte) 0xFF, result[0]);
            assertEquals((byte) 0xD8, result[1]);
        }
    }

    @Test
    void pngOptimize() throws IOException {
        byte[] png = createTestPng(100, 80);
        try (ImageData optimized = NativeImageProcessor.optimizePng(png, true)) {
            assertNotNull(optimized);
            assertTrue(optimized.length() > 0);
            byte[] result = optimized.toByteArray();
            // Should still be valid PNG (89 50 4E 47 header)
            assertEquals((byte) 0x89, result[0]);
            assertEquals((byte) 0x50, result[1]);
        }
    }

    @Test
    void resizeJpegToJpeg() throws IOException {
        byte[] jpeg = createTestJpeg(400, 300);
        try (ImageData resized = NativeImageProcessor.resize(jpeg, 200, 150, ImageFormat.JPEG, 85)) {
            assertNotNull(resized);
            assertTrue(resized.length() > 0);
            // Verify dimensions of output
            ImageDimensions dims = NativeImageProcessor.getDimensions(resized.toByteArray());
            assertEquals(200, dims.width());
            assertEquals(150, dims.height());
        }
    }

    @Test
    void resizePngToPng() throws IOException {
        byte[] png = createTestPng(200, 160);
        try (ImageData resized = NativeImageProcessor.resize(png, 50, 40, ImageFormat.PNG, 0)) {
            assertNotNull(resized);
            assertTrue(resized.length() > 0);
            ImageDimensions dims = NativeImageProcessor.getDimensions(resized.toByteArray());
            assertEquals(50, dims.width());
            assertEquals(40, dims.height());
        }
    }

    @Test
    void resizeJpegToWebp() throws IOException {
        assumeTrue(NativeImageProcessor.hasWebp(), "WebP support required");
        byte[] jpeg = createTestJpeg(300, 200);
        try (ImageData resized = NativeImageProcessor.resize(jpeg, 150, 100, ImageFormat.WEBP, 80)) {
            assertNotNull(resized);
            assertTrue(resized.length() > 0);
            // WebP files start with RIFF....WEBP
            byte[] result = resized.toByteArray();
            assertEquals((byte) 'R', result[0]);
            assertEquals((byte) 'I', result[1]);
            assertEquals((byte) 'F', result[2]);
            assertEquals((byte) 'F', result[3]);
        }
    }

    @Test
    void imageDataCloseFreesMem() throws IOException {
        byte[] jpeg = createTestJpeg(50, 50);
        ImageData data = NativeImageProcessor.optimizeJpeg(jpeg);
        data.close();
        // Double-close should not throw
        assertDoesNotThrow(data::close);
    }
}
