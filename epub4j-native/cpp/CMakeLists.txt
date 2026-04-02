cmake_minimum_required(VERSION 3.16)
project(epub4j_native VERSION 1.0.0 LANGUAGES CXX C)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# Export compile commands for IDE integration
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

# Options
option(EPUB4J_NATIVE_BUILD_SHARED "Build shared library" ON)
option(EPUB4J_NATIVE_BUILD_STATIC "Build static library" OFF)
option(EPUB4J_NATIVE_USE_SYSTEM_PUGIXML "Use system-installed pugixml" OFF)
option(EPUB4J_NATIVE_USE_SYSTEM_GUMBO "Use system-installed gumbo" OFF)
option(EPUB4J_NATIVE_USE_SYSTEM_UCHARDET "Use system-installed uchardet" OFF)
option(EPUB4J_NATIVE_USE_SYSTEM_ZLIB "Use system-installed zlib" OFF)
option(EPUB4J_NATIVE_USE_SYSTEM_LIBARCHIVE "Use system-installed libarchive" OFF)
option(EPUB4J_NATIVE_USE_SYSTEM_LIBJPEG_TURBO "Use system-installed libjpeg-turbo" OFF)
option(EPUB4J_NATIVE_USE_SYSTEM_LIBPNG "Use system-installed libpng" OFF)
option(EPUB4J_NATIVE_USE_SYSTEM_LIBWEBP "Use system-installed libwebp" OFF)
option(EPUB4J_NATIVE_ENABLE_IMAGE_PROCESSING "Build with JPEG/PNG/WebP image processing" ON)

if(EPUB4J_NATIVE_BUILD_SHARED AND EPUB4J_NATIVE_BUILD_STATIC)
    message(FATAL_ERROR "Invalid build configuration: enable only one of EPUB4J_NATIVE_BUILD_SHARED or EPUB4J_NATIVE_BUILD_STATIC")
endif()

if(NOT EPUB4J_NATIVE_BUILD_SHARED AND NOT EPUB4J_NATIVE_BUILD_STATIC)
    message(FATAL_ERROR "Invalid build configuration: one of EPUB4J_NATIVE_BUILD_SHARED or EPUB4J_NATIVE_BUILD_STATIC must be enabled")
endif()

# Compiler warnings and hardening flags (applied to epub4j_native target only, not dependencies)
# Stored in variables here; applied via target_compile_options() after the target is created.
if(MSVC)
    set(EPUB4J_NATIVE_COMPILE_FLAGS /W4 /WX-)
else()
    set(EPUB4J_NATIVE_COMPILE_FLAGS
        -Wall -Wextra -Wpedantic
        -Wformat=2 -Wformat-security
        -Wconversion -Wsign-conversion
        -fstack-protector-strong
    )
    if(CMAKE_CXX_COMPILER_ID MATCHES "GNU|Clang")
        list(APPEND EPUB4J_NATIVE_COMPILE_FLAGS -O1)
        add_compile_definitions(_FORTIFY_SOURCE=2)
    endif()
    if(UNIX AND NOT APPLE)
        add_link_options(-Wl,-z,relro -Wl,-z,now)
    endif()
endif()

# ============================================================================
# Dependency resolution
# All FetchContent'd dependencies are built as static libraries and linked into
# the single shared library. No system dependencies beyond the C runtime.
# ============================================================================
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
include(FetchContent)

# Allow FetchContent'd deps with older cmake_minimum_required versions (CMake 4.x compat)
set(CMAKE_POLICY_VERSION_MINIMUM 3.5)

# Allow FetchContent_Populate for source-only deps (gumbo has no CMakeLists.txt)
if(POLICY CMP0169)
    cmake_policy(SET CMP0169 OLD)
endif()

set(PUGIXML_INCLUDE_DIRS "")
set(PUGIXML_LINK_TARGETS "")
set(GUMBO_INCLUDE_DIRS "")
set(GUMBO_LINK_LIBS "")
set(GUMBO_SOURCES "")
set(UCHARDET_INCLUDE_DIRS "")
set(UCHARDET_LINK_TARGETS "")
set(LIBARCHIVE_LINK_TARGETS "")

