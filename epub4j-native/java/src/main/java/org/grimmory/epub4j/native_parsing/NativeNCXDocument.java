/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Grimmory contributors
 * Copyright (C) 2025-2026 Booklore contributors
 */
package org.grimmory.epub4j.native_parsing;

import java.lang.foreign.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static java.lang.foreign.ValueLayout.*;
import static org.grimmory.epub4j.native_parsing.PanamaConstants.*;

/**
 * High-level Java wrapper for native NCX Document (EPUB 2 TOC) reading and writing.
 *
 * This class provides the same functionality as NCXDocument
 * but using the native C++ implementation via Panama FFM.
 *
 * <p>Example usage - Reading:</p>
 * <pre>{@code
 * try (NativeNCXDocument ncx = NativeNCXDocument.parse(xmlContent)) {
 *     List<TOCReference> tocRefs = ncx.getTocReferences();
 *     for (TOCReference ref : tocRefs) {
 *         System.out.println(ref.getTitle() + " -> " + href);
 *     }
 * }
 * }</pre>
 *
 * <p>Example usage - Writing:</p>
 * <pre>{@code
 * String xml = NativeNCXDocument.write(
 *     Arrays.asList("urn:uuid:123"),
 *     "Book Title",
 *     Arrays.asList("Author Name"),
 *     tocReferences
 * );
 * }</pre>
 *
 * <p>Memory management:</p>
 * <ul>
 *     <li>Implements AutoCloseable for proper resource cleanup</li>
 *     <li>Always use try-with-resources to ensure native memory is freed</li>
 * </ul>
 */
public class NativeNCXDocument implements AutoCloseable {

    private final Arena arena;
    private final MemorySegment docPointer;
    private boolean closed = false;

    // Cached TOC references
    private List<TOCReference> tocReferences;

    /**
         * TOC Reference representation
         */
        public record TOCReference(String title, String href, int playOrder, boolean hasChildren) {

        @Override
            public String toString() {
                return "TOCReference{" +
                        "title='" + title + '\'' +
                        ", href='" + href + '\'' +
                        ", playOrder=" + playOrder +
                        ", hasChildren=" + hasChildren +
                        '}';
            }
        }

    private NativeNCXDocument(Arena arena, MemorySegment docPointer) {
        this.arena = arena;
        this.docPointer = docPointer;
    }

    /**
     * Parse an NCX document from XML content
     *
     * @param xmlContent UTF-8 XML string containing the NCX document
     * @return NativeNCXDocument instance
     * @throws EpubNativeException if parsing fails
     */
    public static NativeNCXDocument parse(String xmlContent) {
        Arena arena = Arena.ofConfined();
        try {
            MemorySegment xmlSegment = toNativeString(xmlContent, arena);
            PointerHolder docPtrHolder = PointerHolder.allocate(arena);

            int errorCode = EpubNativeHeaders.epub_native_ncx_parse(
                xmlSegment,
                xmlContent.getBytes(StandardCharsets.UTF_8).length,
                    docPtrHolder.segment()
            );

            checkError(errorCode);

            return new NativeNCXDocument(arena, docPtrHolder.segment().get(ADDRESS, 0));

        } catch (Throwable e) {
            arena.close();
            throw new EpubNativeException("Failed to parse NCX document", e);
        }
    }

    /**
     * Get TOC references from the NCX document
     *
     * @return List of TOC references
     */
    public List<TOCReference> getTocReferences() {
        checkOpen();
        if (tocReferences != null) {
            return tocReferences;
        }

        MemorySegment refsArray = MemorySegment.NULL;
        long count = 0;
        try (Arena localArena = Arena.ofConfined()) {
            PointerHolder refsHolder = PointerHolder.allocate(localArena);
            MemorySegment countHolder = localArena.allocate(JAVA_LONG);

            int errorCode = EpubNativeHeaders.epub_native_ncx_get_toc_references(
                docPointer,
                    refsHolder.segment(),
                countHolder
            );

            checkError(errorCode);

            count = countHolder.get(JAVA_LONG, 0);
            refsArray = refsHolder.segment().get(ADDRESS, 0);
            if (count > 0 && refsArray != null && !refsArray.equals(MemorySegment.NULL)) {
                refsArray = refsArray.reinterpret(count * EpubNativeTOCReference.sizeof());
            }

            List<TOCReference> result = new ArrayList<>();
            for (long i = 0; i < count; i++) {
                MemorySegment refStruct = EpubNativeTOCReference.asSlice(refsArray, i);

                MemorySegment titlePtr = EpubNativeTOCReference.title(refStruct);
                MemorySegment hrefPtr = EpubNativeTOCReference.href(refStruct);
                int playOrder = EpubNativeTOCReference.play_order(refStruct);
                int hasChildren = EpubNativeTOCReference.has_children(refStruct);

                String title = toJavaString(titlePtr);
                String href = toJavaString(hrefPtr);

                result.add(new TOCReference(title, href, playOrder, hasChildren != 0));
            }

            tocReferences = List.copyOf(result);
            return tocReferences;

        } catch (Throwable e) {
            throw new EpubNativeException("Failed to get TOC references", e);
        } finally {
            if (count > 0 && refsArray != null && !refsArray.equals(MemorySegment.NULL)) {
                EpubNativeHeaders.epub_native_ncx_free_toc_references(refsArray, count);
            }
        }
    }

