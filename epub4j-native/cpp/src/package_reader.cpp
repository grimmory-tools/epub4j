/**
 * package_reader.cpp - Package Document (OPF) Reader Implementation
 *
 * Uses pugixml for parsing EPUB package documents
 */

#include "epub_native.h"
#include "epub_native_internal.h"
#include "pugixml.hpp"
#include <vector>
#include <string>
#include <unordered_map>
#include <cctype>
#include <cstring>
#include <cstdlib>
#include <memory>

// ============================================================================
// Internal Structures
// ============================================================================

struct EpubNativePackageDocument {
    struct ManifestItem {
        std::string href;
        std::string media_type;
        std::string properties;
    };

    pugi::xml_document doc;
    pugi::xml_node package;
    std::string id;
    
    // Cache for manifest items: id -> (href, media_type)
    std::unordered_map<std::string, ManifestItem> manifest;
    
    // Spine item IDs in order
    std::vector<std::string> spine_items;
    
    // Metadata
    std::unordered_map<std::string, std::string> metadata;
    
    // Cover href
    std::string cover_href;
};

// ============================================================================
// Namespace Constants (matching Java implementation)
// ============================================================================

static const char* NAMESPACE_OPF = "http://www.idpf.org/2007/opf";
static const char* NAMESPACE_DC = "http://purl.org/dc/elements/1.1/";

// ============================================================================
// Helper Functions
// ============================================================================

static const char* local_name(const char* qname) {
    if (!qname) {
        return "";
    }
    const char* colon = strrchr(qname, ':');
    return colon ? colon + 1 : qname;
}

static pugi::xml_node find_child_by_name(pugi::xml_node parent, const char* name) {
    for (pugi::xml_node child = parent.first_child(); child; child = child.next_sibling()) {
        const char* node_name = local_name(child.name());
        if (strcmp(node_name, name) == 0) {
            return child;
        }
    }
    return pugi::xml_node();
}

static pugi::xml_node next_sibling_by_name(pugi::xml_node node, const char* name) {
    for (pugi::xml_node sibling = node.next_sibling(); sibling; sibling = sibling.next_sibling()) {
        if (strcmp(local_name(sibling.name()), name) == 0) {
            return sibling;
        }
    }
    return pugi::xml_node();
}

static bool has_space_separated_token(const std::string& value, const char* token) {
    if (!token || *token == '\0' || value.empty()) {
        return false;
    }

    size_t i = 0;
    while (i < value.size()) {
        while (i < value.size() && std::isspace(static_cast<unsigned char>(value[i]))) {
            i++;
        }
        size_t start = i;
        while (i < value.size() && !std::isspace(static_cast<unsigned char>(value[i]))) {
            i++;
        }
        if (i > start) {
            if (value.compare(start, i - start, token) == 0) {
                return true;
            }
        }
    }
    return false;
}

static const char* get_attribute(pugi::xml_node node, const char* attr_name) {
    pugi::xml_attribute attr = node.attribute(attr_name);
    return attr ? attr.as_string() : nullptr;
}

static char* strdup_or_null(const char* str) {
    return duplicate_cstring(str);
}

// ============================================================================
// Public API Implementation
// ============================================================================

