/**
 * epub_native.cpp - Main C API implementation
 *
 * Bridges C++ XML/HTML parsing libraries to C API for Panama FFM
 */

#include "epub_native.h"
#include "epub_native_internal.h"
#include <cstring>
#include <cstdlib>
#include <new>

// Thread-local error storage (definition - declared in internal header)
thread_local char g_last_error[1024] = {0};

// set_error is now defined inline in epub_native_internal.h

EPUB_NATIVE_API const char* epub_native_get_version(void) {
    return "1.0.0";
}

EPUB_NATIVE_API const char* epub_native_get_last_error(void) {
    return g_last_error;
}

EPUB_NATIVE_API void epub_native_string_free(const char* str) {
    if (str) {
        free((void*)str);
    }
}
