/**
 * html_cleaner.cpp - HTML Cleaner Implementation
 *
 * Uses Gumbo HTML5 parser to parse and clean HTML, then serializes to XHTML
 */

#include "epub_native.h"
#include "epub_native_internal.h"
#include "gumbo.h"
#include <sstream>
#include <vector>
#include <cstring>
#include <cstdlib>
#include <string>

// ============================================================================
// Internal Structures
// ============================================================================

struct EpubNativeHtmlCleaner {
    GumboOutput* output;
    std::string last_error;
};

// ============================================================================
// Void elements (no closing tag)
// ============================================================================

static bool is_void_element(const char* tag) {
    if (!tag) {
        return false;
    }
    return strcmp(tag, "area") == 0 || strcmp(tag, "base") == 0 ||
           strcmp(tag, "br") == 0 || strcmp(tag, "col") == 0 ||
           strcmp(tag, "embed") == 0 || strcmp(tag, "hr") == 0 ||
           strcmp(tag, "img") == 0 || strcmp(tag, "input") == 0 ||
           strcmp(tag, "link") == 0 || strcmp(tag, "meta") == 0 ||
           strcmp(tag, "param") == 0 || strcmp(tag, "source") == 0 ||
           strcmp(tag, "track") == 0 || strcmp(tag, "wbr") == 0;
}

// ============================================================================
// Tag name helper
// ============================================================================

static const char* get_tag_name(const GumboNode* node) {
    if (!node || node->type != GUMBO_NODE_ELEMENT) {
        return nullptr;
    }
    return gumbo_normalized_tagname(node->v.element.tag);
}

// ============================================================================
// XHTML Serialization
// ============================================================================

static bool is_preformatted_element(const char* tag) {
    if (!tag) {
        return false;
    }
    return strcmp(tag, "pre") == 0 || strcmp(tag, "textarea") == 0 ||
           strcmp(tag, "script") == 0 || strcmp(tag, "style") == 0;
}

static void append_text_escaped(std::ostringstream& ss, const char* text) {
    if (!text) {
        return;
    }
    while (*text) {
        switch (*text) {
            case '&': ss << "&amp;"; break;
            case '<': ss << "&lt;"; break;
            case '>': ss << "&gt;"; break;
            default: ss << *text; break;
        }
        text++;
    }
}

static void append_attr_escaped(std::ostringstream& ss, const char* text) {
    if (!text) {
        return;
    }
    while (*text) {
        switch (*text) {
            case '&': ss << "&amp;"; break;
            case '<': ss << "&lt;"; break;
            case '>': ss << "&gt;"; break;
            case '"': ss << "&quot;"; break;
            case '\'': ss << "&apos;"; break;
            default: ss << *text; break;
        }
        text++;
    }
}

static void serialize_node(std::ostringstream& ss, const GumboNode* node) {
    if (!node) return;

    switch (node->type) {
        case GUMBO_NODE_DOCUMENT: {
            const GumboVector* children = &node->v.document.children;
            for (size_t i = 0; i < children->length; i++) {
                serialize_node(ss, static_cast<const GumboNode*>(children->data[i]));
            }
            break;
        }

        case GUMBO_NODE_ELEMENT: {
            const char* tag_name = get_tag_name(node);
            if (!tag_name) tag_name = "unknown";

            const GumboElement* elem = &node->v.element;

            ss << "<" << tag_name;

            bool has_default_xmlns = false;

            // Serialize attributes
            const GumboVector* attrs = &elem->attributes;
            for (size_t i = 0; i < attrs->length; i++) {
                GumboAttribute* attr = static_cast<GumboAttribute*>(attrs->data[i]);
                const char* attr_name = attr->name;
                const char* attr_value = attr->value;

                if (strcmp(attr_name, "xmlns") == 0) {
                    has_default_xmlns = true;
                }

                // Skip event handlers (security)
                if ((attr_name[0] == 'o' || attr_name[0] == 'O') &&
                    (attr_name[1] == 'n' || attr_name[1] == 'N')) {
                    continue;
                }

                ss << " " << attr_name << "=\"";
                append_attr_escaped(ss, attr_value);
                ss << "\"";
            }

            if (strcmp(tag_name, "html") == 0 && !has_default_xmlns) {
                ss << " xmlns=\"http://www.w3.org/1999/xhtml\"";
            }

            if (is_void_element(tag_name)) {
                ss << " />";
            } else {
                ss << ">";
                const GumboVector* children = &elem->children;

                if (is_preformatted_element(tag_name)) {
                    for (size_t i = 0; i < children->length; i++) {
                        const GumboNode* child = static_cast<const GumboNode*>(children->data[i]);
                        if (child->type == GUMBO_NODE_TEXT || child->type == GUMBO_NODE_WHITESPACE || child->type == GUMBO_NODE_CDATA) {
                            const char* text = child->v.text.text;
                            if (text) {
                                if (strcmp(tag_name, "script") == 0 || strcmp(tag_name, "style") == 0) {
                                    ss << text;
                                } else {
                                    append_text_escaped(ss, text);
                                }
                            }
                        } else {
                            serialize_node(ss, child);
                        }
                    }
                } else {
                    for (size_t i = 0; i < children->length; i++) {
                        serialize_node(ss, static_cast<const GumboNode*>(children->data[i]));
                    }
                }

                ss << "</" << tag_name << ">";
            }
            break;
        }

        case GUMBO_NODE_TEXT: {
            const char* text = node->v.text.text;
            if (text) {
                append_text_escaped(ss, text);
            }
            break;
        }

        case GUMBO_NODE_WHITESPACE: {
            const char* text = node->v.text.text;
            if (text) {
                append_text_escaped(ss, text);
            }
            break;
        }

        case GUMBO_NODE_CDATA: {
            const char* text = node->v.text.text;
            if (text && text[0] != '\0') {
                ss << "<![CDATA[" << text << "]]>";
            }
            break;
        }

        case GUMBO_NODE_COMMENT: {
            const char* text = node->v.text.text;
            if (text && text[0] != '\0') {
                ss << "<!--" << text << "-->";
            }
            break;
        }

        default:
            break;
    }
}

