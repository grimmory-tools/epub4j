/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Grimmory contributors
 * Copyright (C) 2025-2026 Booklore contributors
 */
package org.grimmory.epub4j.native_parsing;

import java.lang.foreign.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static java.lang.foreign.ValueLayout.*;
import static org.grimmory.epub4j.native_parsing.PanamaConstants.*;

/**
 * High-level Java wrapper for native Package Document (OPF) reading.
 *
 * This class provides the same functionality as PackageDocumentReader
 * but using the native C++ implementation via Panama FFM.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * try (NativePackageReader reader = NativePackageReader.parse(xmlContent)) {
 *     String id = reader.getPackageId();
 *     String title = reader.getMetadata("title");
 *     List<String> spineItems = reader.getSpineItems();
 *     String coverHref = reader.getCoverHref();
 * }
 * }</pre>
 *
 * <p>Memory management:</p>
 * <ul>
 *     <li>Implements AutoCloseable for proper resource cleanup</li>
 *     <li>Always use try-with-resources to ensure native memory is freed</li>
 * </ul>
 */
public class NativePackageReader implements AutoCloseable {

    private final Arena arena;
    private final MemorySegment docPointer;
    private final String sourceXml;
    private boolean closed = false;

    // Cached data
    private String packageId;
    private Map<String, ManifestItem> manifest;
    private List<String> spineItems;
    private String coverHref;
    private Map<String, String> metadata;

    /**
         * Manifest item representation
         */
        public record ManifestItem(String id, String href, String mediaType) {
    }

    private NativePackageReader(Arena arena, MemorySegment docPointer, String sourceXml) {
        this.arena = arena;
        this.docPointer = docPointer;
        this.sourceXml = sourceXml;
    }

    /**
     * Parse an OPF package document from XML content
     *
     * @param xmlContent UTF-8 XML string containing the OPF document
     * @return NativePackageReader instance
     * @throws EpubNativeException if parsing fails
     */
    public static NativePackageReader parse(String xmlContent) {
        Arena arena = Arena.ofConfined();
        try {
            MemorySegment xmlSegment = toNativeString(xmlContent, arena);
            PointerHolder docPtrHolder = PointerHolder.allocate(arena);

            int errorCode = EpubNativeHeaders.epub_native_package_parse(
                xmlSegment,
                xmlContent.getBytes(StandardCharsets.UTF_8).length,
                    docPtrHolder.segment()
            );

            checkError(errorCode);

            return new NativePackageReader(arena, docPtrHolder.segment().get(ADDRESS, 0), xmlContent);

        } catch (Throwable e) {
            arena.close();
            throw new EpubNativeException("Failed to parse package document", e);
        }
    }

    /**
     * Get the package unique identifier
     *
     * @return Package ID, or null if not found
     */
    public String getPackageId() {
        checkOpen();
        if (packageId != null) {
            return packageId;
        }

        try {
            MemorySegment result = EpubNativeHeaders.epub_native_package_get_id(docPointer);
            packageId = toJavaString(result);
            nativeStringFree(result);
            return packageId;
        } catch (Throwable e) {
            throw new EpubNativeException("Failed to get package ID", e);
        }
    }

    /**
     * Get a manifest item by ID
     *
     * @param itemId Item ID to look up
     * @return ManifestItem, or null if not found
     */
    public ManifestItem getManifestItem(String itemId) {
        checkOpen();
        if (manifest != null && manifest.containsKey(itemId)) {
            return manifest.get(itemId);
        }

        try (Arena localArena = Arena.ofConfined()) {
            MemorySegment itemIdSegment = toNativeString(itemId, localArena);
            PointerHolder hrefHolder = PointerHolder.allocate(localArena);
            PointerHolder mediaTypeHolder = PointerHolder.allocate(localArena);

            int errorCode = EpubNativeHeaders.epub_native_package_get_manifest_item(
                docPointer,
                itemIdSegment,
                    hrefHolder.segment(),
                    mediaTypeHolder.segment()
            );

            if (errorCode == EPUB_NATIVE_ERROR_NOT_FOUND) {
                return null;
            }
            checkError(errorCode);

            String href = toJavaString(hrefHolder.segment().get(ADDRESS, 0));
            String mediaType = toJavaString(mediaTypeHolder.segment().get(ADDRESS, 0));

            // Free native strings
            nativeStringFree(hrefHolder.segment().get(ADDRESS, 0));
            nativeStringFree(mediaTypeHolder.segment().get(ADDRESS, 0));

            ManifestItem item = new ManifestItem(itemId, href, mediaType);

            // Cache if manifest map is initialized
            if (manifest != null) {
                manifest.put(itemId, item);
            }

            return item;
        } catch (Throwable e) {
            throw new EpubNativeException("Failed to get manifest item", e);
        }
    }