EPUB_NATIVE_API EpubNativeError epub_native_package_parse(
    const char* xml_content,
    size_t xml_length,
    EpubNativePackageDocument** out_doc
) {
    if (!xml_content || !out_doc) {
        set_error("Invalid argument: null pointer");
        return EPUB_NATIVE_ERROR_INVALID_ARG;
    }
    
    try {
        std::unique_ptr<EpubNativePackageDocument> doc(new EpubNativePackageDocument());

        pugi::xml_parse_result result = doc->doc.load_buffer(
            xml_content,
            xml_length,
            pugi::parse_default
        );

        if (!result) {
            set_error(result.description());
            return EPUB_NATIVE_ERROR_PARSE;
        }

        // Find package element
        doc->package = doc->doc.child("package");
        if (!doc->package) {
            // Try with namespace
            for (pugi::xml_node node = doc->doc.first_child(); node; node = node.next_sibling()) {
                const char* name = node.name();
                const char* colon = strrchr(name, ':');
                if (colon) {
                    name = colon + 1;
                }
                if (strcmp(name, "package") == 0) {
                    doc->package = node;
                    break;
                }
            }
        }
        
        if (!doc->package) {
            set_error("No package element found");
            return EPUB_NATIVE_ERROR_PARSE;
        }
        
        // Get unique identifier
        const char* unique_id = get_attribute(doc->package, "unique-identifier");
        if (unique_id) {
            pugi::xml_node metadata = find_child_by_name(doc->package, "metadata");
            if (metadata) {
                pugi::xml_node dc_id = find_child_by_name(metadata, "identifier");
                while (dc_id) {
                    const char* id_ref = get_attribute(dc_id, "id");
                    if (id_ref && strcmp(id_ref, unique_id) == 0) {
                        doc->id = dc_id.child_value();
                        break;
                    }
                    dc_id = next_sibling_by_name(dc_id, "identifier");
                }
            }
        }
        
        // Parse metadata
        pugi::xml_node metadata = find_child_by_name(doc->package, "metadata");
        if (metadata) {
            for (pugi::xml_node child = metadata.first_child(); child; child = child.next_sibling()) {
                const char* name = child.name();
                const char* colon = strrchr(name, ':');
                if (colon) {
                    name = colon + 1;
                }
                std::string value = child.child_value();
                auto it = doc->metadata.find(name);
                if (it == doc->metadata.end() || it->second.empty()) {
                    doc->metadata[name] = value;
                } else if (!value.empty()) {
                    it->second.append("\n").append(value);
                }
            }
        }
        
        // Parse manifest
        pugi::xml_node manifest = find_child_by_name(doc->package, "manifest");
        if (manifest) {
            for (pugi::xml_node item = find_child_by_name(manifest, "item"); item; item = next_sibling_by_name(item, "item")) {
                const char* id = get_attribute(item, "id");
                const char* href = get_attribute(item, "href");
                const char* media_type = get_attribute(item, "media-type");
                const char* properties = get_attribute(item, "properties");
                
                if (id && href) {
                    doc->manifest[id] = {
                        href ? href : "",
                        media_type ? media_type : "",
                        properties ? properties : ""
                    };
                }
            }
        }
        
        // Parse spine
        pugi::xml_node spine = find_child_by_name(doc->package, "spine");
        if (spine) {
            for (pugi::xml_node itemref = find_child_by_name(spine, "itemref"); itemref; itemref = next_sibling_by_name(itemref, "itemref")) {
                const char* idref = get_attribute(itemref, "idref");
                if (idref) {
                    doc->spine_items.push_back(idref);
                }
            }
        }
        
        // Find cover
        // EPUB 2: meta name="cover" content="cover-image-id"
        // EPUB 3: item with properties="cover-image"
        if (metadata) {
            for (pugi::xml_node meta = find_child_by_name(metadata, "meta"); meta; meta = next_sibling_by_name(meta, "meta")) {
                const char* name = get_attribute(meta, "name");
                if (name && strcmp(name, "cover") == 0) {
                    const char* content = get_attribute(meta, "content");
                    if (content) {
                        auto it = doc->manifest.find(content);
                        if (it != doc->manifest.end()) {
                            doc->cover_href = it->second.href;
                        }
                    }
                    break;
                }
            }
        }
        
        if (!doc->cover_href.empty()) {
            // Already found from EPUB 2 meta
        } else {
            // Try EPUB 3 cover-image property
            for (const auto& kv : doc->manifest) {
                if (has_space_separated_token(kv.second.properties, "cover-image")) {
                    doc->cover_href = kv.second.href;
                    break;
                }
            }
        }
        
        *out_doc = doc.release();
        return EPUB_NATIVE_SUCCESS;
        
    } catch (const std::bad_alloc&) {
        set_error("Memory allocation failed");
        return EPUB_NATIVE_ERROR_MEMORY;
    } catch (...) {
        set_error("Unknown error during package parsing");
        return EPUB_NATIVE_ERROR_PARSE;
    }
}

EPUB_NATIVE_API void epub_native_package_free(EpubNativePackageDocument* doc) {
    delete doc;
}

EPUB_NATIVE_API const char* epub_native_package_get_id(
    EpubNativePackageDocument* doc
) {
    if (!doc || doc->id.empty()) {
        return nullptr;
    }
    char* id = strdup_or_null(doc->id.c_str());
    if (!id) {
        set_error("Memory allocation failed");
    }
    return id;
}

EPUB_NATIVE_API EpubNativeError epub_native_package_get_manifest_item(
    EpubNativePackageDocument* doc,
    const char* item_id,
    const char** out_href,
    const char** out_media_type
) {
    if (!doc || !item_id || !out_href || !out_media_type) {
        set_error("Invalid argument: null pointer");
        return EPUB_NATIVE_ERROR_INVALID_ARG;
    }
    
    auto it = doc->manifest.find(item_id);
    if (it == doc->manifest.end()) {
        set_error("Manifest item not found");
        return EPUB_NATIVE_ERROR_NOT_FOUND;
    }
    
    *out_href = strdup_or_null(it->second.href.c_str());
    *out_media_type = strdup_or_null(it->second.media_type.c_str());
    if (!*out_href || !*out_media_type) {
        free((void*)*out_href);
        free((void*)*out_media_type);
        *out_href = nullptr;
        *out_media_type = nullptr;
        set_error("Memory allocation failed");
        return EPUB_NATIVE_ERROR_MEMORY;
    }
    
    return EPUB_NATIVE_SUCCESS;
}

