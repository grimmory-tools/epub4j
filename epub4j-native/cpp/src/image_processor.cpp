/**
 * Native image processing: JPEG, PNG, and WebP operations via
 * libjpeg-turbo, libpng, and libwebp.
 *
 * Each function operates on raw byte buffers passed from Java via Panama FFM.
 * Output buffers are malloc'd and must be freed by the caller via
 * epub_native_image_data_free().
 */

#include "epub_native.h"

#include <cstdlib>
#include <cstring>
#include <cstdarg>
#include <cstdio>
#include <csetjmp>
#include <vector>
#include <algorithm>

// Compile-time feature flags set by CMake
#ifdef EPUB4J_NATIVE_HAS_JPEG
#include <jpeglib.h>
#include <turbojpeg.h>
#endif

#ifdef EPUB4J_NATIVE_HAS_PNG
#include <png.h>
#endif

#ifdef EPUB4J_NATIVE_HAS_WEBP
#include <webp/decode.h>
#include <webp/encode.h>
#endif

// stb_image_resize2 for high-quality Lanczos resampling
// Suppress warnings in third-party header
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wsign-conversion"
#pragma GCC diagnostic ignored "-Wconversion"
#pragma GCC diagnostic ignored "-Wpedantic"
#define STB_IMAGE_RESIZE_IMPLEMENTATION
#define STBIR_DEFAULT_FILTER_DOWNSAMPLE STBIR_FILTER_MITCHELL
#define STBIR_DEFAULT_FILTER_UPSAMPLE   STBIR_FILTER_CATMULLROM
#include "stb_image_resize2.h"
#pragma GCC diagnostic pop

static thread_local char g_image_error[1024] = {0};

static void set_error(const char* fmt, ...) {
    va_list args;
    va_start(args, fmt);
    vsnprintf(g_image_error, sizeof(g_image_error), fmt, args);
    va_end(args);
}

// ---------------------------------------------------------------------------
// Format detection helpers
// ---------------------------------------------------------------------------

static bool is_jpeg(const uint8_t* data, size_t len) {
    return len >= 2 && data[0] == 0xFF && data[1] == 0xD8;
}