    /**
     * Get all manifest items in the package document.
     * <p>
     * This returns ALL manifest items, not just those referenced by the spine.
     * This includes fonts, images, CSS, and other resources that may not be
     * directly referenced in the reading order.
     *
     * @return Map of item ID to ManifestItem
     */
    public Map<String, ManifestItem> getAllManifestItems() {
        checkOpen();
        if (manifest != null) {
            return manifest;
        }

        manifest = new LinkedHashMap<>();

        try (Arena localArena = Arena.ofConfined()) {
            PointerHolder idsHolder = PointerHolder.allocate(localArena);
            MemorySegment countHolder = localArena.allocate(JAVA_LONG);

            int errorCode = EpubNativeHeaders.epub_native_package_get_all_manifest_items(
                docPointer,
                    idsHolder.segment(),
                countHolder
            );

            checkError(errorCode);

            long count = countHolder.get(JAVA_LONG, 0);
            MemorySegment idsArray = idsHolder.segment().get(ADDRESS, 0);
            if (count > 0 && idsArray != null && !idsArray.equals(MemorySegment.NULL)) {
                idsArray = idsArray.reinterpret(count * ADDRESS.byteSize());
            }

            for (long i = 0; i < count; i++) {
                MemorySegment idPtr = idsArray.get(ADDRESS, i * ADDRESS.byteSize());
                String itemId = toJavaString(idPtr);
                ManifestItem item = getManifestItem(itemId);
                if (item != null) {
                    manifest.put(itemId, item);
                }
            }

            // Free the array (elements are freed individually by getManifestItem)
            EpubNativeHeaders.epub_native_package_free_spine_items(idsArray, count);

        } catch (Throwable e) {
            throw new EpubNativeException("Failed to get all manifest items", e);
        }

        return manifest;
    }

    /**
     * Get spine item IDs in reading order
     *
     * @return List of spine item IDs
     */
    public List<String> getSpineItems() {
        checkOpen();
        if (spineItems != null) {
            return spineItems;
        }

        try (Arena localArena = Arena.ofConfined()) {
            PointerHolder idsHolder = PointerHolder.allocate(localArena);
            MemorySegment countHolder = localArena.allocate(JAVA_LONG);

            int errorCode = EpubNativeHeaders.epub_native_package_get_spine_items(
                docPointer,
                    idsHolder.segment(),
                countHolder
            );

            checkError(errorCode);

            long count = countHolder.get(JAVA_LONG, 0);
            MemorySegment idsArray = idsHolder.segment().get(ADDRESS, 0);
            if (count > 0 && idsArray != null && !idsArray.equals(MemorySegment.NULL)) {
                idsArray = idsArray.reinterpret(count * ADDRESS.byteSize());
            }

            List<String> result = new ArrayList<>();
            for (long i = 0; i < count; i++) {
                MemorySegment idPtr = idsArray.get(ADDRESS, i * ADDRESS.byteSize());
                String id = toJavaString(idPtr);
                result.add(id);
            }

            // Free both the array and each element with the dedicated native API.
            EpubNativeHeaders.epub_native_package_free_spine_items(idsArray, count);

            spineItems = List.copyOf(result);
            return spineItems;

        } catch (Throwable e) {
            throw new EpubNativeException("Failed to get spine items", e);
        }
    }

