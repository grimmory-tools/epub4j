/**
 * epub4j_native - C API for XML/HTML parsing via Panama FFM
 * 
 * This header defines the C API that will be called from Java via Panama FFM.
 * All functions use C linkage for easy FFI binding.
 */

#ifndef EPUB_NATIVE_H
#define EPUB_NATIVE_H

#ifdef __cplusplus
extern "C" {
#endif

#if defined(_WIN32) || defined(_WIN64)
    #ifdef EPUB4J_NATIVE_EXPORTS
        #define EPUB_NATIVE_API __declspec(dllexport)
    #else
        #define EPUB_NATIVE_API __declspec(dllimport)
    #endif
#else
    #define EPUB_NATIVE_API __attribute__((visibility("default")))
#endif

#include <stddef.h>
#include <stdint.h>

// ============================================================================
// Error Codes
// ============================================================================

typedef enum {
    EPUB_NATIVE_SUCCESS = 0,
    EPUB_NATIVE_ERROR_PARSE = 1,
    EPUB_NATIVE_ERROR_IO = 2,
    EPUB_NATIVE_ERROR_MEMORY = 3,
    EPUB_NATIVE_ERROR_INVALID_ARG = 4,
    EPUB_NATIVE_ERROR_NOT_FOUND = 5,
    EPUB_NATIVE_ERROR_NAMESPACE = 6
} EpubNativeError;

// ============================================================================
// Opaque Types (handled internally by C++ implementation)
// ============================================================================

typedef struct EpubNativePackageDocument EpubNativePackageDocument;
typedef struct EpubNativeNCXDocument EpubNativeNCXDocument;
typedef struct EpubNativeNavDocument EpubNativeNavDocument;
typedef struct EpubNativeHtmlCleaner EpubNativeHtmlCleaner;
typedef struct EpubNativeXmlNode EpubNativeXmlNode;
typedef struct EpubNativeXmlAttr EpubNativeXmlAttr;
typedef struct EpubNativeXmlNodeList EpubNativeXmlNodeList;
typedef struct EpubNativeEncodingDetector EpubNativeEncodingDetector;
typedef struct EpubNativeArchive EpubNativeArchive;

// ============================================================================
// String Handling
// ============================================================================

/**
 * Free a string returned by the library
 * @param str String to free
 */
EPUB_NATIVE_API void epub_native_string_free(const char* str);

// ============================================================================
// Package Document Reader (OPF parsing)
// ============================================================================

/**
 * Parse an OPF package document from XML content
 * @param xml_content UTF-8 XML string
 * @param xml_length Length of XML content
 * @param out_doc Output document pointer
 * @return Error code
 */
EPUB_NATIVE_API EpubNativeError epub_native_package_parse(
    const char* xml_content,
    size_t xml_length,
    EpubNativePackageDocument** out_doc
);

/**
 * Free a package document
 * @param doc Document to free
 */
EPUB_NATIVE_API void epub_native_package_free(EpubNativePackageDocument* doc);

/**
 * Get the package ID (unique identifier)
 * @param doc Package document
 * @return Package ID string (caller must free)
 */
EPUB_NATIVE_API const char* epub_native_package_get_id(
    EpubNativePackageDocument* doc
);

/**
 * Get manifest item by ID
 * @param doc Package document
 * @param item_id Item ID to find
 * @param out_href Output href (caller must free)
 * @param out_media_type Output media type (caller must free)
 * @return Error code
 */
EPUB_NATIVE_API EpubNativeError epub_native_package_get_manifest_item(
    EpubNativePackageDocument* doc,
    const char* item_id,
    const char** out_href,
    const char** out_media_type
);

/**
 * Get spine item IDs in reading order
 * @param doc Package document
 * @param out_ids Output array of IDs
 * @param out_count Output count of IDs
 * @return Error code
 */
EPUB_NATIVE_API EpubNativeError epub_native_package_get_spine_items(
    EpubNativePackageDocument* doc,
    const char*** out_ids,
    size_t* out_count
);

/**
 * Get all manifest item IDs (not just spine items)
 * @param doc Package document
 * @param out_ids Output array of IDs
 * @param out_count Output count of IDs
 * @return Error code
 */
EPUB_NATIVE_API EpubNativeError epub_native_package_get_all_manifest_items(
    EpubNativePackageDocument* doc,
    const char*** out_ids,
    size_t* out_count
);

/**
 * Free spine items array
 * @param ids Array to free
 * @param count Count of items
 */
EPUB_NATIVE_API void epub_native_package_free_spine_items(
    const char** ids,
    size_t count
);

/**
 * Get cover image href
 * @param doc Package document
 * @param out_cover_href Output cover href (caller must free)
 * @return Error code
 */
EPUB_NATIVE_API EpubNativeError epub_native_package_get_cover(
    EpubNativePackageDocument* doc,
    const char** out_cover_href
);

/**
 * Get metadata value by name
 * @param doc Package document
 * @param metadata_name Metadata name (e.g., "title", "creator")
 * @param out_value Output value (caller must free)
 * @return Error code
 */
EPUB_NATIVE_API EpubNativeError epub_native_package_get_metadata(
    EpubNativePackageDocument* doc,
    const char* metadata_name,
    const char** out_value
);

// ============================================================================
// NCX Document (EPUB 2 TOC)
// ============================================================================

/**
 * Parse an NCX document from XML content
 * @param xml_content UTF-8 XML string
 * @param xml_length Length of XML content
 * @param out_doc Output document pointer
 * @return Error code
 */
EPUB_NATIVE_API EpubNativeError epub_native_ncx_parse(
    const char* xml_content,
    size_t xml_length,
    EpubNativeNCXDocument** out_doc
);

/**
 * Free an NCX document
 * @param doc Document to free
 */
EPUB_NATIVE_API void epub_native_ncx_free(EpubNativeNCXDocument* doc);

// NCX TOC Reference structure
typedef struct {
    const char* title;
    const char* href;
    int play_order;
    int has_children;
} EpubNativeTOCReference;

/**
 * Get TOC references from NCX document
 * @param doc NCX document
 * @param out_refs Output array of TOC references
 * @param out_count Output count of references
 * @return Error code
 */
EPUB_NATIVE_API EpubNativeError epub_native_ncx_get_toc_references(
    EpubNativeNCXDocument* doc,
    EpubNativeTOCReference** out_refs,
    size_t* out_count
);

/**
 * Free TOC references array
 * @param refs Array to free
 * @param count Count of items
 */
EPUB_NATIVE_API void epub_native_ncx_free_toc_references(
    EpubNativeTOCReference* refs,
    size_t count
);

/**
 * Write NCX document to string
 * @param identifiers Array of identifier strings
 * @param identifier_count Count of identifiers
 * @param title Document title
 * @param authors Array of author strings
 * @param author_count Count of authors
 * @param toc_refs TOC references
 * @param toc_count Count of TOC references
 * @param out_xml Output XML string (caller must free)
 * @return Error code
 */
EPUB_NATIVE_API EpubNativeError epub_native_ncx_write(
    const char** identifiers,
    size_t identifier_count,
    const char* title,
    const char** authors,
    size_t author_count,
    const EpubNativeTOCReference* toc_refs,
    size_t toc_count,
    char** out_xml
);

// ============================================================================
// Navigation Document (EPUB 3 TOC)
// ============================================================================

/**
 * Create EPUB 3 navigation document
 * @param title Document title
 * @param toc_refs TOC references
 * @param toc_count Count of TOC references
 * @param out_xhtml Output XHTML string (caller must free)
 * @return Error code
 */
EPUB_NATIVE_API EpubNativeError epub_native_nav_create(
    const char* title,
    const EpubNativeTOCReference* toc_refs,
    size_t toc_count,
    char** out_xhtml
);

// ============================================================================
// HTML Cleaner (HTML to XHTML conversion)
// ============================================================================

/**
 * Create HTML cleaner instance
 * @param out_cleaner Output cleaner pointer
 * @return Error code
 */
EPUB_NATIVE_API EpubNativeError epub_native_html_cleaner_create(
    EpubNativeHtmlCleaner** out_cleaner
);

/**
 * Free HTML cleaner instance
 * @param cleaner Cleaner to free
 */
EPUB_NATIVE_API void epub_native_html_cleaner_free(EpubNativeHtmlCleaner* cleaner);

/**
 * Clean HTML content and convert to XHTML
 * @param cleaner HTML cleaner instance
 * @param html_content Input HTML content
 * @param html_length Length of HTML content
 * @param output_encoding Output encoding (e.g., "UTF-8")
 * @param out_xhtml Output XHTML string (caller must free)
 * @param out_length Output length of XHTML
 * @return Error code
 */
EPUB_NATIVE_API EpubNativeError epub_native_html_clean(
    EpubNativeHtmlCleaner* cleaner,
    const char* html_content,
    size_t html_length,
    const char* output_encoding,
    char** out_xhtml,
    size_t* out_length
);

// ============================================================================
// Utility Functions
// ============================================================================

/**
 * Get library version string
 * @return Version string (do not free)
 */
EPUB_NATIVE_API const char* epub_native_get_version(void);

/**
 * Get last error message
 * @return Error message (do not free)
 */
EPUB_NATIVE_API const char* epub_native_get_last_error(void);

// ============================================================================
// Encoding Detection
// ============================================================================

/**
 * Create encoding detector instance
 * @param out_detector Output detector pointer
 * @return Error code
 */
EPUB_NATIVE_API EpubNativeError epub_native_encoding_detector_create(
    EpubNativeEncodingDetector** out_detector
);

/**
 * Free encoding detector instance
 * @param detector Detector to free
 */
EPUB_NATIVE_API void epub_native_encoding_detector_free(
    EpubNativeEncodingDetector* detector
);

/**
 * Detect encoding of byte data
 * @param detector Encoding detector instance
 * @param data Input byte data
 * @param data_length Length of input data
 * @param out_encoding Detected encoding name (caller must free with epub_native_string_free)
 * @param out_confidence Confidence score 0-100
 * @return Error code
 */
EPUB_NATIVE_API EpubNativeError epub_native_detect_encoding(
    EpubNativeEncodingDetector* detector,
    const char* data,
    size_t data_length,
    char** out_encoding,
    int* out_confidence
);

/**
 * Convert text from one encoding to UTF-8
 * @param source_encoding Source encoding name (e.g., "ISO-8859-1")
 * @param source_data Source data
 * @param source_length Source data length
 * @param out_utf8 Output UTF-8 data (caller must free with epub_native_string_free)
 * @param out_utf8_length Output UTF-8 data length
 * @return Error code
 */
EPUB_NATIVE_API EpubNativeError epub_native_convert_to_utf8(
    const char* source_encoding,
    const char* source_data,
    size_t source_length,
    char** out_utf8,
    size_t* out_utf8_length
);

// ============================================================================
// Archive Handling (libarchive-based ZIP support)
// ============================================================================

/**
 * Open an archive (ZIP/EPUB) file
 * @param filepath Path to archive file
 * @param out_archive Output archive pointer
 * @return Error code
 */
EPUB_NATIVE_API EpubNativeError epub_native_archive_open(
    const char* filepath,
    EpubNativeArchive** out_archive
);

/**
 * Open an archive from memory buffer
 * @param data Archive data bytes
 * @param data_length Data length
 * @param out_archive Output archive pointer
 * @return Error code
 */
EPUB_NATIVE_API EpubNativeError epub_native_archive_open_memory(
    const char* data,
    size_t data_length,
    EpubNativeArchive** out_archive
);

/**
 * Close and free an archive
 * @param archive Archive to free
 */
EPUB_NATIVE_API void epub_native_archive_free(EpubNativeArchive* archive);

/**
 * Get list of all entries in the archive
 * @param archive Archive handle
 * @param out_entries Output array of entry paths (caller must free with epub_native_archive_free_string_array)
 * @param out_count Output count of entries
 * @return Error code
 */
EPUB_NATIVE_API EpubNativeError epub_native_archive_list_entries(
    EpubNativeArchive* archive,
    char*** out_entries,
    size_t* out_count
);

/**
 * Free string array returned by archive functions
 * @param entries Array to free
 * @param count Count of entries
 */
EPUB_NATIVE_API void epub_native_archive_free_string_array(
    char** entries,
    size_t count
);

/**
 * Read a file entry from the archive
 * @param archive Archive handle
 * @param entry_path Path of entry to read
 * @param out_data Output data bytes (caller must free with epub_native_string_free)
 * @param out_data_length Output data length
 * @return Error code
 */
EPUB_NATIVE_API EpubNativeError epub_native_archive_read_entry(
    EpubNativeArchive* archive,
    const char* entry_path,
    char** out_data,
    size_t* out_data_length
);

/**
 * Callback for reading archive entry in blocks
 * @param data Data block
 * @param size Size of data block
 * @param user_data User-provided context
 */
typedef int (*epub_native_archive_read_callback)(const void* data, size_t size, void* user_data);

/**
 * Read an archive entry via callback (streaming)
 * @param archive Archive handle
 * @param entry_path Path of entry to read
 * @param callback Callback function
 * @param user_data User-provided context
 * @return Error code
 */
EPUB_NATIVE_API EpubNativeError epub_native_archive_read_entry_to_callback(
    EpubNativeArchive* archive,
    const char* entry_path,
    epub_native_archive_read_callback callback,
    void* user_data
);

/**
 * Check if archive entry exists
 * @param archive Archive handle
 * @param entry_path Path to check
 * @return EPUB_NATIVE_SUCCESS if exists, EPUB_NATIVE_ERROR_NOT_FOUND otherwise
 */
EPUB_NATIVE_API EpubNativeError epub_native_archive_entry_exists(
    EpubNativeArchive* archive,
    const char* entry_path
);

// ============================================================================
// Image Processing
// ============================================================================

/** Image format constants for epub_native_image_get_dimensions */
#define EPUB_NATIVE_IMAGE_FORMAT_UNKNOWN 0
#define EPUB_NATIVE_IMAGE_FORMAT_JPEG    1
#define EPUB_NATIVE_IMAGE_FORMAT_PNG     2
#define EPUB_NATIVE_IMAGE_FORMAT_WEBP    3

/**
 * Check if JPEG processing support (libjpeg-turbo) is compiled in
 * @return 1 if available, 0 otherwise
 */
EPUB_NATIVE_API int epub_native_image_has_jpeg(void);

/**
 * Check if PNG processing support (libpng) is compiled in
 * @return 1 if available, 0 otherwise
 */
EPUB_NATIVE_API int epub_native_image_has_png(void);

/**
 * Check if WebP processing support (libwebp) is compiled in
 * @return 1 if available, 0 otherwise
 */
EPUB_NATIVE_API int epub_native_image_has_webp(void);

/**
 * Read image dimensions and format without full decode
 * @param data Image file bytes
 * @param data_length Length of data
 * @param out_width Output image width
 * @param out_height Output image height
 * @param out_format Output image format (EPUB_NATIVE_IMAGE_FORMAT_*)
 * @return Error code
 */
EPUB_NATIVE_API EpubNativeError epub_native_image_get_dimensions(
    const uint8_t* data,
    size_t data_length,
    int* out_width,
    int* out_height,
    int* out_format
);

/**
 * Lossless JPEG optimization: progressive encoding + Huffman table optimization.
 * Uses coefficient-level transform to avoid generation loss.
 * @param data JPEG file bytes
 * @param data_length Length of data
 * @param out_data Output optimized JPEG bytes (caller must free with epub_native_image_data_free)
 * @param out_length Output length
 * @return Error code
 */
EPUB_NATIVE_API EpubNativeError epub_native_jpeg_optimize(
    const uint8_t* data,
    size_t data_length,
    uint8_t** out_data,
    size_t* out_length
);

/**
 * Lossy JPEG re-compression at a target quality level.
 * Decodes to pixels then re-encodes.
 * @param data JPEG or PNG or WebP file bytes (any supported input format)
 * @param data_length Length of data
 * @param quality JPEG quality 1-100
 * @param progressive 1 for progressive JPEG, 0 for baseline
 * @param out_data Output JPEG bytes (caller must free with epub_native_image_data_free)
 * @param out_length Output length
 * @return Error code
 */
EPUB_NATIVE_API EpubNativeError epub_native_jpeg_compress(
    const uint8_t* data,
    size_t data_length,
    int quality,
    int progressive,
    uint8_t** out_data,
    size_t* out_length
);

/**
 * Optimize PNG: strip ancillary chunks, re-compress with maximum effort.
 * @param data PNG file bytes
 * @param data_length Length of data
 * @param strip_ancillary 1 to strip non-critical chunks, 0 to preserve
 * @param out_data Output PNG bytes (caller must free with epub_native_image_data_free)
 * @param out_length Output length
 * @return Error code
 */
EPUB_NATIVE_API EpubNativeError epub_native_png_optimize(
    const uint8_t* data,
    size_t data_length,
    int strip_ancillary,
    uint8_t** out_data,
    size_t* out_length
);

/**
 * Encode raw RGBA pixels to WebP
 * @param rgba_pixels Raw RGBA pixel data (4 bytes per pixel)
 * @param width Image width
 * @param height Image height
 * @param stride Row stride in bytes (typically width * 4)
 * @param quality Lossy quality 0-100, or -1 for lossless
 * @param out_data Output WebP bytes (caller must free with epub_native_image_data_free)
 * @param out_length Output length
 * @return Error code
 */
EPUB_NATIVE_API EpubNativeError epub_native_webp_encode(
    const uint8_t* rgba_pixels,
    int width,
    int height,
    int stride,
    int quality,
    uint8_t** out_data,
    size_t* out_length
);

/**
 * Resize an image with high-quality Lanczos filtering.
 * Decodes input, resizes, re-encodes to the target format.
 * @param data Image file bytes (JPEG, PNG, or WebP)
 * @param data_length Length of data
 * @param target_width Target width in pixels
 * @param target_height Target height in pixels
 * @param output_format Output format (EPUB_NATIVE_IMAGE_FORMAT_JPEG, _PNG, or _WEBP)
 * @param quality Output quality for lossy formats (1-100)
 * @param out_data Output resized image bytes (caller must free with epub_native_image_data_free)
 * @param out_length Output length
 * @return Error code
 */
EPUB_NATIVE_API EpubNativeError epub_native_image_resize(
    const uint8_t* data,
    size_t data_length,
    int target_width,
    int target_height,
    int output_format,
    int quality,
    uint8_t** out_data,
    size_t* out_length
);

/**
 * Free image data returned by image processing functions
 * @param data Data to free
 */
EPUB_NATIVE_API void epub_native_image_data_free(uint8_t* data);

#ifdef __cplusplus
}
#endif

#endif // EPUB_NATIVE_H