static bool is_png(const uint8_t* data, size_t len) {
    static const uint8_t sig[8] = {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    return len >= 8 && memcmp(data, sig, 8) == 0;
}

static bool is_webp(const uint8_t* data, size_t len) {
    return len >= 12
        && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
        && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P';
}

// ---------------------------------------------------------------------------
// Availability checks
// ---------------------------------------------------------------------------

extern "C" EPUB_NATIVE_API int epub_native_image_has_jpeg(void) {
#ifdef EPUB4J_NATIVE_HAS_JPEG
    return 1;
#else
    return 0;
#endif
}

extern "C" EPUB_NATIVE_API int epub_native_image_has_png(void) {
#ifdef EPUB4J_NATIVE_HAS_PNG
    return 1;
#else
    return 0;
#endif
}

extern "C" EPUB_NATIVE_API int epub_native_image_has_webp(void) {
#ifdef EPUB4J_NATIVE_HAS_WEBP
    return 1;
#else
    return 0;
#endif
}

// ---------------------------------------------------------------------------
// Dimension reading
// ---------------------------------------------------------------------------

extern "C" EPUB_NATIVE_API EpubNativeError epub_native_image_get_dimensions(
    const uint8_t* data, size_t data_length,
    int* out_width, int* out_height, int* out_format)
{
    if (!data || data_length == 0 || !out_width || !out_height || !out_format) {
        set_error("null argument in image_get_dimensions");
        return EPUB_NATIVE_ERROR_INVALID_ARG;
    }

    *out_width = 0;
    *out_height = 0;
    *out_format = EPUB_NATIVE_IMAGE_FORMAT_UNKNOWN;

#ifdef EPUB4J_NATIVE_HAS_JPEG
    if (is_jpeg(data, data_length)) {
        tjhandle handle = tj3Init(TJINIT_DECOMPRESS);
        if (!handle) {
            set_error("failed to init TurboJPEG decompressor");
            return EPUB_NATIVE_ERROR_MEMORY;
        }
        int err = tj3DecompressHeader(handle, data, data_length);
        if (err == 0) {
            *out_width  = tj3Get(handle, TJPARAM_JPEGWIDTH);
            *out_height = tj3Get(handle, TJPARAM_JPEGHEIGHT);
            *out_format = EPUB_NATIVE_IMAGE_FORMAT_JPEG;
        }
        tj3Destroy(handle);
        if (err != 0) {
            set_error("JPEG header parse failed");
            return EPUB_NATIVE_ERROR_PARSE;
        }
        return EPUB_NATIVE_SUCCESS;
    }
#endif

#ifdef EPUB4J_NATIVE_HAS_PNG
    if (is_png(data, data_length)) {
        // PNG IHDR is at fixed offset: bytes 16-19 = width, 20-23 = height (big-endian)
        if (data_length >= 24) {
            *out_width  = (int)((data[16] << 24) | (data[17] << 16) | (data[18] << 8) | data[19]);
            *out_height = (int)((data[20] << 24) | (data[21] << 16) | (data[22] << 8) | data[23]);
            *out_format = EPUB_NATIVE_IMAGE_FORMAT_PNG;
            return EPUB_NATIVE_SUCCESS;
        }
        set_error("PNG data too short for IHDR");
        return EPUB_NATIVE_ERROR_PARSE;
    }
#endif

#ifdef EPUB4J_NATIVE_HAS_WEBP
    if (is_webp(data, data_length)) {
        int w = 0, h = 0;
        if (WebPGetInfo(data, data_length, &w, &h)) {
            *out_width  = w;
            *out_height = h;
            *out_format = EPUB_NATIVE_IMAGE_FORMAT_WEBP;
            return EPUB_NATIVE_SUCCESS;
        }
        set_error("WebP header parse failed");
        return EPUB_NATIVE_ERROR_PARSE;
    }
#endif

    set_error("unrecognized image format");
    return EPUB_NATIVE_ERROR_PARSE;
}

// ---------------------------------------------------------------------------
// JPEG lossless optimization (coefficient-based, no generation loss)
// ---------------------------------------------------------------------------

#ifdef EPUB4J_NATIVE_HAS_JPEG

// Custom error manager that longjmps instead of calling exit()
struct jpeg_error_jmp {
    struct jpeg_error_mgr pub;
    jmp_buf jmp;
    char msg[JMSG_LENGTH_MAX];
};

static void jpeg_error_exit_handler(j_common_ptr cinfo) {
    auto* err = reinterpret_cast<jpeg_error_jmp*>(cinfo->err);
    (*cinfo->err->format_message)(cinfo, err->msg);
    longjmp(err->jmp, 1);
}

// libjpeg-turbo provides jpeg_mem_src and jpeg_mem_dest in-library;
// no custom memory managers needed.

#endif // EPUB4J_NATIVE_HAS_JPEG

extern "C" EPUB_NATIVE_API EpubNativeError epub_native_jpeg_optimize(
    const uint8_t* data, size_t data_length,
    uint8_t** out_data, size_t* out_length)
{
#ifndef EPUB4J_NATIVE_HAS_JPEG
    set_error("JPEG support not compiled in");
    return EPUB_NATIVE_ERROR_INVALID_ARG;
#else
    if (!data || data_length == 0 || !out_data || !out_length) {
        set_error("null argument in jpeg_optimize");
        return EPUB_NATIVE_ERROR_INVALID_ARG;
    }

    struct jpeg_decompress_struct srcinfo;
    struct jpeg_compress_struct   dstinfo;
    jpeg_error_jmp src_err{}, dst_err{};

    srcinfo.err = jpeg_std_error(&src_err.pub);
    src_err.pub.error_exit = jpeg_error_exit_handler;
    dstinfo.err = jpeg_std_error(&dst_err.pub);
    dst_err.pub.error_exit = jpeg_error_exit_handler;

    if (setjmp(src_err.jmp)) {
        set_error("JPEG decompress error: %s", src_err.msg);
        jpeg_destroy_decompress(&srcinfo);
        return EPUB_NATIVE_ERROR_PARSE;
    }
    if (setjmp(dst_err.jmp)) {
        set_error("JPEG compress error: %s", dst_err.msg);
        jpeg_destroy_compress(&dstinfo);
        jpeg_destroy_decompress(&srcinfo);
        return EPUB_NATIVE_ERROR_PARSE;
    }

    jpeg_create_decompress(&srcinfo);
    jpeg_mem_src(&srcinfo, data, data_length);

    // Save all markers so they are copied to the output
    jpeg_save_markers(&srcinfo, JPEG_COM, 0xFFFF);
    for (int m = 0; m < 16; m++) {
        jpeg_save_markers(&srcinfo, JPEG_APP0 + m, 0xFFFF);
    }

    jpeg_read_header(&srcinfo, TRUE);

    // Read DCT coefficients (no pixel decode)
    jvirt_barray_ptr* coef_arrays = jpeg_read_coefficients(&srcinfo);

    jpeg_create_compress(&dstinfo);

    unsigned char* outbuf = nullptr;
    unsigned long outsize = 0;
    jpeg_mem_dest(&dstinfo, &outbuf, &outsize);

    jpeg_copy_critical_parameters(&srcinfo, &dstinfo);

    // Enable progressive encoding and optimized Huffman tables
    jpeg_simple_progression(&dstinfo);
    dstinfo.optimize_coding = TRUE;

    // Write coefficients (lossless: no pixel round-trip)
    jpeg_write_coefficients(&dstinfo, coef_arrays);

    // Copy markers
    for (jpeg_saved_marker_ptr marker = srcinfo.marker_list;
         marker != nullptr; marker = marker->next) {
        jpeg_write_marker(&dstinfo, marker->marker,
                          marker->data, marker->data_length);
    }

    jpeg_finish_compress(&dstinfo);
    jpeg_finish_decompress(&srcinfo);

    jpeg_destroy_compress(&dstinfo);
    jpeg_destroy_decompress(&srcinfo);

    *out_length = static_cast<size_t>(outsize);
    *out_data = outbuf;
    return EPUB_NATIVE_SUCCESS;
#endif
}

// ---------------------------------------------------------------------------
// JPEG lossy re-compression via TurboJPEG
// ---------------------------------------------------------------------------

extern "C" EPUB_NATIVE_API EpubNativeError epub_native_jpeg_compress(
    const uint8_t* data, size_t data_length,
    int quality, int progressive,
    uint8_t** out_data, size_t* out_length)
{
#ifndef EPUB4J_NATIVE_HAS_JPEG
    set_error("JPEG support not compiled in");
    return EPUB_NATIVE_ERROR_INVALID_ARG;
#else
    if (!data || data_length == 0 || !out_data || !out_length) {
        set_error("null argument in jpeg_compress");
        return EPUB_NATIVE_ERROR_INVALID_ARG;
    }
    if (quality < 1 || quality > 100) {
        set_error("quality must be 1-100");
        return EPUB_NATIVE_ERROR_INVALID_ARG;
    }

    // Decode input to pixels (handles JPEG input; for PNG/WebP callers should
    // use the resize path which decodes any format)
    tjhandle dec = tj3Init(TJINIT_DECOMPRESS);
    if (!dec) {
        set_error("TurboJPEG decompressor init failed");
        return EPUB_NATIVE_ERROR_MEMORY;
    }

    int err = tj3DecompressHeader(dec, data, data_length);
    if (err != 0) {
        const char* tjmsg = tj3GetErrorStr(dec);
        set_error("JPEG header error: %s", tjmsg ? tjmsg : "unknown");
        tj3Destroy(dec);
        return EPUB_NATIVE_ERROR_PARSE;
    }

    int width  = tj3Get(dec, TJPARAM_JPEGWIDTH);
    int height = tj3Get(dec, TJPARAM_JPEGHEIGHT);
    int pixel_size = tjPixelSize[TJPF_RGB];
    size_t buf_size = static_cast<size_t>(width) * static_cast<size_t>(height)
                      * static_cast<size_t>(pixel_size);

    auto* pixels = static_cast<uint8_t*>(malloc(buf_size));
    if (!pixels) {
        tj3Destroy(dec);
        set_error("malloc failed for pixel buffer");
        return EPUB_NATIVE_ERROR_MEMORY;
    }

    tj3Set(dec, TJPARAM_COLORSPACE, TJCS_RGB);
    err = tj3Decompress8(dec, data, data_length, pixels, width * pixel_size, TJPF_RGB);
    tj3Destroy(dec);

    if (err != 0) {
        free(pixels);
        set_error("JPEG decompression failed");
        return EPUB_NATIVE_ERROR_PARSE;
    }

    // Re-encode at target quality
    tjhandle enc = tj3Init(TJINIT_COMPRESS);
    if (!enc) {
        free(pixels);
        set_error("TurboJPEG compressor init failed");
        return EPUB_NATIVE_ERROR_MEMORY;
    }

    tj3Set(enc, TJPARAM_QUALITY, quality);
    tj3Set(enc, TJPARAM_SUBSAMP, TJSAMP_420);
    if (progressive) {
        tj3Set(enc, TJPARAM_PROGRESSIVE, 1);
    }
    tj3Set(enc, TJPARAM_OPTIMIZE, 1);

    uint8_t* jpeg_buf = nullptr;
    size_t jpeg_size = 0;
    err = tj3Compress8(enc, pixels, width, width * pixel_size, height, TJPF_RGB,
                       &jpeg_buf, &jpeg_size);
    free(pixels);
    tj3Destroy(enc);

    if (err != 0 || !jpeg_buf) {
        if (jpeg_buf) tj3Free(jpeg_buf);
        set_error("JPEG compression failed");
        return EPUB_NATIVE_ERROR_PARSE;
    }

    // Copy to malloc'd buffer so Java can free with epub_native_image_data_free
    *out_data = static_cast<uint8_t*>(malloc(jpeg_size));
    if (!*out_data) {
        tj3Free(jpeg_buf);
        set_error("malloc failed for output");
        return EPUB_NATIVE_ERROR_MEMORY;
    }
    memcpy(*out_data, jpeg_buf, jpeg_size);
    *out_length = jpeg_size;
    tj3Free(jpeg_buf);
    return EPUB_NATIVE_SUCCESS;
#endif
}

// ---------------------------------------------------------------------------
// PNG optimization
// ---------------------------------------------------------------------------

#ifdef EPUB4J_NATIVE_HAS_PNG

struct png_read_state {
    const uint8_t* data;
    size_t length;
    size_t offset;
};

static void png_read_from_memory(png_structp png, png_bytep out, png_size_t count) {
    auto* state = static_cast<png_read_state*>(png_get_io_ptr(png));
    size_t avail = state->length - state->offset;
    if (count > avail) count = avail;
    memcpy(out, state->data + state->offset, count);
    state->offset += count;
}

struct png_write_state {
    std::vector<uint8_t>* buffer;
};

static void png_write_to_memory(png_structp png, png_bytep data, png_size_t length) {
    auto* state = static_cast<png_write_state*>(png_get_io_ptr(png));
    state->buffer->insert(state->buffer->end(), data, data + length);
}

static void png_flush_memory(png_structp) {}

#endif // EPUB4J_NATIVE_HAS_PNG

extern "C" EPUB_NATIVE_API EpubNativeError epub_native_png_optimize(
    const uint8_t* data, size_t data_length,
    int strip_ancillary,
    uint8_t** out_data, size_t* out_length)
{
#ifndef EPUB4J_NATIVE_HAS_PNG
    set_error("PNG support not compiled in");
    return EPUB_NATIVE_ERROR_INVALID_ARG;
#else
    if (!data || data_length == 0 || !out_data || !out_length) {
        set_error("null argument in png_optimize");
        return EPUB_NATIVE_ERROR_INVALID_ARG;
    }

    // Read
    png_structp read_png = png_create_read_struct(PNG_LIBPNG_VER_STRING,
                                                  nullptr, nullptr, nullptr);
    if (!read_png) {
        set_error("png_create_read_struct failed");
        return EPUB_NATIVE_ERROR_MEMORY;
    }

    png_infop read_info = png_create_info_struct(read_png);
    if (!read_info) {
        png_destroy_read_struct(&read_png, nullptr, nullptr);
        set_error("png_create_info_struct failed");
        return EPUB_NATIVE_ERROR_MEMORY;
    }

    if (setjmp(png_jmpbuf(read_png))) {
        png_destroy_read_struct(&read_png, &read_info, nullptr);
        set_error("PNG read error");
        return EPUB_NATIVE_ERROR_PARSE;
    }

    png_read_state rstate = {data, data_length, 0};
    png_set_read_fn(read_png, &rstate, png_read_from_memory);

    // Read all image data
    int transforms = PNG_TRANSFORM_IDENTITY;
    if (strip_ancillary) {
        transforms |= PNG_TRANSFORM_STRIP_FILLER_AFTER;
    }
    png_read_png(read_png, read_info, transforms, nullptr);

    png_uint_32 width = png_get_image_width(read_png, read_info);
    png_uint_32 height = png_get_image_height(read_png, read_info);
    int bit_depth = png_get_bit_depth(read_png, read_info);
    int color_type = png_get_color_type(read_png, read_info);
    int interlace = png_get_interlace_type(read_png, read_info);
    png_bytepp rows = png_get_rows(read_png, read_info);

    // Write with higher compression
    png_structp write_png = png_create_write_struct(PNG_LIBPNG_VER_STRING,
                                                    nullptr, nullptr, nullptr);
    if (!write_png) {
        png_destroy_read_struct(&read_png, &read_info, nullptr);
        set_error("png_create_write_struct failed");
        return EPUB_NATIVE_ERROR_MEMORY;
    }

    png_infop write_info = png_create_info_struct(write_png);
    if (!write_info) {
        png_destroy_write_struct(&write_png, nullptr);
        png_destroy_read_struct(&read_png, &read_info, nullptr);
        set_error("png_create_info_struct (write) failed");
        return EPUB_NATIVE_ERROR_MEMORY;
    }

    if (setjmp(png_jmpbuf(write_png))) {
        png_destroy_write_struct(&write_png, &write_info);
        png_destroy_read_struct(&read_png, &read_info, nullptr);
        set_error("PNG write error");
        return EPUB_NATIVE_ERROR_PARSE;
    }

    std::vector<uint8_t> outbuf;
    outbuf.reserve(data_length);
    png_write_state wstate = {&outbuf};
    png_set_write_fn(write_png, &wstate, png_write_to_memory, png_flush_memory);

    png_set_IHDR(write_png, write_info, width, height, bit_depth, color_type,
                 interlace, PNG_COMPRESSION_TYPE_DEFAULT, PNG_FILTER_TYPE_DEFAULT);

    // Copy palette if present
    if (color_type == PNG_COLOR_TYPE_PALETTE) {
        png_colorp palette;
        int num_palette;
        if (png_get_PLTE(read_png, read_info, &palette, &num_palette)) {
            png_set_PLTE(write_png, write_info, palette, num_palette);
        }
    }

    // Copy transparency if present and not stripping
    if (!strip_ancillary) {
        png_bytep trans_alpha;
        int num_trans;
        png_color_16p trans_color;
        if (png_get_tRNS(read_png, read_info, &trans_alpha, &num_trans, &trans_color)) {
            png_set_tRNS(write_png, write_info, trans_alpha, num_trans, trans_color);
        }
    }

    // Maximum compression
    png_set_compression_level(write_png, 9);
    png_set_compression_strategy(write_png, PNG_Z_DEFAULT_STRATEGY);

    // Write
    png_set_rows(write_png, write_info, rows);
    png_write_png(write_png, write_info, PNG_TRANSFORM_IDENTITY, nullptr);

    png_destroy_write_struct(&write_png, &write_info);
    png_destroy_read_struct(&read_png, &read_info, nullptr);

    *out_length = outbuf.size();
    *out_data = static_cast<uint8_t*>(malloc(outbuf.size()));
    if (!*out_data) {
        set_error("malloc failed for PNG output");
        return EPUB_NATIVE_ERROR_MEMORY;
    }
    memcpy(*out_data, outbuf.data(), outbuf.size());
    return EPUB_NATIVE_SUCCESS;
#endif
}

// ---------------------------------------------------------------------------
// WebP encoding
// ---------------------------------------------------------------------------

extern "C" EPUB_NATIVE_API EpubNativeError epub_native_webp_encode(
    const uint8_t* rgba_pixels, int width, int height, int stride,
    int quality,
    uint8_t** out_data, size_t* out_length)
{
#ifndef EPUB4J_NATIVE_HAS_WEBP
    set_error("WebP support not compiled in");
    return EPUB_NATIVE_ERROR_INVALID_ARG;
#else
    if (!rgba_pixels || !out_data || !out_length || width <= 0 || height <= 0) {
        set_error("invalid argument in webp_encode");
        return EPUB_NATIVE_ERROR_INVALID_ARG;
    }

    uint8_t* output = nullptr;
    size_t output_size = 0;

    if (quality < 0) {
        // Lossless
        output_size = WebPEncodeLosslessRGBA(rgba_pixels, width, height, stride, &output);
    } else {
        output_size = WebPEncodeRGBA(rgba_pixels, width, height, stride,
                                     static_cast<float>(quality), &output);
    }

    if (output_size == 0 || !output) {
        if (output) WebPFree(output);
        set_error("WebP encoding failed");
        return EPUB_NATIVE_ERROR_PARSE;
    }

    // Copy to malloc'd buffer for consistent free
    *out_data = static_cast<uint8_t*>(malloc(output_size));
    if (!*out_data) {
        WebPFree(output);
        set_error("malloc failed for WebP output");
        return EPUB_NATIVE_ERROR_MEMORY;
    }
    memcpy(*out_data, output, output_size);
    *out_length = output_size;
    WebPFree(output);
    return EPUB_NATIVE_SUCCESS;
#endif
}

// ---------------------------------------------------------------------------
// Decoding helpers (shared by resize and compress paths)
// ---------------------------------------------------------------------------

struct decoded_image {
    uint8_t* pixels;     // RGBA, 4 bytes per pixel
    int width;
    int height;
    int channels;        // always 4 (RGBA)
    bool needs_free;     // true if pixels was malloc'd (vs WebP internal)
    bool webp_allocated; // true if WebPFree should be used
};

static EpubNativeError decode_image(
    const uint8_t* data, size_t data_length, decoded_image* out)
{
    out->pixels = nullptr;
    out->needs_free = false;
    out->webp_allocated = false;
    out->channels = 4;

#ifdef EPUB4J_NATIVE_HAS_JPEG
    if (is_jpeg(data, data_length)) {
        tjhandle dec = tj3Init(TJINIT_DECOMPRESS);
        if (!dec) {
            set_error("TurboJPEG init failed");
            return EPUB_NATIVE_ERROR_MEMORY;
        }

        if (tj3DecompressHeader(dec, data, data_length) != 0) {
            tj3Destroy(dec);
            set_error("JPEG header read failed");
            return EPUB_NATIVE_ERROR_PARSE;
        }

        out->width  = tj3Get(dec, TJPARAM_JPEGWIDTH);
        out->height = tj3Get(dec, TJPARAM_JPEGHEIGHT);
        size_t buf_size = static_cast<size_t>(out->width) * static_cast<size_t>(out->height) * 4;
        out->pixels = static_cast<uint8_t*>(malloc(buf_size));
        if (!out->pixels) {
            tj3Destroy(dec);
            set_error("malloc failed for JPEG decode");
            return EPUB_NATIVE_ERROR_MEMORY;
        }

        if (tj3Decompress8(dec, data, data_length, out->pixels,
                           out->width * 4, TJPF_RGBA) != 0) {
            free(out->pixels);
            out->pixels = nullptr;
            tj3Destroy(dec);
            set_error("JPEG decompression failed");
            return EPUB_NATIVE_ERROR_PARSE;
        }

        tj3Destroy(dec);
        out->needs_free = true;
        return EPUB_NATIVE_SUCCESS;
    }
#endif

#ifdef EPUB4J_NATIVE_HAS_PNG
    if (is_png(data, data_length)) {
        png_structp png = png_create_read_struct(PNG_LIBPNG_VER_STRING,
                                                 nullptr, nullptr, nullptr);
        if (!png) {
            set_error("png_create_read_struct failed");
            return EPUB_NATIVE_ERROR_MEMORY;
        }

        png_infop info = png_create_info_struct(png);
        if (!info) {
            png_destroy_read_struct(&png, nullptr, nullptr);
            set_error("png_create_info_struct failed");
            return EPUB_NATIVE_ERROR_MEMORY;
        }

        if (setjmp(png_jmpbuf(png))) {
            png_destroy_read_struct(&png, &info, nullptr);
            set_error("PNG decode error");
            return EPUB_NATIVE_ERROR_PARSE;
        }

        png_read_state rstate = {data, data_length, 0};
        png_set_read_fn(png, &rstate, png_read_from_memory);

        png_read_info(png, info);

        png_uint_32 w = png_get_image_width(png, info);
        png_uint_32 h = png_get_image_height(png, info);
        int bit_depth = png_get_bit_depth(png, info);
        int color_type = png_get_color_type(png, info);

        // Normalize to RGBA 8-bit
        if (bit_depth == 16) png_set_strip_16(png);
        if (color_type == PNG_COLOR_TYPE_PALETTE) png_set_palette_to_rgb(png);
        if (color_type == PNG_COLOR_TYPE_GRAY && bit_depth < 8) png_set_expand_gray_1_2_4_to_8(png);
        if (png_get_valid(png, info, PNG_INFO_tRNS)) png_set_tRNS_to_alpha(png);
        if (color_type == PNG_COLOR_TYPE_RGB ||
            color_type == PNG_COLOR_TYPE_GRAY ||
            color_type == PNG_COLOR_TYPE_PALETTE) {
            png_set_filler(png, 0xFF, PNG_FILLER_AFTER);
        }
        if (color_type == PNG_COLOR_TYPE_GRAY ||
            color_type == PNG_COLOR_TYPE_GRAY_ALPHA) {
            png_set_gray_to_rgb(png);
        }

        png_read_update_info(png, info);

        out->width  = static_cast<int>(w);
        out->height = static_cast<int>(h);
        size_t row_bytes = png_get_rowbytes(png, info);
        size_t buf_size = row_bytes * h;

        out->pixels = static_cast<uint8_t*>(malloc(buf_size));
        if (!out->pixels) {
            png_destroy_read_struct(&png, &info, nullptr);
            set_error("malloc failed for PNG decode");
            return EPUB_NATIVE_ERROR_MEMORY;
        }

        std::vector<png_bytep> row_ptrs(h);
        for (png_uint_32 y = 0; y < h; y++) {
            row_ptrs[y] = out->pixels + y * row_bytes;
        }
        png_read_image(png, row_ptrs.data());

        png_destroy_read_struct(&png, &info, nullptr);
        out->needs_free = true;
        return EPUB_NATIVE_SUCCESS;
    }
#endif

#ifdef EPUB4J_NATIVE_HAS_WEBP
    if (is_webp(data, data_length)) {
        int w = 0, h = 0;
        uint8_t* rgba = WebPDecodeRGBA(data, data_length, &w, &h);
        if (!rgba) {
            set_error("WebP decode failed");
            return EPUB_NATIVE_ERROR_PARSE;
        }
        out->pixels = rgba;
        out->width  = w;
        out->height = h;
        out->needs_free = false;
        out->webp_allocated = true;
        return EPUB_NATIVE_SUCCESS;
    }
#endif

    set_error("unrecognized image format for decode");
    return EPUB_NATIVE_ERROR_PARSE;
}

static void free_decoded_image(decoded_image* img) {
    if (!img->pixels) return;
#ifdef EPUB4J_NATIVE_HAS_WEBP
    if (img->webp_allocated) {
        WebPFree(img->pixels);
        img->pixels = nullptr;
        return;
    }
#endif
    if (img->needs_free) {
        free(img->pixels);
    }
    img->pixels = nullptr;
}

// ---------------------------------------------------------------------------
// Encode RGBA pixels to a target format
// ---------------------------------------------------------------------------

static EpubNativeError encode_pixels(
    const uint8_t* rgba, int width, int height,
    int format, int quality,
    uint8_t** out_data, size_t* out_length)
{
    switch (format) {
#ifdef EPUB4J_NATIVE_HAS_JPEG
    case EPUB_NATIVE_IMAGE_FORMAT_JPEG: {
        tjhandle enc = tj3Init(TJINIT_COMPRESS);
        if (!enc) {
            set_error("TurboJPEG compressor init failed");
            return EPUB_NATIVE_ERROR_MEMORY;
        }

        tj3Set(enc, TJPARAM_QUALITY, quality > 0 ? quality : 85);
        tj3Set(enc, TJPARAM_SUBSAMP, TJSAMP_420);
        tj3Set(enc, TJPARAM_PROGRESSIVE, 1);
        tj3Set(enc, TJPARAM_OPTIMIZE, 1);

        uint8_t* jpeg_buf = nullptr;
        size_t jpeg_size = 0;
        int err = tj3Compress8(enc, rgba, width, width * 4, height, TJPF_RGBA,
                               &jpeg_buf, &jpeg_size);
        tj3Destroy(enc);
        if (err != 0 || !jpeg_buf) {
            if (jpeg_buf) tj3Free(jpeg_buf);
            set_error("JPEG re-encoding failed");
            return EPUB_NATIVE_ERROR_PARSE;
        }

        *out_data = static_cast<uint8_t*>(malloc(jpeg_size));
        if (!*out_data) {
            tj3Free(jpeg_buf);
            return EPUB_NATIVE_ERROR_MEMORY;
        }
        memcpy(*out_data, jpeg_buf, jpeg_size);
        *out_length = jpeg_size;
        tj3Free(jpeg_buf);
        return EPUB_NATIVE_SUCCESS;
    }
#endif

#ifdef EPUB4J_NATIVE_HAS_PNG
    case EPUB_NATIVE_IMAGE_FORMAT_PNG: {
        png_structp png = png_create_write_struct(PNG_LIBPNG_VER_STRING,
                                                  nullptr, nullptr, nullptr);
        if (!png) {
            set_error("png_create_write_struct failed");
            return EPUB_NATIVE_ERROR_MEMORY;
        }
        png_infop info = png_create_info_struct(png);
        if (!info) {
            png_destroy_write_struct(&png, nullptr);
            set_error("png_create_info_struct failed");
            return EPUB_NATIVE_ERROR_MEMORY;
        }
        if (setjmp(png_jmpbuf(png))) {
            png_destroy_write_struct(&png, &info);
            set_error("PNG write error");
            return EPUB_NATIVE_ERROR_PARSE;
        }

        std::vector<uint8_t> outbuf;
        outbuf.reserve(static_cast<size_t>(width) * static_cast<size_t>(height) * 4);
        png_write_state wstate = {&outbuf};
        png_set_write_fn(png, &wstate, png_write_to_memory, png_flush_memory);

        png_set_IHDR(png, info,
                     static_cast<png_uint_32>(width),
                     static_cast<png_uint_32>(height),
                     8, PNG_COLOR_TYPE_RGBA,
                     PNG_INTERLACE_NONE,
                     PNG_COMPRESSION_TYPE_DEFAULT,
                     PNG_FILTER_TYPE_DEFAULT);
        png_set_compression_level(png, 9);

        png_write_info(png, info);
        for (int y = 0; y < height; y++) {
            png_write_row(png, rgba + static_cast<size_t>(y) * static_cast<size_t>(width) * 4);
        }
        png_write_end(png, nullptr);
        png_destroy_write_struct(&png, &info);

        *out_data = static_cast<uint8_t*>(malloc(outbuf.size()));
        if (!*out_data) {
            set_error("malloc failed for PNG output");
            return EPUB_NATIVE_ERROR_MEMORY;
        }
        memcpy(*out_data, outbuf.data(), outbuf.size());
        *out_length = outbuf.size();
        return EPUB_NATIVE_SUCCESS;
    }
#endif

#ifdef EPUB4J_NATIVE_HAS_WEBP
    case EPUB_NATIVE_IMAGE_FORMAT_WEBP:
        return epub_native_webp_encode(rgba, width, height, width * 4, quality, out_data, out_length);
#endif

    default:
        set_error("unsupported output format %d", format);
        return EPUB_NATIVE_ERROR_INVALID_ARG;
    }
}

// ---------------------------------------------------------------------------
// Image resize
// ---------------------------------------------------------------------------

extern "C" EPUB_NATIVE_API EpubNativeError epub_native_image_resize(
    const uint8_t* data, size_t data_length,
    int target_width, int target_height,
    int output_format, int quality,
    uint8_t** out_data, size_t* out_length)
{
    if (!data || data_length == 0 || !out_data || !out_length) {
        set_error("null argument in image_resize");
        return EPUB_NATIVE_ERROR_INVALID_ARG;
    }
    if (target_width <= 0 || target_height <= 0) {
        set_error("target dimensions must be positive");
        return EPUB_NATIVE_ERROR_INVALID_ARG;
    }

    // Decode input to RGBA pixels
    decoded_image img{};
    EpubNativeError err = decode_image(data, data_length, &img);
    if (err != EPUB_NATIVE_SUCCESS) {
        return err;
    }

    // Resize using stb_image_resize2 (Mitchell for downsample, Catmull-Rom for upsample)
    size_t dst_size = static_cast<size_t>(target_width) * static_cast<size_t>(target_height) * 4;
    auto* dst_pixels = static_cast<uint8_t*>(malloc(dst_size));
    if (!dst_pixels) {
        free_decoded_image(&img);
        set_error("malloc failed for resize buffer");
        return EPUB_NATIVE_ERROR_MEMORY;
    }

    stbir_resize_uint8_linear(
        img.pixels, img.width, img.height, img.width * 4,
        dst_pixels, target_width, target_height, target_width * 4,
        STBIR_RGBA);

    free_decoded_image(&img);

    // Encode to target format
    err = encode_pixels(dst_pixels, target_width, target_height,
                        output_format, quality, out_data, out_length);
    free(dst_pixels);
    return err;
}

// ---------------------------------------------------------------------------
// Free
// ---------------------------------------------------------------------------

extern "C" EPUB_NATIVE_API void epub_native_image_data_free(uint8_t* data) {
    free(data);
}