if(EPUB4J_NATIVE_USE_SYSTEM_PUGIXML OR EPUB4J_NATIVE_USE_SYSTEM_GUMBO OR
   EPUB4J_NATIVE_USE_SYSTEM_UCHARDET OR EPUB4J_NATIVE_USE_SYSTEM_LIBARCHIVE)
    find_package(PkgConfig REQUIRED)
endif()

# --- pugixml v1.14 ---
if(EPUB4J_NATIVE_USE_SYSTEM_PUGIXML)
    pkg_check_modules(PUGIXML REQUIRED pugixml)
    set(PUGIXML_LINK_TARGETS ${PUGIXML_LINK_LIBRARIES})
else()
    FetchContent_Declare(
        pugixml
        GIT_REPOSITORY https://github.com/zeux/pugixml.git
        GIT_TAG        v1.14
    )
    FetchContent_MakeAvailable(pugixml)
    list(APPEND PUGIXML_INCLUDE_DIRS ${pugixml_SOURCE_DIR}/src)
    list(APPEND PUGIXML_LINK_TARGETS pugixml)
endif()

# --- gumbo-parser 0.13.2 (compiled inline; no CMakeLists.txt in project) ---
if(EPUB4J_NATIVE_USE_SYSTEM_GUMBO)
    pkg_check_modules(GUMBO REQUIRED gumbo)
    set(GUMBO_LINK_LIBS ${GUMBO_LINK_LIBRARIES})
else()
    FetchContent_Declare(
        gumbo
        GIT_REPOSITORY https://codeberg.org/gumbo-parser/gumbo-parser.git
        GIT_TAG        0.13.2
    )
    FetchContent_Populate(gumbo)
    list(APPEND GUMBO_INCLUDE_DIRS ${gumbo_SOURCE_DIR}/src)
    set(GUMBO_SOURCES
        ${gumbo_SOURCE_DIR}/src/attribute.c
        ${gumbo_SOURCE_DIR}/src/char_ref.c
        ${gumbo_SOURCE_DIR}/src/error.c
        ${gumbo_SOURCE_DIR}/src/parser.c
        ${gumbo_SOURCE_DIR}/src/string_buffer.c
        ${gumbo_SOURCE_DIR}/src/string_piece.c
        ${gumbo_SOURCE_DIR}/src/tag.c
        ${gumbo_SOURCE_DIR}/src/tokenizer.c
        ${gumbo_SOURCE_DIR}/src/utf8.c
        ${gumbo_SOURCE_DIR}/src/util.c
        ${gumbo_SOURCE_DIR}/src/vector.c
    )
endif()

# --- uchardet v0.0.8 (charset detection; replaces ICU) ---
if(EPUB4J_NATIVE_USE_SYSTEM_UCHARDET)
    pkg_check_modules(UCHARDET REQUIRED uchardet)
    set(UCHARDET_LINK_TARGETS ${UCHARDET_LINK_LIBRARIES})
    set(UCHARDET_INCLUDE_DIRS ${UCHARDET_INCLUDE_DIRS})
else()
    set(BUILD_BINARY OFF CACHE BOOL "" FORCE)
    FetchContent_Declare(
        uchardet
        URL https://www.freedesktop.org/software/uchardet/releases/uchardet-0.0.8.tar.xz
        DOWNLOAD_EXTRACT_TIMESTAMP TRUE
    )
    FetchContent_MakeAvailable(uchardet)
    set(UCHARDET_INCLUDE_DIRS ${uchardet_SOURCE_DIR}/src)
    set(UCHARDET_LINK_TARGETS libuchardet)
endif()

