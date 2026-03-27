/**
 * epub_encoding.cpp - Encoding Detection and Conversion Implementation
 *
 * Uses ICU (International Components for Unicode) for encoding detection
 * and conversion.
 */

#include "epub_native.h"
#include "epub_native_internal.h"
#include <cstdlib>

#ifdef HAVE_ICU
#include <unicode/ucsdet.h>
#include <unicode/ucnv.h>
#include <unicode/ustring.h>
#endif

// ============================================================================
// Internal Structures
// ============================================================================

struct EpubNativeEncodingDetector {
#ifdef HAVE_ICU
    UCharsetDetector* detector;
#else
    int dummy; // Placeholder when ICU is not available
#endif
    std::string last_error;
};

// ============================================================================
// Encoding Detector Implementation
// ============================================================================

EPUB_NATIVE_API EpubNativeError epub_native_encoding_detector_create(
    EpubNativeEncodingDetector** out_detector
) {
    if (!out_detector) {
        set_error("Invalid argument: null pointer");
        return EPUB_NATIVE_ERROR_INVALID_ARG;
    }

    try {
        auto detector = new EpubNativeEncodingDetector();

#ifdef HAVE_ICU
        UErrorCode status = U_ZERO_ERROR;
        detector->detector = ucsdet_open(&status);
        if (U_FAILURE(status)) {
            set_error("Failed to create ICU charset detector");
            delete detector;
            return EPUB_NATIVE_ERROR_MEMORY;
        }
#else
        detector->dummy = 0;
#endif

        *out_detector = detector;
        return EPUB_NATIVE_SUCCESS;

    } catch (const std::bad_alloc&) {
        set_error("Memory allocation failed");
        return EPUB_NATIVE_ERROR_MEMORY;
    } catch (...) {
        set_error("Unknown error creating encoding detector");
        return EPUB_NATIVE_ERROR_MEMORY;
    }
}

EPUB_NATIVE_API void epub_native_encoding_detector_free(
    EpubNativeEncodingDetector* detector
) {
    if (detector) {
#ifdef HAVE_ICU
        if (detector->detector) {
            ucsdet_close(detector->detector);
        }
#endif
        delete detector;
    }
}

EPUB_NATIVE_API EpubNativeError epub_native_detect_encoding(
    EpubNativeEncodingDetector* detector,
    const char* data,
    size_t data_length,
    char** out_encoding,
    int* out_confidence
) {
    if (!detector || !data || !out_encoding || !out_confidence) {
        set_error("Invalid argument: null pointer");
        return EPUB_NATIVE_ERROR_INVALID_ARG;
    }

#ifdef HAVE_ICU
    try {
        UErrorCode status = U_ZERO_ERROR;
        
        // Set the text to be analyzed
        ucsdet_setText(detector->detector, data, static_cast<int32_t>(data_length), &status);
        if (U_FAILURE(status)) {
            set_error("Failed to set text for encoding detection");
            return EPUB_NATIVE_ERROR_INVALID_ARG;
        }

        // Find the matching encoding
        const UCharsetMatch* match = ucsdet_detect(detector->detector, &status);
        if (U_FAILURE(status) || !match) {
            set_error("Failed to detect encoding");
            return EPUB_NATIVE_ERROR_NOT_FOUND;
        }

        // Get the encoding name
        const char* encoding = ucsdet_getName(match, &status);
        if (U_FAILURE(status) || !encoding) {
            set_error("Failed to get encoding name");
            return EPUB_NATIVE_ERROR_NOT_FOUND;
        }

        // Get confidence level
        int32_t confidence = ucsdet_getConfidence(match, &status);
        if (U_FAILURE(status)) {
            confidence = 50; // Default confidence
        }

        // Allocate and copy encoding name
        *out_encoding = duplicate_cstring(encoding);
        if (!*out_encoding) {
            set_error("Memory allocation failed");
            return EPUB_NATIVE_ERROR_MEMORY;
        }

        *out_confidence = static_cast<int>(confidence);
        return EPUB_NATIVE_SUCCESS;

    } catch (const std::bad_alloc&) {
        set_error("Memory allocation failed");
        return EPUB_NATIVE_ERROR_MEMORY;
    } catch (...) {
        set_error("Unknown error detecting encoding");
        return EPUB_NATIVE_ERROR_PARSE;
    }
#else
    // Fallback: assume UTF-8 when ICU is not available
    *out_encoding = duplicate_cstring("UTF-8");
    *out_confidence = 50; // Medium confidence for fallback
    if (!*out_encoding) {
        set_error("Memory allocation failed");
        return EPUB_NATIVE_ERROR_MEMORY;
    }
    return EPUB_NATIVE_SUCCESS;
#endif
}

EPUB_NATIVE_API EpubNativeError epub_native_convert_to_utf8(
    const char* source_encoding,
    const char* source_data,
    size_t source_length,
    char** out_utf8,
    size_t* out_utf8_length
) {
    if (!source_encoding || !source_data || !out_utf8 || !out_utf8_length) {
        set_error("Invalid argument: null pointer");
        return EPUB_NATIVE_ERROR_INVALID_ARG;
    }

#ifdef HAVE_ICU
    try {
        UErrorCode status = U_ZERO_ERROR;
        
        // Open converter for source encoding
        UConverter* converter = ucnv_open(source_encoding, &status);
        if (U_FAILURE(status)) {
            std::string err = "Failed to open converter for encoding: ";
            err += source_encoding;
            set_error(err.c_str());
            return EPUB_NATIVE_ERROR_INVALID_ARG;
        }

        // Calculate required UTF-8 buffer size
        int32_t utf8_length = ucnv_toAlgorithmic(
            UCNV_UTF8, converter,
            nullptr, 0,
            source_data, static_cast<int32_t>(source_length),
            &status
        );
        if (utf8_length < 0) {
            ucnv_close(converter);
            set_error("Failed to compute UTF-8 output length");
            return EPUB_NATIVE_ERROR_PARSE;
        }
        
        // Reset status and allocate buffer
        status = U_ZERO_ERROR;
        char* utf8_buffer = static_cast<char*>(malloc(static_cast<size_t>(utf8_length) + 1));
        if (!utf8_buffer) {
            ucnv_close(converter);
            set_error("Memory allocation failed");
            return EPUB_NATIVE_ERROR_MEMORY;
        }

        // Convert to UTF-8
        int32_t result = ucnv_toAlgorithmic(
            UCNV_UTF8, converter,
            utf8_buffer, utf8_length + 1,
            source_data, static_cast<int32_t>(source_length),
            &status
        );

        ucnv_close(converter);

        if (U_FAILURE(status)) {
            free(utf8_buffer);
            set_error("Conversion to UTF-8 failed");
            return EPUB_NATIVE_ERROR_PARSE;
        }

        utf8_buffer[result] = '\0';
        *out_utf8 = utf8_buffer;
        *out_utf8_length = static_cast<size_t>(result);
        return EPUB_NATIVE_SUCCESS;

    } catch (const std::bad_alloc&) {
        set_error("Memory allocation failed");
        return EPUB_NATIVE_ERROR_MEMORY;
    } catch (...) {
        set_error("Unknown error converting to UTF-8");
        return EPUB_NATIVE_ERROR_PARSE;
    }
#else
    // Fallback: assume source is already UTF-8
    *out_utf8 = duplicate_cstring(source_data);
    *out_utf8_length = source_length;
    if (!*out_utf8) {
        set_error("Memory allocation failed");
        return EPUB_NATIVE_ERROR_MEMORY;
    }
    return EPUB_NATIVE_SUCCESS;
#endif
}
