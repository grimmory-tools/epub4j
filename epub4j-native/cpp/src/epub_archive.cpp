/**
 * epub_archive.cpp - Archive (ZIP/EPUB) Handling Implementation
 *
 * Uses libarchive for robust ZIP/EPUB file handling with better
 * error recovery than Java's ZipInputStream.
 * All dependencies are statically linked via FetchContent.
 */

#include "epub_native.h"
#include "epub_native_internal.h"

#include <archive.h>
#include <archive_entry.h>

#include <vector>
#include <string>
#include <cstring>

// ============================================================================
// Internal Structures
// ============================================================================

struct EpubNativeArchive {
    struct archive* archive;
    std::vector<char> buffer; // For memory archives
    std::string filepath;
    std::string last_error;
};

static bool reopen_archive(EpubNativeArchive* archive_wrapper, std::string& error_message) {
    if (!archive_wrapper) {
        error_message = "Invalid archive wrapper";
        return false;
    }

    if (archive_wrapper->archive) {
        archive_read_free(archive_wrapper->archive);
        archive_wrapper->archive = nullptr;
    }

    archive_wrapper->archive = archive_read_new();
    if (!archive_wrapper->archive) {
        error_message = "Failed to create archive handle";
        return false;
    }

    archive_read_support_format_all(archive_wrapper->archive);
    archive_read_support_filter_all(archive_wrapper->archive);

    int r;
    if (!archive_wrapper->filepath.empty()) {
        r = archive_read_open_filename(archive_wrapper->archive, archive_wrapper->filepath.c_str(), 10240);
    } else {
        r = archive_read_open_memory(
            archive_wrapper->archive,
            archive_wrapper->buffer.data(),
            archive_wrapper->buffer.size()
        );
    }

    if (r != ARCHIVE_OK) {
        const char* err = archive_error_string(archive_wrapper->archive);
        error_message = err ? err : "Unknown libarchive open error";
        archive_read_free(archive_wrapper->archive);
        archive_wrapper->archive = nullptr;
        return false;
    }

    return true;
}

// ============================================================================
// Archive Implementation
// ============================================================================

EPUB_NATIVE_API EpubNativeError epub_native_archive_open(
    const char* filepath,
    EpubNativeArchive** out_archive
) {
    if (!filepath || !out_archive) {
        set_error("Invalid argument: null pointer");
        return EPUB_NATIVE_ERROR_INVALID_ARG;
    }

    try {
        auto archive_wrapper = new EpubNativeArchive();
        archive_wrapper->filepath = filepath;

        archive_wrapper->archive = archive_read_new();
        if (!archive_wrapper->archive) {
            delete archive_wrapper;
            set_error("Failed to create archive handle");
            return EPUB_NATIVE_ERROR_MEMORY;
        }

        // Enable all formats (ZIP, TAR, etc.)
        archive_read_support_format_all(archive_wrapper->archive);
        
        // Enable all filters (gzip, bzip2, xz, etc.)
        archive_read_support_filter_all(archive_wrapper->archive);

        // Open the file
        int r = archive_read_open_filename(
            archive_wrapper->archive,
            filepath,
            10240 // 10K block size
        );

        if (r != ARCHIVE_OK) {
            std::string err = "Failed to open archive: ";
            err += filepath;
            err += " - ";
            const char* archive_err = archive_error_string(archive_wrapper->archive);
            err += archive_err ? archive_err : "unknown error";
            archive_read_free(archive_wrapper->archive);
            delete archive_wrapper;
            set_error(err.c_str());
            return EPUB_NATIVE_ERROR_IO;
        }

        *out_archive = archive_wrapper;
        return EPUB_NATIVE_SUCCESS;

    } catch (const std::bad_alloc&) {
        set_error("Memory allocation failed");
        return EPUB_NATIVE_ERROR_MEMORY;
    } catch (...) {
        set_error("Unknown error opening archive");
        return EPUB_NATIVE_ERROR_IO;
    }
}

