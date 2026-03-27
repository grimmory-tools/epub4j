/**
 * epub_native_internal.h - Internal utility functions
 *
 * Shared utilities for native implementation files
 */

#ifndef EPUB_NATIVE_INTERNAL_H
#define EPUB_NATIVE_INTERNAL_H

#include <cstring>
#include <cstdlib>
#include <sstream>

// Thread-local error storage (defined in epub_native.cpp)
extern thread_local char g_last_error[1024];

// Set error message (shared utility)
inline void set_error(const char* msg) {
    strncpy(g_last_error, msg ? msg : "Unknown error", sizeof(g_last_error) - 1);
    g_last_error[sizeof(g_last_error) - 1] = '\0';
}

inline char* duplicate_cstring(const char* str) {
    if (!str) {
        return nullptr;
    }

    size_t len = std::strlen(str);
    char* copy = static_cast<char*>(std::malloc(len + 1));
    if (!copy) {
        return nullptr;
    }

    std::memcpy(copy, str, len);
    copy[len] = '\0';
    return copy;
}

inline void append_xml_escaped(std::ostringstream& ss, const char* text) {
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

#endif // EPUB_NATIVE_INTERNAL_H