EPUB_NATIVE_API EpubNativeError epub_native_package_get_spine_items(
    EpubNativePackageDocument* doc,
    const char*** out_ids,
    size_t* out_count
) {
    if (!doc || !out_ids || !out_count) {
        set_error("Invalid argument: null pointer");
        return EPUB_NATIVE_ERROR_INVALID_ARG;
    }

    size_t count = doc->spine_items.size();
    if (count == 0) {
        *out_ids = nullptr;
        *out_count = 0;
        return EPUB_NATIVE_SUCCESS;
    }

    try {
        if (count > SIZE_MAX / sizeof(const char*)) {
            set_error("Spine item count too large");
            return EPUB_NATIVE_ERROR_MEMORY;
        }
        const char** ids = (const char**)malloc(count * sizeof(const char*));
        if (!ids) {
            set_error("Memory allocation failed");
            return EPUB_NATIVE_ERROR_MEMORY;
        }

        for (size_t i = 0; i < count; i++) {
            ids[i] = strdup_or_null(doc->spine_items[i].c_str());
            if (!ids[i]) {
                // Cleanup on failure
                for (size_t j = 0; j < i; j++) {
                    free((void*)ids[j]);
                }
                free((void*)ids);
                set_error("Memory allocation failed");
                return EPUB_NATIVE_ERROR_MEMORY;
            }
        }

        *out_ids = ids;
        *out_count = count;
        return EPUB_NATIVE_SUCCESS;

    } catch (...) {
        set_error("Memory allocation failed");
        return EPUB_NATIVE_ERROR_MEMORY;
    }
}

EPUB_NATIVE_API EpubNativeError epub_native_package_get_all_manifest_items(
    EpubNativePackageDocument* doc,
    const char*** out_ids,
    size_t* out_count
) {
    if (!doc || !out_ids || !out_count) {
        set_error("Invalid argument: null pointer");
        return EPUB_NATIVE_ERROR_INVALID_ARG;
    }

    size_t count = doc->manifest.size();
    if (count == 0) {
        *out_ids = nullptr;
        *out_count = 0;
        return EPUB_NATIVE_SUCCESS;
    }

    try {
        if (count > SIZE_MAX / sizeof(const char*)) {
            set_error("Manifest item count too large");
            return EPUB_NATIVE_ERROR_MEMORY;
        }
        const char** ids = (const char**)malloc(count * sizeof(const char*));
        if (!ids) {
            set_error("Memory allocation failed");
            return EPUB_NATIVE_ERROR_MEMORY;
        }

        // Copy all manifest IDs to array
        size_t i = 0;
        for (const auto& kv : doc->manifest) {
            ids[i] = strdup_or_null(kv.first.c_str());
            if (!ids[i]) {
                // Cleanup on failure
                for (size_t j = 0; j < i; j++) {
                    free((void*)ids[j]);
                }
                free((void*)ids);
                set_error("Memory allocation failed");
                return EPUB_NATIVE_ERROR_MEMORY;
            }
            i++;
        }

        *out_ids = ids;
        *out_count = count;
        return EPUB_NATIVE_SUCCESS;

    } catch (...) {
        set_error("Memory allocation failed");
        return EPUB_NATIVE_ERROR_MEMORY;
    }
}

EPUB_NATIVE_API void epub_native_package_free_spine_items(
    const char** ids,
    size_t count
) {
    if (ids) {
        for (size_t i = 0; i < count; i++) {
            free((void*)ids[i]);
        }
        free((void*)ids);
    }
}

EPUB_NATIVE_API EpubNativeError epub_native_package_get_cover(
    EpubNativePackageDocument* doc,
    const char** out_cover_href
) {
    if (!doc || !out_cover_href) {
        set_error("Invalid argument: null pointer");
        return EPUB_NATIVE_ERROR_INVALID_ARG;
    }
    
    if (doc->cover_href.empty()) {
        *out_cover_href = nullptr;
        return EPUB_NATIVE_ERROR_NOT_FOUND;
    }
    
    *out_cover_href = strdup_or_null(doc->cover_href.c_str());
    if (!*out_cover_href) {
        set_error("Memory allocation failed");
        return EPUB_NATIVE_ERROR_MEMORY;
    }
    return EPUB_NATIVE_SUCCESS;
}

EPUB_NATIVE_API EpubNativeError epub_native_package_get_metadata(
    EpubNativePackageDocument* doc,
    const char* metadata_name,
    const char** out_value
) {
    if (!doc || !metadata_name || !out_value) {
        set_error("Invalid argument: null pointer");
        return EPUB_NATIVE_ERROR_INVALID_ARG;
    }
    
    auto it = doc->metadata.find(metadata_name);
    if (it == doc->metadata.end()) {
        *out_value = nullptr;
        return EPUB_NATIVE_ERROR_NOT_FOUND;
    }
    
    *out_value = strdup_or_null(it->second.c_str());
    if (!*out_value) {
        set_error("Memory allocation failed");
        return EPUB_NATIVE_ERROR_MEMORY;
    }
    return EPUB_NATIVE_SUCCESS;
}