EPUB_NATIVE_API EpubNativeError epub_native_archive_open_memory(
    const char* data,
    size_t data_length,
    EpubNativeArchive** out_archive
) {
    if (!data || !out_archive) {
        set_error("Invalid argument: null pointer");
        return EPUB_NATIVE_ERROR_INVALID_ARG;
    }

    try {
        auto archive_wrapper = new EpubNativeArchive();
        archive_wrapper->buffer.assign(data, data + data_length);

        archive_wrapper->archive = archive_read_new();
        if (!archive_wrapper->archive) {
            delete archive_wrapper;
            set_error("Failed to create archive handle");
            return EPUB_NATIVE_ERROR_MEMORY;
        }

        // Enable all formats and filters
        archive_read_support_format_all(archive_wrapper->archive);
        archive_read_support_filter_all(archive_wrapper->archive);

        // Open from memory buffer
        int r = archive_read_open_memory(
            archive_wrapper->archive,
            archive_wrapper->buffer.data(),
            archive_wrapper->buffer.size()
        );

        if (r != ARCHIVE_OK) {
            archive_read_free(archive_wrapper->archive);
            delete archive_wrapper;
            set_error("Failed to open memory archive");
            return EPUB_NATIVE_ERROR_IO;
        }

        *out_archive = archive_wrapper;
        return EPUB_NATIVE_SUCCESS;

    } catch (const std::bad_alloc&) {
        set_error("Memory allocation failed");
        return EPUB_NATIVE_ERROR_MEMORY;
    } catch (...) {
        set_error("Unknown error opening memory archive");
        return EPUB_NATIVE_ERROR_IO;
    }
}

EPUB_NATIVE_API void epub_native_archive_free(EpubNativeArchive* archive_wrapper) {
    if (archive_wrapper) {
        if (archive_wrapper->archive) {
            archive_read_free(archive_wrapper->archive);
        }
        delete archive_wrapper;
    }
}

EPUB_NATIVE_API EpubNativeError epub_native_archive_list_entries(
    EpubNativeArchive* archive_wrapper,
    char*** out_entries,
    size_t* out_count
) {
    if (!archive_wrapper || !out_entries || !out_count) {
        set_error("Invalid argument: null pointer");
        return EPUB_NATIVE_ERROR_INVALID_ARG;
    }

    try {
        std::vector<std::string> entries;

        std::string reopen_error;
        if (!reopen_archive(archive_wrapper, reopen_error)) {
            set_error(reopen_error.c_str());
            return EPUB_NATIVE_ERROR_IO;
        }

        struct archive_entry* entry;
        int r;
        while ((r = archive_read_next_header(archive_wrapper->archive, &entry)) == ARCHIVE_OK) {
            const char* pathname = archive_entry_pathname(entry);
            if (pathname) {
                entries.push_back(pathname);
            }
            archive_read_data_skip(archive_wrapper->archive);
        }

        if (r != ARCHIVE_EOF && r != ARCHIVE_OK) {
            set_error(archive_error_string(archive_wrapper->archive));
            return EPUB_NATIVE_ERROR_IO;
        }

        // Allocate output array
        size_t count = entries.size();
        if (count == 0) {
            *out_entries = nullptr;
            *out_count = 0;
            return EPUB_NATIVE_SUCCESS;
        }

        char** result = static_cast<char**>(malloc(count * sizeof(char*)));
        if (!result) {
            set_error("Memory allocation failed");
            return EPUB_NATIVE_ERROR_MEMORY;
        }

        for (size_t i = 0; i < count; i++) {
            result[i] = duplicate_cstring(entries[i].c_str());
            if (!result[i]) {
                // Cleanup on failure
                for (size_t j = 0; j < i; j++) {
                    free(result[j]);
                }
                free(result);
                set_error("Memory allocation failed");
                return EPUB_NATIVE_ERROR_MEMORY;
            }
        }

        *out_entries = result;
        *out_count = count;
        return EPUB_NATIVE_SUCCESS;

    } catch (const std::bad_alloc&) {
        set_error("Memory allocation failed");
        return EPUB_NATIVE_ERROR_MEMORY;
    } catch (...) {
        set_error("Unknown error listing archive entries");
        return EPUB_NATIVE_ERROR_IO;
    }
}

EPUB_NATIVE_API void epub_native_archive_free_string_array(
    char** entries,
    size_t count
) {
    if (entries) {
        for (size_t i = 0; i < count; i++) {
            free(entries[i]);
        }
        free(entries);
    }
}

