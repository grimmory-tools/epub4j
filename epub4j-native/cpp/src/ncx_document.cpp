/**
 * ncx_document.cpp - NCX Document (EPUB 2 TOC) Implementation
 *
 * Uses pugixml for parsing and generating NCX documents
 */

#include "epub_native.h"
#include "epub_native_internal.h"
#include "pugixml.hpp"
#include <vector>
#include <string>
#include <sstream>
#include <unordered_map>
#include <cstring>
#include <cstdlib>
#include <climits>

// ============================================================================
// Internal Structures
// ============================================================================

struct EpubNativeNCXDocument {
    pugi::xml_document doc;
    pugi::xml_node ncx;
    pugi::xml_node nav_map;
    
    // Parsed TOC references
    std::vector<EpubNativeTOCReference> toc_refs;
};

// ============================================================================
// Namespace Constants
// ============================================================================

static const char* NAMESPACE_NCX = "http://www.daisy.org/z3986/2005/ncx/";

// ============================================================================
// Helper Functions
// ============================================================================

static pugi::xml_node find_child_by_name_ns(pugi::xml_node parent, const char* name) {
    for (pugi::xml_node child = parent.first_child(); child; child = child.next_sibling()) {
        const char* node_name = child.name();
        // Skip namespace prefix if present
        const char* colon = strrchr(node_name, ':');
        if (colon) {
            node_name = colon + 1;
        }
        if (strcmp(node_name, name) == 0) {
            return child;
        }
    }
    return pugi::xml_node();
}

static pugi::xml_node find_next_sibling_by_name_ns(pugi::xml_node node, const char* name) {
    for (pugi::xml_node sibling = node.next_sibling(); sibling; sibling = sibling.next_sibling()) {
        const char* node_name = sibling.name();
        const char* colon = strrchr(node_name, ':');
        if (colon) {
            node_name = colon + 1;
        }
        if (strcmp(node_name, name) == 0) {
            return sibling;
        }
    }
    return pugi::xml_node();
}

static const char* get_attr(pugi::xml_node node, const char* attr_name) {
    pugi::xml_attribute attr = node.attribute(attr_name);
    return attr ? attr.as_string() : nullptr;
}

static std::string get_text_content(pugi::xml_node node) {
    std::string result;
    for (pugi::xml_node child = node.first_child(); child; child = child.next_sibling()) {
        if (child.type() == pugi::node_pcdata || child.type() == pugi::node_cdata) {
            const char* text = child.value();
            if (text) {
                result += text;
            }
        } else if (child.type() == pugi::node_element) {
            result += get_text_content(child);
        }
    }
    return result;
}

// ============================================================================
// Public API Implementation - Parsing
// ============================================================================

