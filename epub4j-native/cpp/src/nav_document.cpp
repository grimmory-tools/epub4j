/**
 * nav_document.cpp - Navigation Document (EPUB 3 TOC) Implementation
 *
 * Generates EPUB 3 XHTML Navigation Documents
 */

#include "epub_native.h"
#include "epub_native_internal.h"
#include <sstream>
#include <cstring>
#include <cstdlib>

// ============================================================================
// Namespace Constants
// ============================================================================

static const char* NAMESPACE_XHTML = "http://www.w3.org/1999/xhtml";
static const char* NAMESPACE_EPUB = "http://www.idpf.org/2007/ops";

// ============================================================================
// TOC Entry Writing (recursive for nested structure)
// ============================================================================

static void write_toc_entries(
    std::ostringstream& ss,
    const EpubNativeTOCReference* toc_refs,
    size_t toc_count,
    int indent_level
) {
    std::string indent(indent_level * 2, ' ');
    
    ss << indent << "<ol>\n";
    
    for (size_t i = 0; i < toc_count; i++) {
        const auto& ref = toc_refs[i];
        
        ss << indent << "  <li>\n";
        
        if (ref.href && ref.href[0] != '\0') {
            ss << indent << "    <a href=\"";
            append_xml_escaped(ss, ref.href);
            ss << "\">";
            append_xml_escaped(ss, ref.title);
            ss << "</a>\n";
        } else {
            ss << indent << "    <span>";
            append_xml_escaped(ss, ref.title);
            ss << "</span>\n";
        }
        
        // Note: Nested TOC would go here if we supported children
        // For now, we just handle flat structure
        
        ss << indent << "  </li>\n";
    }
    
    ss << indent << "</ol>\n";
}

// ============================================================================
// Public API Implementation
// ============================================================================

EPUB_NATIVE_API EpubNativeError epub_native_nav_create(
    const char* title,
    const EpubNativeTOCReference* toc_refs,
    size_t toc_count,
    char** out_xhtml
) {
    if (!out_xhtml) {
        set_error("Invalid argument: null pointer");
        return EPUB_NATIVE_ERROR_INVALID_ARG;
    }
    *out_xhtml = nullptr;
    if (toc_count > 0 && !toc_refs) {
        set_error("Invalid argument: toc_refs is null but toc_count > 0");
        return EPUB_NATIVE_ERROR_INVALID_ARG;
    }
    
    try {
        std::ostringstream ss;
        
        // DOCTYPE
        ss << "<!DOCTYPE html>\n";
        
        // HTML element with namespaces
        ss << "<html xmlns=\"" << NAMESPACE_XHTML 
           << "\" xmlns:epub=\"" << NAMESPACE_EPUB << "\">\n";
        
        // Head
        ss << "  <head>\n";
        ss << "    <title>";
        append_xml_escaped(ss, title ? title : "Table of Contents");
        ss << "</title>\n";
        ss << "    <meta charset=\"utf-8\" />\n";
        ss << "    <style>\n";
        ss << "      nav#toc > ol { list-style-type: none; padding-left: 0; }\n";
        ss << "      nav#toc > ol > li > ol { margin-left: 1.5em; }\n";
        ss << "    </style>\n";
        ss << "  </head>\n";
        
        // Body
        ss << "  <body>\n";
        
        // Navigation element
        ss << "    <nav epub:type=\"toc\" id=\"toc\">\n";
        ss << "      <h1>";
        append_xml_escaped(ss, title ? title : "Table of Contents");
        ss << "</h1>\n";
        
        // TOC entries
        write_toc_entries(ss, toc_refs, toc_count, 2);
        
        ss << "    </nav>\n";
        ss << "  </body>\n";
        ss << "</html>\n";
        
        std::string xhtml = ss.str();
        *out_xhtml = duplicate_cstring(xhtml.c_str());
        if (!*out_xhtml) {
            set_error("Memory allocation failed");
            return EPUB_NATIVE_ERROR_MEMORY;
        }
        
        return EPUB_NATIVE_SUCCESS;
        
    } catch (const std::bad_alloc&) {
        set_error("Memory allocation failed");
        return EPUB_NATIVE_ERROR_MEMORY;
    } catch (...) {
        set_error("Unknown error during nav document creation");
        return EPUB_NATIVE_ERROR_PARSE;
    }
}