EPUB_NATIVE_API EpubNativeError epub_native_archive_read_entry(
    EpubNativeArchive* archive_wrapper,
    const char* entry_path,
    char** out_data,
    size_t* out_data_length
) {
    if (!archive_wrapper || !entry_path || !out_data || !out_data_length) {
        set_error("Invalid argument: null pointer");
        return EPUB_NATIVE_ERROR_INVALID_ARG;
    }

    try {
        std::string reopen_error;
        if (!reopen_archive(archive_wrapper, reopen_error)) {
            set_error(reopen_error.c_str());
            return EPUB_NATIVE_ERROR_IO;
        }

        struct archive_entry* entry;
        int r;
        bool found = false;

        while ((r = archive_read_next_header(archive_wrapper->archive, &entry)) == ARCHIVE_OK) {
            const char* pathname = archive_entry_pathname(entry);
            if (pathname && strcmp(pathname, entry_path) == 0) {
                found = true;
                break;
            }
            archive_read_data_skip(archive_wrapper->archive);
        }

        if (r != ARCHIVE_OK || !found) {
            set_error("Entry not found in archive");
            return EPUB_NATIVE_ERROR_NOT_FOUND;
        }

        // Read the entry data
        std::vector<char> buffer;
        const size_t initial_size = 8192;
        buffer.resize(initial_size);

        size_t total_read = 0;
        const void* buff;
        size_t size;
        la_int64_t offset;

        while ((r = archive_read_data_block(
                archive_wrapper->archive, &buff, &size, &offset)) == ARCHIVE_OK) {
            
            // Expand buffer if needed
            if (total_read + size > buffer.size()) {
                buffer.resize(std::max(buffer.size() * 2, total_read + size));
            }
            memcpy(buffer.data() + total_read, buff, size);
            total_read += size;
        }

        if (r != ARCHIVE_EOF) {
            set_error(archive_error_string(archive_wrapper->archive));
            return EPUB_NATIVE_ERROR_IO;
        }

        // Allocate and copy data
        *out_data = static_cast<char*>(malloc(total_read + 1));
        if (!*out_data) {
            set_error("Memory allocation failed");
            return EPUB_NATIVE_ERROR_MEMORY;
        }

        memcpy(*out_data, buffer.data(), total_read);
        (*out_data)[total_read] = '\0';
        *out_data_length = total_read;

        return EPUB_NATIVE_SUCCESS;

    } catch (const std::bad_alloc&) {
        set_error("Memory allocation failed");
        return EPUB_NATIVE_ERROR_MEMORY;
    } catch (...) {
        set_error("Unknown error reading archive entry");
        return EPUB_NATIVE_ERROR_IO;
    }
}

EPUB_NATIVE_API EpubNativeError epub_native_archive_read_entry_to_callback(
    EpubNativeArchive* archive_wrapper,
    const char* entry_path,
    epub_native_archive_read_callback callback,
    void* user_data
) {
    if (!archive_wrapper || !entry_path || !callback) {
        set_error("Invalid argument: null pointer");
        return EPUB_NATIVE_ERROR_INVALID_ARG;
    }

    try {
        std::string reopen_error;
        if (!reopen_archive(archive_wrapper, reopen_error)) {
            set_error(reopen_error.c_str());
            return EPUB_NATIVE_ERROR_IO;
        }

        struct archive_entry* entry;
        int r;
        bool found = false;

        while ((r = archive_read_next_header(archive_wrapper->archive, &entry)) == ARCHIVE_OK) {
            const char* pathname = archive_entry_pathname(entry);
            if (pathname && strcmp(pathname, entry_path) == 0) {
                found = true;
                break;
            }
            archive_read_data_skip(archive_wrapper->archive);
        }

        if (r != ARCHIVE_OK || !found) {
            set_error("Entry not found in archive");
            return EPUB_NATIVE_ERROR_NOT_FOUND;
        }

        const void* buff;
        size_t size;
        la_int64_t offset;

        while ((r = archive_read_data_block(
                archive_wrapper->archive, &buff, &size, &offset)) == ARCHIVE_OK) {
            callback(buff, size, user_data);
        }

        if (r != ARCHIVE_EOF) {
            set_error(archive_error_string(archive_wrapper->archive));
            return EPUB_NATIVE_ERROR_IO;
        }

        return EPUB_NATIVE_SUCCESS;

    } catch (...) {
        set_error("Unknown error reading archive entry via callback");
        return EPUB_NATIVE_ERROR_IO;
    }
}

EPUB_NATIVE_API EpubNativeError epub_native_archive_entry_exists(
    EpubNativeArchive* archive_wrapper,
    const char* entry_path
) {
    if (!archive_wrapper || !entry_path) {
        set_error("Invalid argument: null pointer");
        return EPUB_NATIVE_ERROR_INVALID_ARG;
    }

    try {
        std::string reopen_error;
        if (!reopen_archive(archive_wrapper, reopen_error)) {
            set_error(reopen_error.c_str());
            return EPUB_NATIVE_ERROR_IO;
        }

        struct archive_entry* entry;
        int r;

        while ((r = archive_read_next_header(archive_wrapper->archive, &entry)) == ARCHIVE_OK) {
            const char* pathname = archive_entry_pathname(entry);
            if (pathname && strcmp(pathname, entry_path) == 0) {
                return EPUB_NATIVE_SUCCESS;
            }
            archive_read_data_skip(archive_wrapper->archive);
        }

        return EPUB_NATIVE_ERROR_NOT_FOUND;

    } catch (...) {
        return EPUB_NATIVE_ERROR_IO;
    }
}