    /**
     * Get the cover image href
     *
     * @return Cover href, or null if no cover found
     */
    public String getCoverHref() {
        checkOpen();
        if (coverHref != null) {
            return coverHref;
        }

        try (Arena localArena = Arena.ofConfined()) {
            PointerHolder coverHolder = PointerHolder.allocate(localArena);

            int errorCode = EpubNativeHeaders.epub_native_package_get_cover(
                docPointer,
                    coverHolder.segment()
            );

            if (errorCode == EPUB_NATIVE_ERROR_NOT_FOUND) {
                coverHref = null;
                return null;
            }
            checkError(errorCode);

            coverHref = toJavaString(coverHolder.segment().get(ADDRESS, 0));
            nativeStringFree(coverHolder.segment().get(ADDRESS, 0));
            return coverHref;

        } catch (Throwable e) {
            throw new EpubNativeException("Failed to get cover href", e);
        }
    }

    /**
     * Get metadata value by name
     *
     * @param name Metadata name (e.g., "title", "creator", "language")
     * @return Metadata value, or null if not found
     */
    public String getMetadata(String name) {
        checkOpen();
        if (metadata != null && metadata.containsKey(name)) {
            return metadata.get(name);
        }

        try (Arena localArena = Arena.ofConfined()) {
            MemorySegment nameSegment = toNativeString(name, localArena);
            PointerHolder valueHolder = PointerHolder.allocate(localArena);

            int errorCode = EpubNativeHeaders.epub_native_package_get_metadata(
                docPointer,
                nameSegment,
                    valueHolder.segment()
            );

            if (errorCode == EPUB_NATIVE_ERROR_NOT_FOUND) {
                return null;
            }
            checkError(errorCode);

            String value = toJavaString(valueHolder.segment().get(ADDRESS, 0));
            nativeStringFree(valueHolder.segment().get(ADDRESS, 0));

            // Cache if metadata map is initialized
            if (metadata != null) {
                metadata.put(name, value);
            }

            return value;

        } catch (Throwable e) {
            throw new EpubNativeException("Failed to get metadata", e);
        }
    }

    /**
     * Get all metadata
     *
     * @return Map of metadata name to value
     */
    public Map<String, String> getAllMetadata() {
        checkOpen();
        if (metadata != null) {
            return metadata;
        }

        metadata = new LinkedHashMap<>();

        Map<String, String> parsedMetadata = parseMetadataFromSourceXml();
        for (Map.Entry<String, String> entry : parsedMetadata.entrySet()) {
            String key = entry.getKey();
            String value = getMetadata(key);
            if (value != null) {
                metadata.put(key, value);
            } else if (entry.getValue() != null) {
                metadata.put(key, entry.getValue());
            }
        }

        return metadata;
    }

    private Map<String, String> parseMetadataFromSourceXml() {
        Map<String, String> allMetadata = new LinkedHashMap<>();
        if (sourceXml == null || sourceXml.isBlank()) {
            return allMetadata;
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder();
            var doc = builder.parse(new ByteArrayInputStream(sourceXml.getBytes(StandardCharsets.UTF_8)));

            NodeList metadataNodes = doc.getElementsByTagNameNS("*", "metadata");
            if (metadataNodes.getLength() == 0) {
                return allMetadata;
            }

            Node metadataNode = metadataNodes.item(0);
            NodeList children = metadataNode.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }

                String key = child.getLocalName() != null ? child.getLocalName() : child.getNodeName();
                if ("meta".equals(key) && child instanceof Element metaElem) {
                    String property = metaElem.getAttribute("property");
                    if (!property.isBlank()) {
                        key = property;
                    }
                }

                String value = child.getTextContent();
                if (value != null) {
                    value = value.trim();
                }
                if (value == null || value.isEmpty()) {
                    continue;
                }

                String existing = allMetadata.get(key);
                if (existing == null || existing.isEmpty()) {
                    allMetadata.put(key, value);
                } else {
                    allMetadata.put(key, existing + "\n" + value);
                }
            }
        } catch (Exception ignored) {
            // Keep wrapper robust; callers can still use getMetadata(key) for known fields.
        }

        return allMetadata;
    }

    private void checkOpen() {
        if (closed) {
            throw new IllegalStateException("PackageReader is closed");
        }
    }

    @Override
    public void close() {
        if (!closed) {
            if (docPointer != null && !docPointer.equals(MemorySegment.NULL)) {
                try {
                    EpubNativeHeaders.epub_native_package_free(docPointer);
                } catch (Throwable e) {
                    // Log but don't throw
                }
            }
            arena.close();
            closed = true;
        }
    }
}
