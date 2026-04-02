/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Grimmory contributors
 * Copyright (C) 2025-2026 Booklore contributors
 */
package org.grimmory.epub4j.native_parsing;

import java.lang.foreign.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static java.lang.foreign.ValueLayout.*;
import static org.grimmory.epub4j.native_parsing.PanamaConstants.*;

/**
 * High-level Java wrapper for native HTML Cleaner (HTML to XHTML conversion).
 *
 * This class provides the same functionality as HtmlCleanerBookProcessor
 * but using the native C++ implementation (Gumbo HTML5 parser) via Panama FFM.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * try (NativeHtmlCleaner cleaner = new NativeHtmlCleaner()) {
 *     String html = "<html><head><title>Test</title></head><body><p>Hello World!</p></body></html>";
 *     String xhtml = cleaner.clean(html, "UTF-8");
 *     System.out.println(xhtml);
 * }
 * }</pre>
 *
 * <p>Features:</p>
 * <ul>
 *     <li>Parses malformed HTML using Gumbo HTML5 parser</li>
 *     <li>Produces well-formed XHTML</li>
 *     <li>Removes event handlers (onclick, onload, etc.) for security</li>
 *     <li>Properly escapes special characters</li>
 *     <li>Adds XHTML namespace to root element</li>
 *     <li>Handles void elements correctly (br, img, etc.)</li>
 * </ul>
 *
 * <p>Memory management:</p>
 * <ul>
 *     <li>Implements AutoCloseable for proper resource cleanup</li>
 *     <li>Always use try-with-resources to ensure native memory is freed</li>
 * </ul>
 */
public class NativeHtmlCleaner implements AutoCloseable {

    private final Arena arena;
    private final MemorySegment cleanerPointer;
    private boolean closed = false;

    /**
     * Create a new HTML cleaner instance
     *
     * @throws EpubNativeException if creation fails
     */
    public NativeHtmlCleaner() {
        this.arena = Arena.ofConfined();
        try {
            PointerHolder cleanerHolder = PointerHolder.allocate(arena);

            int errorCode = EpubNativeHeaders.epub_native_html_cleaner_create(
                    cleanerHolder.segment()
            );

            checkError(errorCode);

            this.cleanerPointer = cleanerHolder.segment().get(ADDRESS, 0);

        } catch (Throwable e) {
            arena.close();
            throw new EpubNativeException("Failed to create HTML cleaner", e);
        }
    }

    /**
     * Clean HTML content and convert to XHTML
     *
     * @param htmlContent Input HTML string (can be malformed)
     * @param outputEncoding Output encoding (e.g., "UTF-8")
     * @return Well-formed XHTML string
     * @throws EpubNativeException if cleaning fails
     */
    public String clean(String htmlContent, String outputEncoding) {
        checkOpen();

        try (Arena localArena = Arena.ofConfined()) {
            MemorySegment htmlSegment = toNativeString(htmlContent, localArena);
            MemorySegment encodingSegment = toNativeString(
                outputEncoding != null ? outputEncoding : "UTF-8",
                localArena
            );
            PointerHolder xhtmlHolder = PointerHolder.allocate(localArena);
            MemorySegment lengthHolder = localArena.allocate(JAVA_LONG);

            int errorCode = EpubNativeHeaders.epub_native_html_clean(
                cleanerPointer,
                htmlSegment,
                htmlContent.getBytes(StandardCharsets.UTF_8).length,
                encodingSegment,
                    xhtmlHolder.segment(),
                lengthHolder
            );

            checkError(errorCode);

            MemorySegment xhtmlPtr = xhtmlHolder.segment().get(ADDRESS, 0);
            try {
                return toJavaString(xhtmlPtr);
            } finally {
                if (xhtmlPtr != null && !xhtmlPtr.equals(MemorySegment.NULL)) {
                    nativeStringFree(xhtmlPtr);
                }
            }

        } catch (Throwable e) {
            throw new EpubNativeException("Failed to clean HTML", e);
        }
    }

    /**
     * Clean HTML content with default UTF-8 encoding
     *
     * @param htmlContent Input HTML string
     * @return Well-formed XHTML string
     */
    public String clean(String htmlContent) {
        return clean(htmlContent, "UTF-8");
    }

    /**
     * Clean HTML content from bytes
     *
     * @param htmlBytes Input HTML bytes
     * @param inputEncoding Input encoding
     * @param outputEncoding Output encoding
     * @return Well-formed XHTML string
     */
    public String clean(byte[] htmlBytes, String inputEncoding, String outputEncoding) {
        Charset charset = StandardCharsets.UTF_8;
        if (inputEncoding != null && !inputEncoding.isBlank()) {
            charset = Charset.forName(inputEncoding);
        }
        String html = new String(htmlBytes, charset);
        return clean(html, outputEncoding);
    }

    /**
     * Check if the cleaner is still open
     */
    private void checkOpen() {
        if (closed) {
            throw new IllegalStateException("HtmlCleaner is closed");
        }
    }

    @Override
    public void close() {
        if (!closed) {
            if (cleanerPointer != null && !cleanerPointer.equals(MemorySegment.NULL)) {
                try {
                    EpubNativeHeaders.epub_native_html_cleaner_free(cleanerPointer);
                } catch (Throwable e) {
                    // Log but don't throw
                }
            }
            arena.close();
            closed = true;
        }
    }
}