EPUB_NATIVE_API EpubNativeError epub_native_ncx_parse(
    const char* xml_content,
    size_t xml_length,
    EpubNativeNCXDocument** out_doc
) {
    if (!xml_content || !out_doc) {
        set_error("Invalid argument: null pointer");
        return EPUB_NATIVE_ERROR_INVALID_ARG;
    }
    
    try {
        auto doc = new EpubNativeNCXDocument();

        pugi::xml_parse_result result = doc->doc.load_buffer(
            xml_content,
            xml_length,
            pugi::parse_default
        );

        if (!result) {
            set_error(result.description());
            delete doc;
            return EPUB_NATIVE_ERROR_PARSE;
        }

        // Find ncx element
        doc->ncx = doc->doc.child("ncx");
        if (!doc->ncx) {
            for (pugi::xml_node node = doc->doc.first_child(); node; node = node.next_sibling()) {
                const char* name = node.name();
                const char* colon = strrchr(name, ':');
                if (colon) name = colon + 1;
                if (strcmp(name, "ncx") == 0) {
                    doc->ncx = node;
                    break;
                }
            }
        }
        
        if (!doc->ncx) {
            set_error("No ncx element found");
            delete doc;
            return EPUB_NATIVE_ERROR_PARSE;
        }
        
        // Find navMap
        doc->nav_map = find_child_by_name_ns(doc->ncx, "navMap");
        if (!doc->nav_map) {
            set_error("No navMap element found");
            delete doc;
            return EPUB_NATIVE_ERROR_PARSE;
        }
        
        // Parse navPoints
        pugi::xml_node navpoint = find_child_by_name_ns(doc->nav_map, "navPoint");
        while (navpoint) {
            // Read navLabel
            pugi::xml_node nav_label = find_child_by_name_ns(navpoint, "navLabel");
            std::string title = nav_label ? get_text_content(nav_label) : "";

            // Read content src
            pugi::xml_node content = find_child_by_name_ns(navpoint, "content");
            std::string href = content ? (get_attr(content, "src") ? get_attr(content, "src") : "") : "";

            // Read playOrder (use strtol for safe parsing)
            const char* play_order_str = get_attr(navpoint, "playOrder");
            int play_order = 0;
            if (play_order_str) {
                char* end = nullptr;
                long val = strtol(play_order_str, &end, 10);
                if (end != play_order_str && val >= 0 && val <= INT_MAX) {
                    play_order = static_cast<int>(val);
                }
            }

            // Check for children
            bool has_children = !!find_child_by_name_ns(navpoint, "navPoint");

            EpubNativeTOCReference ref;
            ref.title = duplicate_cstring(title.c_str());
            ref.href = duplicate_cstring(href.c_str());
            ref.play_order = play_order;
            ref.has_children = has_children ? 1 : 0;
            if (!ref.title || !ref.href) {
                free((void*)ref.title);
                free((void*)ref.href);
                set_error("Memory allocation failed");
                delete doc;
                return EPUB_NATIVE_ERROR_MEMORY;
            }

            try {
                doc->toc_refs.push_back(ref);
            } catch (...) {
                free((void*)ref.title);
                free((void*)ref.href);
                set_error("Memory allocation failed");
                delete doc;
                return EPUB_NATIVE_ERROR_MEMORY;
            }

            navpoint = find_next_sibling_by_name_ns(navpoint, "navPoint");
        }
        
        *out_doc = doc;
        return EPUB_NATIVE_SUCCESS;
        
    } catch (const std::bad_alloc&) {
        set_error("Memory allocation failed");
        return EPUB_NATIVE_ERROR_MEMORY;
    } catch (...) {
        set_error("Unknown error during NCX parsing");
        return EPUB_NATIVE_ERROR_PARSE;
    }
}

EPUB_NATIVE_API void epub_native_ncx_free(EpubNativeNCXDocument* doc) {
    if (doc) {
        // Free TOC reference strings
        for (auto& ref : doc->toc_refs) {
            free((void*)ref.title);
            free((void*)ref.href);
        }
        delete doc;
    }
}

EPUB_NATIVE_API EpubNativeError epub_native_ncx_get_toc_references(
    EpubNativeNCXDocument* doc,
    EpubNativeTOCReference** out_refs,
    size_t* out_count
) {
    if (!doc || !out_refs || !out_count) {
        set_error("Invalid argument: null pointer");
        return EPUB_NATIVE_ERROR_INVALID_ARG;
    }
    
    size_t count = doc->toc_refs.size();
    if (count == 0) {
        *out_refs = nullptr;
        *out_count = 0;
        return EPUB_NATIVE_SUCCESS;
    }
    
    try {
        if (count > SIZE_MAX / sizeof(EpubNativeTOCReference)) {
            set_error("TOC reference count too large");
            return EPUB_NATIVE_ERROR_MEMORY;
        }
        EpubNativeTOCReference* refs = (EpubNativeTOCReference*)malloc(
            count * sizeof(EpubNativeTOCReference)
        );
        if (!refs) {
            set_error("Memory allocation failed");
            return EPUB_NATIVE_ERROR_MEMORY;
        }

        for (size_t i = 0; i < count; i++) {
            refs[i].title = duplicate_cstring(doc->toc_refs[i].title ? doc->toc_refs[i].title : "");
            refs[i].href = duplicate_cstring(doc->toc_refs[i].href ? doc->toc_refs[i].href : "");
            if (!refs[i].title || !refs[i].href) {
                for (size_t j = 0; j <= i; j++) {
                    free((void*)refs[j].title);
                    free((void*)refs[j].href);
                }
                free(refs);
                set_error("Memory allocation failed");
                return EPUB_NATIVE_ERROR_MEMORY;
            }
            refs[i].play_order = doc->toc_refs[i].play_order;
            refs[i].has_children = doc->toc_refs[i].has_children;
        }
        
        *out_refs = refs;
        *out_count = count;
        return EPUB_NATIVE_SUCCESS;
        
    } catch (...) {
        set_error("Memory allocation failed");
        return EPUB_NATIVE_ERROR_MEMORY;
    }
}

