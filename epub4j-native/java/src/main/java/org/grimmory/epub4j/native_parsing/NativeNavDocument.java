/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Grimmory contributors
 * Copyright (C) 2025-2026 Booklore contributors
 */
package org.grimmory.epub4j.native_parsing;

import java.lang.foreign.*;
import java.util.*;

import static java.lang.foreign.ValueLayout.*;
import static org.grimmory.epub4j.native_parsing.PanamaConstants.*;

/**
 * High-level Java wrapper for native Navigation Document (EPUB 3 TOC) generation.
 *
 * This class provides the same functionality as NavDocument
 * but using the native C++ implementation via Panama FFM.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * List<NativeNCXDocument.TOCReference> tocRefs = Arrays.asList(
 *     new NativeNCXDocument.TOCReference("Chapter 1", "chapter1.xhtml", 1, false),
 *     new NativeNCXDocument.TOCReference("Chapter 2", "chapter2.xhtml", 2, false)
 * );
 *
 * String navXhtml = NativeNavDocument.create("Book Title", tocRefs);
 * }</pre>
 *
 * <p>The generated XHTML follows EPUB 3 navigation document specifications:</p>
 * <ul>
 *     <li>DOCTYPE html</li>
 *     <li>XHTML namespace (http://www.w3.org/1999/xhtml)</li>
 *     <li>EPUB namespace (http://www.idpf.org/2007/ops)</li>
 *     <li>nav element with epub:type="toc"</li>
 *     <li>Hierarchical ordered lists for TOC structure</li>
 * </ul>
 */
public class NativeNavDocument {

    // Prevent instantiation - all methods are static
    private NativeNavDocument() {
    }

    /**
     * Create an EPUB 3 navigation document (XHTML)
     *
     * @param title Document title
     * @param tocRefs List of TOC references
     * @return XHTML string containing the navigation document
     * @throws EpubNativeException if creation fails
     */
    public static String create(String title, List<NativeNCXDocument.TOCReference> tocRefs) {
        try (Arena arena = Arena.ofConfined()) {
            // Create TOC references array using jextract-generated struct
            MemorySegment tocArray = MemorySegment.NULL;
            if (tocRefs != null && !tocRefs.isEmpty()) {
                long structSize = EpubNativeTOCReference.sizeof();
                tocArray = arena.allocate(structSize * tocRefs.size());
                for (int i = 0; i < tocRefs.size(); i++) {
                    NativeNCXDocument.TOCReference ref = tocRefs.get(i);
                    MemorySegment refStruct = EpubNativeTOCReference.asSlice(tocArray, i);

                    MemorySegment titleSegment = toNativeString(ref.title(), arena);
                    MemorySegment hrefSegment = toNativeString(ref.href(), arena);

                    EpubNativeTOCReference.title(refStruct, titleSegment);
                    EpubNativeTOCReference.href(refStruct, hrefSegment);
                    EpubNativeTOCReference.play_order(refStruct, ref.playOrder());
                    EpubNativeTOCReference.has_children(refStruct, ref.hasChildren() ? 1 : 0);
                }
            }

            MemorySegment titleSegment = toNativeString(title, arena);
            PointerHolder xhtmlHolder = PointerHolder.allocate(arena);

            int errorCode = EpubNativeHeaders.epub_native_nav_create(
                titleSegment,
                tocArray,
                (tocRefs != null ? tocRefs.size() : 0),
                    xhtmlHolder.segment()
            );

            checkError(errorCode);

            String xhtml = toJavaString(xhtmlHolder.segment().get(ADDRESS, 0));
            nativeStringFree(xhtmlHolder.segment().get(ADDRESS, 0));

            return xhtml;

        } catch (Throwable e) {
            throw new EpubNativeException("Failed to create navigation document", e);
        }
    }

    /**
     * Create a minimal EPUB 3 navigation document with just a title
     *
     * @param title Document title
     * @return XHTML string
     */
    public static String createMinimal(String title) {
        return create(title, Collections.emptyList());
    }

    /**
     * Create a navigation document from a single TOC entry
     *
     * @param title Document title
     * @param entryTitle Entry title
     * @param entryHref Entry href
     * @return XHTML string
     */
    public static String createSingle(String title, String entryTitle, String entryHref) {
        NativeNCXDocument.TOCReference ref = new NativeNCXDocument.TOCReference(
            entryTitle, entryHref, 1, false
        );
        return create(title, Collections.singletonList(ref));
    }
}