// ============================================================================
// Public API Implementation
// ============================================================================

EPUB_NATIVE_API EpubNativeError epub_native_html_cleaner_create(
    EpubNativeHtmlCleaner** out_cleaner
) {
    if (!out_cleaner) {
        set_error("Invalid argument: null pointer");
        return EPUB_NATIVE_ERROR_INVALID_ARG;
    }

    try {
        auto cleaner = new EpubNativeHtmlCleaner();
        cleaner->output = nullptr;
        *out_cleaner = cleaner;
        return EPUB_NATIVE_SUCCESS;
    } catch (const std::bad_alloc&) {
        set_error("Memory allocation failed");
        return EPUB_NATIVE_ERROR_MEMORY;
    } catch (...) {
        set_error("Memory allocation failed");
        return EPUB_NATIVE_ERROR_MEMORY;
    }
}

EPUB_NATIVE_API void epub_native_html_cleaner_free(EpubNativeHtmlCleaner* cleaner) {
    if (cleaner) {
        if (cleaner->output) {
            gumbo_destroy_output(&kGumboDefaultOptions, cleaner->output);
        }
        delete cleaner;
    }
}

EPUB_NATIVE_API EpubNativeError epub_native_html_clean(
    EpubNativeHtmlCleaner* cleaner,
    const char* html_content,
    size_t html_length,
    const char* output_encoding,
    char** out_xhtml,
    size_t* out_length
) {
    if (!cleaner || !html_content || !out_xhtml) {
        set_error("Invalid argument: null pointer");
        return EPUB_NATIVE_ERROR_INVALID_ARG;
    }
    (void)output_encoding;

    try {
        // Destroy previous output if any
        if (cleaner->output) {
            gumbo_destroy_output(&kGumboDefaultOptions, cleaner->output);
            cleaner->output = nullptr;
        }

        // Parse HTML
        cleaner->output = gumbo_parse_with_options(
            &kGumboDefaultOptions,
            html_content,
            html_length
        );

        if (!cleaner->output) {
            cleaner->last_error = "Failed to parse HTML";
            set_error(cleaner->last_error.c_str());
            return EPUB_NATIVE_ERROR_PARSE;
        }

        // Note: New Gumbo API doesn't have status field
        // Parsing may have errors but we can still try to serialize

        // Serialize to XHTML
        std::ostringstream ss;

        // XML declaration
        ss << "<?xml version=\"1.0\" encoding=\"";
        ss << "UTF-8";
        ss << "\"?>\n";

        // DOCTYPE
        ss << "<!DOCTYPE html>\n";

        // Serialize the root element
        serialize_node(ss, cleaner->output->root);

        std::string xhtml = ss.str();
        *out_xhtml = duplicate_cstring(xhtml.c_str());
        if (!*out_xhtml) {
            set_error("Memory allocation failed");
            return EPUB_NATIVE_ERROR_MEMORY;
        }

        if (out_length) {
            *out_length = xhtml.length();
        }

        return EPUB_NATIVE_SUCCESS;

    } catch (const std::bad_alloc&) {
        set_error("Memory allocation failed");
        return EPUB_NATIVE_ERROR_MEMORY;
    } catch (...) {
        set_error("Unknown error during HTML cleaning");
        return EPUB_NATIVE_ERROR_PARSE;
    }
}
