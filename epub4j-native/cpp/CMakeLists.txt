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
option(EPUB4J_NATIVE_USE_SYSTEM_ICU "Use system-installed ICU" OFF)
option(EPUB4J_NATIVE_USE_SYSTEM_LIBARCHIVE "Use system-installed libarchive" OFF)

if(EPUB4J_NATIVE_BUILD_SHARED AND EPUB4J_NATIVE_BUILD_STATIC)
    message(FATAL_ERROR "Invalid build configuration: enable only one of EPUB4J_NATIVE_BUILD_SHARED or EPUB4J_NATIVE_BUILD_STATIC")
endif()

if(NOT EPUB4J_NATIVE_BUILD_SHARED AND NOT EPUB4J_NATIVE_BUILD_STATIC)
    message(FATAL_ERROR "Invalid build configuration: one of EPUB4J_NATIVE_BUILD_SHARED or EPUB4J_NATIVE_BUILD_STATIC must be enabled")
endif()

# Compiler warnings and hardening flags
if(MSVC)
    add_compile_options(/W4 /WX-)
else()
    add_compile_options(
        -Wall -Wextra -Wpedantic
        -Wformat=2 -Wformat-security
        -Wconversion -Wsign-conversion
        -fstack-protector-strong
    )
    if(CMAKE_CXX_COMPILER_ID MATCHES "GNU|Clang")
        add_compile_options(-O1)
        add_compile_definitions(_FORTIFY_SOURCE=2)
    endif()
    if(UNIX AND NOT APPLE)
        add_link_options(-Wl,-z,relro -Wl,-z,now)
    endif()
endif()

# Dependency resolution: use system packages when requested, otherwise fetch pinned sources.
set(PUGIXML_INCLUDE_DIRS "")
set(PUGIXML_LINK_TARGETS "")
set(GUMBO_INCLUDE_DIRS "")
set(GUMBO_LINK_LIBS "")
set(GUMBO_SOURCES "")

if(EPUB4J_NATIVE_USE_SYSTEM_PUGIXML OR EPUB4J_NATIVE_USE_SYSTEM_GUMBO)
    find_package(PkgConfig REQUIRED)
endif()

if(EPUB4J_NATIVE_USE_SYSTEM_PUGIXML)
    pkg_check_modules(PUGIXML REQUIRED pugixml)
    set(PUGIXML_LINK_TARGETS ${PUGIXML_LINK_LIBRARIES})
else()
    include(FetchContent)
    FetchContent_Declare(
        pugixml
        GIT_REPOSITORY https://github.com/zeux/pugixml.git
        GIT_TAG        v1.14
    )
    FetchContent_MakeAvailable(pugixml)
    list(APPEND PUGIXML_INCLUDE_DIRS ${pugixml_SOURCE_DIR}/src)
    list(APPEND PUGIXML_LINK_TARGETS pugixml)
endif()

if(EPUB4J_NATIVE_USE_SYSTEM_GUMBO)
    pkg_check_modules(GUMBO REQUIRED gumbo)
    set(GUMBO_LINK_LIBS ${GUMBO_LINK_LIBRARIES})
else()
    include(FetchContent)
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

# ICU dependency for encoding detection
set(ICU_LINK_LIBS "")
set(ICU_DEFINES "")
if(EPUB4J_NATIVE_USE_SYSTEM_ICU)
    pkg_check_modules(ICU REQUIRED icu-uc icu-io)
    set(ICU_LINK_LIBS ${ICU_LINK_LIBRARIES})
    set(ICU_DEFINES -DHAVE_ICU)
else()
    # ICU is typically system-installed; try to find it
    find_package(ICU COMPONENTS uc io QUIET)
    if(ICU_FOUND)
        set(ICU_LINK_LIBS ICU::uc ICU::io)
        set(ICU_DEFINES -DHAVE_ICU)
    endif()
endif()

# libarchive dependency for ZIP handling
set(LIBARCHIVE_LINK_LIBS "")
set(LIBARCHIVE_DEFINES "")
if(EPUB4J_NATIVE_USE_SYSTEM_LIBARCHIVE)
    pkg_check_modules(LIBARCHIVE REQUIRED libarchive)
    set(LIBARCHIVE_LINK_LIBS ${LIBARCHIVE_LINK_LIBRARIES})
    set(LIBARCHIVE_DEFINES -DHAVE_LIBARCHIVE)
else()
    # Try to find system libarchive
    find_package(PkgConfig QUIET)
    if(PkgConfig_FOUND)
        pkg_check_modules(LIBARCHIVE libarchive)
    endif()
    if(LIBARCHIVE_FOUND)
        set(LIBARCHIVE_LINK_LIBS ${LIBARCHIVE_LINK_LIBRARIES})
        set(LIBARCHIVE_DEFINES -DHAVE_LIBARCHIVE)
    endif()
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
)

# Include directories
set(EPUB4J_NATIVE_INCLUDES
    include
    ${PUGIXML_INCLUDE_DIRS}
    ${GUMBO_INCLUDE_DIRS}
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

target_include_directories(epub4j_native
    PUBLIC
        $<BUILD_INTERFACE:${EPUB4J_NATIVE_INCLUDES}>
        $<INSTALL_INTERFACE:include>
)

target_link_libraries(epub4j_native
    PRIVATE
    ${PUGIXML_LINK_TARGETS}
    ${GUMBO_LINK_LIBS}
    ${ICU_LINK_LIBS}
    ${LIBARCHIVE_LINK_LIBS}
)

# Add compile definitions for optional features
target_compile_definitions(epub4j_native
    PRIVATE
    ${ICU_DEFINES}
    ${LIBARCHIVE_DEFINES}
)

# Platform-specific settings
if(WIN32)
    target_compile_definitions(epub4j_native PRIVATE
        EPUB4J_NATIVE_EXPORTS
        _CRT_SECURE_NO_WARNINGS
    )
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