# --- zlib v1.3.1 (needed by libarchive for ZIP deflate) ---
if(NOT EPUB4J_NATIVE_USE_SYSTEM_ZLIB AND NOT EPUB4J_NATIVE_USE_SYSTEM_LIBARCHIVE)
    set(ZLIB_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
    FetchContent_Declare(
        zlib
        GIT_REPOSITORY https://github.com/madler/zlib.git
        GIT_TAG        v1.3.1
    )
    FetchContent_MakeAvailable(zlib)
    # Override any cached/system ZLIB so libarchive uses our FetchContent'd zlib
    set(ZLIB_FOUND TRUE CACHE BOOL "" FORCE)
    set(ZLIB_INCLUDE_DIR "${zlib_SOURCE_DIR}" CACHE PATH "" FORCE)
    set(ZLIB_LIBRARIES zlibstatic CACHE STRING "" FORCE)
    set(ZLIB_LIBRARY_RELEASE zlibstatic CACHE STRING "" FORCE)
    # Also need the build dir for the generated zconf.h
    include_directories("${zlib_BINARY_DIR}")
    if(NOT TARGET ZLIB::ZLIB)
        add_library(ZLIB::ZLIB ALIAS zlibstatic)
    endif()
endif()

# --- libarchive v3.7.7 (ZIP/EPUB archive handling) ---
if(EPUB4J_NATIVE_USE_SYSTEM_LIBARCHIVE)
    pkg_check_modules(LIBARCHIVE REQUIRED libarchive)
    set(LIBARCHIVE_LINK_TARGETS ${LIBARCHIVE_LINK_LIBRARIES})
else()
    # Disable all features except zlib-based ZIP support
    set(ENABLE_OPENSSL OFF CACHE BOOL "" FORCE)
    set(ENABLE_LIBB2 OFF CACHE BOOL "" FORCE)
    set(ENABLE_LIBXML2 OFF CACHE BOOL "" FORCE)
    set(ENABLE_EXPAT OFF CACHE BOOL "" FORCE)
    set(ENABLE_LZMA OFF CACHE BOOL "" FORCE)
    set(ENABLE_LZ4 OFF CACHE BOOL "" FORCE)
    set(ENABLE_ZSTD OFF CACHE BOOL "" FORCE)
    set(ENABLE_BZip2 OFF CACHE BOOL "" FORCE)
    set(ENABLE_ACL OFF CACHE BOOL "" FORCE)
    set(ENABLE_XATTR OFF CACHE BOOL "" FORCE)
    set(ENABLE_PCREPOSIX OFF CACHE BOOL "" FORCE)
    set(ENABLE_PCRE2POSIX OFF CACHE BOOL "" FORCE)
    set(ENABLE_LibGCC OFF CACHE BOOL "" FORCE)
    set(ENABLE_CNG OFF CACHE BOOL "" FORCE)
    set(ENABLE_MBEDTLS OFF CACHE BOOL "" FORCE)
    set(ENABLE_NETTLE OFF CACHE BOOL "" FORCE)
    set(ENABLE_ICONV OFF CACHE BOOL "" FORCE)
    set(ENABLE_TAR OFF CACHE BOOL "" FORCE)
    set(ENABLE_CPIO OFF CACHE BOOL "" FORCE)
    set(ENABLE_CAT OFF CACHE BOOL "" FORCE)
    set(ENABLE_UNZIP OFF CACHE BOOL "" FORCE)
    set(ENABLE_TEST OFF CACHE BOOL "" FORCE)
    set(ENABLE_INSTALL OFF CACHE BOOL "" FORCE)
    # Disable ext2fs detection (not needed for EPUB/ZIP, may be missing on some systems)
    set(HAVE_EXT2FS_EXT2_FS_H FALSE CACHE BOOL "" FORCE)
    FetchContent_Declare(
        libarchive
        GIT_REPOSITORY https://github.com/libarchive/libarchive.git
        GIT_TAG        v3.7.7
    )
    FetchContent_MakeAvailable(libarchive)
    if(TARGET archive_static)
        set(LIBARCHIVE_LINK_TARGETS archive_static)
    else()
        set(LIBARCHIVE_LINK_TARGETS archive)
    endif()
endif()

# ============================================================================
# Image processing dependencies (optional, controlled by EPUB4J_NATIVE_ENABLE_IMAGE_PROCESSING)
# ============================================================================

set(IMAGE_COMPILE_DEFS "")
set(IMAGE_INCLUDE_DIRS "")
set(IMAGE_LINK_TARGETS "")
set(IMAGE_SOURCES "")

if(EPUB4J_NATIVE_ENABLE_IMAGE_PROCESSING)
    # --- libjpeg-turbo 3.1.3 (JPEG lossless optimization and lossy compression) ---
    if(EPUB4J_NATIVE_USE_SYSTEM_LIBJPEG_TURBO)
        pkg_check_modules(LIBJPEG_TURBO REQUIRED libjpeg libturbojpeg)
        list(APPEND IMAGE_INCLUDE_DIRS ${LIBJPEG_TURBO_INCLUDE_DIRS})
        list(APPEND IMAGE_LINK_TARGETS ${LIBJPEG_TURBO_LINK_LIBRARIES})
    else()
        # libjpeg-turbo prohibits add_subdirectory() integration, so we build
        # it as a standalone project at configure time and import the results.
        find_program(NASM_EXECUTABLE nasm)
        if(NOT NASM_EXECUTABLE)
            set(_LJT_SIMD_FLAG "-DWITH_SIMD=OFF")
        else()
            set(_LJT_SIMD_FLAG "")
        endif()
        FetchContent_Declare(
            libjpeg_turbo
            GIT_REPOSITORY https://github.com/libjpeg-turbo/libjpeg-turbo.git
            GIT_TAG        3.1.3
        )
        FetchContent_Populate(libjpeg_turbo)
        set(_LJT_INSTALL "${libjpeg_turbo_BINARY_DIR}/dist")
        include(ProcessorCount)
        ProcessorCount(_LJT_NCPU)
        if(_LJT_NCPU EQUAL 0)
            set(_LJT_NCPU 1)
        endif()
        execute_process(
            COMMAND ${CMAKE_COMMAND}
                -S "${libjpeg_turbo_SOURCE_DIR}"
                -B "${libjpeg_turbo_BINARY_DIR}/build"
                -G "${CMAKE_GENERATOR}"
                -DCMAKE_BUILD_TYPE=Release
                -DCMAKE_POSITION_INDEPENDENT_CODE=ON
                -DCMAKE_INSTALL_PREFIX=${_LJT_INSTALL}
                -DENABLE_SHARED=OFF
                -DENABLE_STATIC=ON
                -DWITH_JAVA=OFF
                -DWITH_TURBOJPEG=ON
                ${_LJT_SIMD_FLAG}
            RESULT_VARIABLE _LJT_RESULT
        )
        if(_LJT_RESULT)
            message(FATAL_ERROR "libjpeg-turbo configure step failed")
        endif()
        execute_process(
            COMMAND ${CMAKE_COMMAND}
                --build "${libjpeg_turbo_BINARY_DIR}/build"
                --config Release
                --parallel ${_LJT_NCPU}
            RESULT_VARIABLE _LJT_RESULT
        )
        if(_LJT_RESULT)
            message(FATAL_ERROR "libjpeg-turbo build step failed")
        endif()
        execute_process(
            COMMAND ${CMAKE_COMMAND}
                --install "${libjpeg_turbo_BINARY_DIR}/build"
                --config Release
            RESULT_VARIABLE _LJT_RESULT
        )
        if(_LJT_RESULT)
            message(FATAL_ERROR "libjpeg-turbo install step failed")
        endif()
        # lib/ vs lib64/ varies by distro
        if(EXISTS "${_LJT_INSTALL}/lib64/libturbojpeg.a")
            set(_LJT_LIB_DIR "${_LJT_INSTALL}/lib64")
        else()
            set(_LJT_LIB_DIR "${_LJT_INSTALL}/lib")
        endif()
        add_library(turbojpeg-static STATIC IMPORTED GLOBAL)
        set_target_properties(turbojpeg-static PROPERTIES
            IMPORTED_LOCATION "${_LJT_LIB_DIR}/libturbojpeg.a"
        )
        add_library(jpeg-static STATIC IMPORTED GLOBAL)
        set_target_properties(jpeg-static PROPERTIES
            IMPORTED_LOCATION "${_LJT_LIB_DIR}/libjpeg.a"
        )
        list(APPEND IMAGE_INCLUDE_DIRS "${_LJT_INSTALL}/include")
        list(APPEND IMAGE_LINK_TARGETS turbojpeg-static jpeg-static)
    endif()
    list(APPEND IMAGE_COMPILE_DEFS EPUB4J_NATIVE_HAS_JPEG)

    # --- libpng 1.6.45 (PNG optimization) ---
    if(EPUB4J_NATIVE_USE_SYSTEM_LIBPNG)
        pkg_check_modules(LIBPNG REQUIRED libpng)
        list(APPEND IMAGE_INCLUDE_DIRS ${LIBPNG_INCLUDE_DIRS})
        list(APPEND IMAGE_LINK_TARGETS ${LIBPNG_LINK_LIBRARIES})
    else()
        set(PNG_SHARED OFF CACHE BOOL "" FORCE)
        set(PNG_STATIC ON CACHE BOOL "" FORCE)
        set(PNG_TESTS OFF CACHE BOOL "" FORCE)
        set(PNG_TOOLS OFF CACHE BOOL "" FORCE)
        set(PNG_FRAMEWORK OFF CACHE BOOL "" FORCE)
        # Prevent install(EXPORT) errors from referencing zlibstatic outside the export set
        set(SKIP_INSTALL_ALL ON CACHE BOOL "" FORCE)
        set(PNG_INSTALL OFF CACHE BOOL "" FORCE)
        FetchContent_Declare(
            libpng
            GIT_REPOSITORY https://github.com/pnggroup/libpng.git
            GIT_TAG        v1.6.45
        )
        FetchContent_MakeAvailable(libpng)
        list(APPEND IMAGE_INCLUDE_DIRS
            ${libpng_SOURCE_DIR}
            ${libpng_BINARY_DIR}
        )
        if(TARGET png_static)
            list(APPEND IMAGE_LINK_TARGETS png_static)
        elseif(TARGET png16_static)
            list(APPEND IMAGE_LINK_TARGETS png16_static)
        endif()
    endif()
    list(APPEND IMAGE_COMPILE_DEFS EPUB4J_NATIVE_HAS_PNG)

    # --- libwebp 1.5.0 (WebP encoding and decoding) ---
    if(EPUB4J_NATIVE_USE_SYSTEM_LIBWEBP)
        pkg_check_modules(LIBWEBP REQUIRED libwebp)
        list(APPEND IMAGE_INCLUDE_DIRS ${LIBWEBP_INCLUDE_DIRS})
        list(APPEND IMAGE_LINK_TARGETS ${LIBWEBP_LINK_LIBRARIES})
    else()
        set(WEBP_BUILD_ANIM_UTILS OFF CACHE BOOL "" FORCE)
        set(WEBP_BUILD_CWEBP OFF CACHE BOOL "" FORCE)
        set(WEBP_BUILD_DWEBP OFF CACHE BOOL "" FORCE)
        set(WEBP_BUILD_GIF2WEBP OFF CACHE BOOL "" FORCE)
        set(WEBP_BUILD_IMG2WEBP OFF CACHE BOOL "" FORCE)
        set(WEBP_BUILD_VWEBP OFF CACHE BOOL "" FORCE)
        set(WEBP_BUILD_WEBPINFO OFF CACHE BOOL "" FORCE)
        set(WEBP_BUILD_WEBPMUX OFF CACHE BOOL "" FORCE)
        set(WEBP_BUILD_EXTRAS OFF CACHE BOOL "" FORCE)
        set(WEBP_BUILD_LIBSHARPYUV ON CACHE BOOL "" FORCE)
        FetchContent_Declare(
            libwebp
            GIT_REPOSITORY https://github.com/webmproject/libwebp.git
            GIT_TAG        v1.5.0
        )
        FetchContent_MakeAvailable(libwebp)
        list(APPEND IMAGE_INCLUDE_DIRS ${libwebp_SOURCE_DIR}/src)
        if(TARGET webp)
            list(APPEND IMAGE_LINK_TARGETS webp)
        endif()
    endif()
    list(APPEND IMAGE_COMPILE_DEFS EPUB4J_NATIVE_HAS_WEBP)

    # --- stb (header-only: stb_image_resize2.h for high-quality resampling) ---
    FetchContent_Declare(
        stb
        GIT_REPOSITORY https://github.com/nothings/stb.git
        GIT_TAG        f0569113c93ad095470c54bf34a17b36646bbbb5
    )
    FetchContent_Populate(stb)
    list(APPEND IMAGE_INCLUDE_DIRS ${stb_SOURCE_DIR})

    # Image processor source file
    list(APPEND IMAGE_SOURCES src/image_processor.cpp)
endif()

# Source files
set(EPUB4J_NATIVE_SOURCES
    src/epub_native.cpp
    src/package_reader.cpp
    src/ncx_document.cpp
    src/nav_document.cpp
    src/html_cleaner.cpp
    src/epub_encoding.cpp
    src/epub_archive.cpp
    ${IMAGE_SOURCES}
)

# Include directories
set(EPUB4J_NATIVE_INCLUDES
    include
    ${PUGIXML_INCLUDE_DIRS}
    ${GUMBO_INCLUDE_DIRS}
    ${UCHARDET_INCLUDE_DIRS}
    ${IMAGE_INCLUDE_DIRS}
)

# Create library
if(EPUB4J_NATIVE_BUILD_SHARED)
    add_library(epub4j_native SHARED ${EPUB4J_NATIVE_SOURCES} ${GUMBO_SOURCES})
    set_target_properties(epub4j_native PROPERTIES
        OUTPUT_NAME "epub4j_native"
        VERSION ${PROJECT_VERSION}
        SOVERSION 1
    )
elseif(EPUB4J_NATIVE_BUILD_STATIC)
    add_library(epub4j_native STATIC ${EPUB4J_NATIVE_SOURCES} ${GUMBO_SOURCES})
endif()

target_compile_options(epub4j_native PRIVATE ${EPUB4J_NATIVE_COMPILE_FLAGS})

target_include_directories(epub4j_native
    PUBLIC
        $<BUILD_INTERFACE:${EPUB4J_NATIVE_INCLUDES}>
        $<INSTALL_INTERFACE:include>
)

target_link_libraries(epub4j_native
    PRIVATE
    ${PUGIXML_LINK_TARGETS}
    ${GUMBO_LINK_LIBS}
    ${UCHARDET_LINK_TARGETS}
    ${LIBARCHIVE_LINK_TARGETS}
    ${IMAGE_LINK_TARGETS}
)

# Image processing compile definitions (EPUB4J_NATIVE_HAS_JPEG etc.)
if(IMAGE_COMPILE_DEFS)
    target_compile_definitions(epub4j_native PRIVATE ${IMAGE_COMPILE_DEFS})
endif()

# iconv: part of libc on Linux/musl; link system libiconv on macOS
if(APPLE)
    find_library(ICONV_LIB iconv)
    if(ICONV_LIB)
        target_link_libraries(epub4j_native PRIVATE ${ICONV_LIB})
    endif()
endif()

# Platform-specific settings
if(WIN32)
    target_compile_definitions(epub4j_native PRIVATE
        EPUB4J_NATIVE_EXPORTS
        _CRT_SECURE_NO_WARNINGS
    )
    # MinGW adds a "lib" prefix by default; remove it so the DLL is named epub4j_native.dll
    set_target_properties(epub4j_native PROPERTIES PREFIX "")
elseif(APPLE)
    set_target_properties(epub4j_native PROPERTIES
        INSTALL_RPATH "@executable_path/../lib"
    )
else()
    set_target_properties(epub4j_native PROPERTIES
        INSTALL_RPATH "$ORIGIN/../lib"
    )
endif()

# Installation
install(TARGETS epub4j_native
    LIBRARY DESTINATION lib
    ARCHIVE DESTINATION lib
    RUNTIME DESTINATION bin
)

install(DIRECTORY include/ DESTINATION include)