EPUB_NATIVE_API void epub_native_ncx_free_toc_references(
    EpubNativeTOCReference* refs,
    size_t count
) {
    if (refs) {
        for (size_t i = 0; i < count; i++) {
            free((void*)refs[i].title);
            free((void*)refs[i].href);
        }
        free(refs);
    }
}

// ============================================================================
// Public API Implementation - Writing
// ============================================================================

EPUB_NATIVE_API EpubNativeError epub_native_ncx_write(
    const char** identifiers,
    size_t identifier_count,
    const char* title,
    const char** authors,
    size_t author_count,
    const EpubNativeTOCReference* toc_refs,
    size_t toc_count,
    char** out_xml
) {
    if (!out_xml) {
        set_error("Invalid argument: null pointer");
        return EPUB_NATIVE_ERROR_INVALID_ARG;
    }
    *out_xml = nullptr;
    if ((identifier_count > 0 && !identifiers) ||
        (author_count > 0 && !authors) ||
        (toc_count > 0 && !toc_refs)) {
        set_error("Invalid argument: null array pointer with non-zero count");
        return EPUB_NATIVE_ERROR_INVALID_ARG;
    }

    try {
        std::ostringstream ss;
        
        // XML declaration
        ss << "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";
        
        // NCX element with namespace
        ss << "<ncx xmlns=\"" << NAMESPACE_NCX << "\" version=\"2005-1\">\n";
        
        // Head (optional, skip for simplicity)
        
        // DocTitle and DocAuthor
        ss << "  <docTitle><text>";
        append_xml_escaped(ss, title ? title : "");
        ss << "</text></docTitle>\n";
        
        for (size_t i = 0; i < author_count; i++) {
            ss << "  <docAuthor><text>";
            append_xml_escaped(ss, authors[i]);
            ss << "</text></docAuthor>\n";
        }
        
        // Meta elements
        if (identifier_count > 0) {
            ss << "  <meta name=\"dtb:uid\" content=\"";
            append_xml_escaped(ss, identifiers[0]);
            ss << "\"/>\n";
        }
        ss << "  <meta name=\"dtb:generator\" content=\"epub4j-native\"/>\n";
        int dtb_depth = 1;
        for (size_t i = 0; i < toc_count; i++) {
            if (toc_refs[i].has_children) {
                dtb_depth = 2;
                break;
            }
        }
        ss << "  <meta name=\"dtb:depth\" content=\"" << dtb_depth << "\"/>\n";
        ss << "  <meta name=\"dtb:totalPageCount\" content=\"0\"/>\n";
        ss << "  <meta name=\"dtb:maxPageNumber\" content=\"0\"/>\n";
        
        // NavMap
        ss << "  <navMap>\n";
        
        int play_order = 1;
        for (size_t i = 0; i < toc_count; i++) {
            const auto& ref = toc_refs[i];
                int output_play_order = ref.play_order > 0 ? ref.play_order : play_order;
            
            ss << "    <navPoint id=\"navPoint-" << (i + 1) 
                    << "\" playOrder=\"" << output_play_order << "\">\n";
                play_order = output_play_order + 1;
            
            ss << "      <navLabel><text>";
                append_xml_escaped(ss, ref.title);
            ss << "</text></navLabel>\n";
            
            ss << "      <content src=\"";
                append_xml_escaped(ss, ref.href);
            ss << "\"/>\n";
            
            ss << "    </navPoint>\n";
        }
        
        ss << "  </navMap>\n";
        ss << "</ncx>\n";
        
        std::string xml = ss.str();
        *out_xml = duplicate_cstring(xml.c_str());
        if (!*out_xml) {
            set_error("Memory allocation failed");
            return EPUB_NATIVE_ERROR_MEMORY;
        }
        
        return EPUB_NATIVE_SUCCESS;
        
    } catch (const std::bad_alloc&) {
        set_error("Memory allocation failed");
        return EPUB_NATIVE_ERROR_MEMORY;
    } catch (...) {
        set_error("Unknown error during NCX writing");
        return EPUB_NATIVE_ERROR_PARSE;
    }
}