    /**
     * Write NCX document to XML string
     *
     * @param identifiers List of document identifiers (UUIDs)
     * @param title Document title
     * @param authors List of author names
     * @param tocRefs List of TOC references
     * @return XML string
     * @throws EpubNativeException if writing fails
     */
    public static String write(
        List<String> identifiers,
        String title,
        List<String> authors,
        List<TOCReference> tocRefs
    ) {
        try (Arena arena = Arena.ofConfined()) {
            // Create identifiers array
            MemorySegment identifiersArray = MemorySegment.NULL;
            if (identifiers != null && !identifiers.isEmpty()) {
                identifiersArray = arena.allocate(ADDRESS.byteSize() * identifiers.size());
                for (int i = 0; i < identifiers.size(); i++) {
                    MemorySegment idSegment = toNativeString(identifiers.get(i), arena);
                    identifiersArray.set(ADDRESS, i * ADDRESS.byteSize(), idSegment);
                }
            }

            // Create authors array
            MemorySegment authorsArray = MemorySegment.NULL;
            if (authors != null && !authors.isEmpty()) {
                authorsArray = arena.allocate(ADDRESS.byteSize() * authors.size());
                for (int i = 0; i < authors.size(); i++) {
                    MemorySegment authorSegment = toNativeString(authors.get(i), arena);
                    authorsArray.set(ADDRESS, i * ADDRESS.byteSize(), authorSegment);
                }
            }

            // Create TOC references array using jextract-generated struct
            MemorySegment tocArray = MemorySegment.NULL;
            if (tocRefs != null && !tocRefs.isEmpty()) {
                long structSize = EpubNativeTOCReference.sizeof();
                tocArray = arena.allocate(structSize * tocRefs.size());
                for (int i = 0; i < tocRefs.size(); i++) {
                    TOCReference ref = tocRefs.get(i);
                    MemorySegment refStruct = EpubNativeTOCReference.asSlice(tocArray, i);

                    MemorySegment titleSegment = toNativeString(ref.title, arena);
                    MemorySegment hrefSegment = toNativeString(ref.href, arena);

                    EpubNativeTOCReference.title(refStruct, titleSegment);
                    EpubNativeTOCReference.href(refStruct, hrefSegment);
                    EpubNativeTOCReference.play_order(refStruct, ref.playOrder);
                    EpubNativeTOCReference.has_children(refStruct, ref.hasChildren ? 1 : 0);
                }
            }

            MemorySegment titleSegment = toNativeString(title, arena);
            PointerHolder xmlHolder = PointerHolder.allocate(arena);

            int errorCode = EpubNativeHeaders.epub_native_ncx_write(
                identifiersArray,
                (identifiers != null ? identifiers.size() : 0),
                titleSegment,
                authorsArray,
                (authors != null ? authors.size() : 0),
                tocArray,
                (tocRefs != null ? tocRefs.size() : 0),
                    xmlHolder.segment()
            );

            checkError(errorCode);

            String xml = toJavaString(xmlHolder.segment().get(ADDRESS, 0));
            nativeStringFree(xmlHolder.segment().get(ADDRESS, 0));

            return xml;

        } catch (Throwable e) {
            throw new EpubNativeException("Failed to write NCX document", e);
        }
    }

    private void checkOpen() {
        if (closed) {
            throw new IllegalStateException("NCXDocument is closed");
        }
    }

    @Override
    public void close() {
        if (!closed) {
            if (docPointer != null && !docPointer.equals(MemorySegment.NULL)) {
                try {
                    EpubNativeHeaders.epub_native_ncx_free(docPointer);
                } catch (Throwable e) {
                    // Log but don't throw
                }
            }
            arena.close();
            closed = true;
        }
    }
}
