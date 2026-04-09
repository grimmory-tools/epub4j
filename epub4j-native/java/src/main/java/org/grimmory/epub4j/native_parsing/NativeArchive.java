/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Grimmory contributors
 * Copyright (C) 2025-2026 Booklore contributors
 */
package org.grimmory.epub4j.native_parsing;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static java.lang.foreign.ValueLayout.*;
import static org.grimmory.epub4j.native_parsing.PanamaConstants.*;

/**
 * Native archive (ZIP/EPUB) handler using libarchive via Panama FFM.
 *
 * <p>Provides robust ZIP/EPUB file handling with better error recovery
 * than Java's ZipInputStream. Handles corrupted archives gracefully.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * // Open from file
 * try (NativeArchive archive = NativeArchive.open(path)) {
 *     List<String> entries = archive.listEntries();
 *     byte[] content = archive.readEntry("OEBPS/content.opf");
 *     String opfXml = new String(content, StandardCharsets.UTF_8);
 * }
 *
 * // Open from memory
 * byte[] epubBytes = Files.readAllBytes(path);
 * try (NativeArchive archive = NativeArchive.openFromMemory(epubBytes)) {
 *     byte[] mimetype = archive.readEntry("mimetype");
 * }
 * }</pre>
 *
 * <p>Memory management:</p>
 * <ul>
 *     <li>Implements AutoCloseable for proper resource cleanup</li>
 *     <li>Always use try-with-resources to ensure native memory is freed</li>
 * </ul>
 *
 * @author Grimmory
 */
public class NativeArchive implements AutoCloseable {

    private final Arena arena;
    private final MemorySegment archivePointer;
    private boolean closed = false;
    private boolean nativeAvailable = true;

    private NativeArchive(Arena arena, MemorySegment archivePointer) {
        this.arena = arena;
        this.archivePointer = archivePointer;
        this.nativeAvailable = true;
    }

    /**
     * Open an archive from a file path.
     *
     * @param path Path to the archive file (ZIP, EPUB, etc.)
     * @return NativeArchive instance
     * @throws EpubNativeException if opening fails
     */
    public static NativeArchive open(Path path) {
        return open(path.toString());
    }

    /**
     * Open an archive from a file path string.
     *
     * @param filepath Path to the archive file
     * @return NativeArchive instance
     * @throws EpubNativeException if opening fails
     */
    public static NativeArchive open(String filepath) {
        Arena arena = Arena.ofConfined();
        try {
            MemorySegment filepathSegment = toNativeString(filepath, arena);
            PointerHolder archiveHolder = PointerHolder.allocate(arena);

            int errorCode = EpubNativeHeaders.epub_native_archive_open(
                filepathSegment,
                    archiveHolder.segment()
            );

            checkError(errorCode);

            MemorySegment archivePtr = archiveHolder.segment().get(ADDRESS, 0);
            return new NativeArchive(arena, archivePtr);

        } catch (UnsatisfiedLinkError e) {
            // Native library not available
            arena.close();
            throw new EpubNativeException("Native archive library not available", e);
        } catch (Throwable e) {
            arena.close();
            throw new EpubNativeException("Failed to open archive: " + filepath, e);
        }
    }

    /**
     * Open an archive from a memory buffer.
     *
     * @param data Archive data bytes
     * @return NativeArchive instance
     * @throws EpubNativeException if opening fails
     */
    public static NativeArchive openFromMemory(byte[] data) {
        Arena arena = Arena.ofConfined();
        try {
            MemorySegment dataSegment = arena.allocateFrom(JAVA_BYTE, data);
            PointerHolder archiveHolder = PointerHolder.allocate(arena);

            int errorCode = EpubNativeHeaders.epub_native_archive_open_memory(
                dataSegment,
                data.length,
                    archiveHolder.segment()
            );

            checkError(errorCode);

            MemorySegment archivePtr = archiveHolder.segment().get(ADDRESS, 0);
            return new NativeArchive(arena, archivePtr);

        } catch (UnsatisfiedLinkError e) {
            // Native library not available
            arena.close();
            throw new EpubNativeException("Native archive library not available", e);
        } catch (Throwable e) {
            arena.close();
            throw new EpubNativeException("Failed to open archive from memory", e);
        }
    }

    /**
     * List all entries in the archive.
     *
     * @return List of entry paths
     * @throws EpubNativeException if listing fails
     */
    public List<String> listEntries() {
        checkOpen();

        if (!nativeAvailable) {
            throw new EpubNativeException("Native archive library not available");
        }

        try (Arena localArena = Arena.ofConfined()) {
            PointerHolder entriesHolder = PointerHolder.allocate(localArena);
            MemorySegment countHolder = localArena.allocate(JAVA_LONG);

            int errorCode = EpubNativeHeaders.epub_native_archive_list_entries(
                archivePointer,
                    entriesHolder.segment(),
                countHolder
            );

            checkError(errorCode);

            long count = countHolder.get(JAVA_LONG, 0);
            MemorySegment entriesArray = entriesHolder.segment().get(ADDRESS, 0);

            List<String> result = new ArrayList<>();
            if (count > 0 && entriesArray != null && !entriesArray.equals(MemorySegment.NULL)) {
                entriesArray = entriesArray.reinterpret(count * ADDRESS.byteSize());

                for (long i = 0; i < count; i++) {
                    MemorySegment entryPtr = entriesArray.get(ADDRESS, i * ADDRESS.byteSize());
                    String entry = toJavaString(entryPtr);
                    result.add(entry);
                }
            }

            // Free the array (strings are freed by the array free function)
            EpubNativeHeaders.epub_native_archive_free_string_array(entriesArray, count);

            return List.copyOf(result);

        } catch (Throwable e) {
            throw new EpubNativeException("Failed to list archive entries", e);
        }
    }

    /**
     * Read a specific entry from the archive.
     *
     * @param entryPath Path of the entry to read
     * @return Entry content as byte array
     * @throws EpubNativeException if reading fails or entry not found
     */
    public byte[] readEntry(String entryPath) {
        checkOpen();

        if (!nativeAvailable) {
            throw new EpubNativeException("Native archive library not available");
        }

        try (Arena localArena = Arena.ofConfined()) {
            MemorySegment entryPathSegment = toNativeString(entryPath, localArena);
            PointerHolder dataHolder = PointerHolder.allocate(localArena);
            MemorySegment dataLengthHolder = localArena.allocate(JAVA_LONG);

            int errorCode = EpubNativeHeaders.epub_native_archive_read_entry(
                archivePointer,
                entryPathSegment,
                    dataHolder.segment(),
                dataLengthHolder
            );

            if (errorCode == EPUB_NATIVE_ERROR_NOT_FOUND) {
                throw new EpubNativeException("Entry not found: " + entryPath);
            }
            checkError(errorCode);

            long dataLength = dataLengthHolder.get(JAVA_LONG, 0);
            MemorySegment dataPtr = dataHolder.segment().get(ADDRESS, 0);

            byte[] data = dataPtr.reinterpret(dataLength).toArray(JAVA_BYTE);

            // Free native string
            nativeStringFree(dataPtr);

            return data;

        } catch (EpubNativeException e) {
            throw e;
        } catch (Throwable e) {
            throw new EpubNativeException("Failed to read entry: " + entryPath, e);
        }
    }

    /**
     * Stream a specific entry from the archive directly to an OutputStream.
     *
     * <p>Unlike {@link #readEntry(String)}, this method does not allocate the entire
     * entry content on the Java heap. Instead, libarchive reads in ~10KB blocks and
     * each block is written directly to the output stream via a Panama upcall.
     * This keeps memory usage constant regardless of entry size.</p>
     *
     * @param entryPath Path of the entry to stream
     * @param out       OutputStream to write data to
     * @throws IOException          if writing to the stream fails
     * @throws EpubNativeException  if the entry is not found or native reading fails
     */
    public void streamEntry(String entryPath, OutputStream out) throws IOException {
        checkOpen();
        java.util.Objects.requireNonNull(out, "out");

        if (!nativeAvailable) {
            throw new EpubNativeException("Native archive library not available");
        }

        try (Arena callbackArena = Arena.ofConfined()) {
            MemorySegment entryPathSegment = toNativeString(entryPath, callbackArena);

            // Capture any throwable thrown inside the callback and rethrow it on the Java side
            Throwable[] captured = new Throwable[1];

            EpubNativeHeaders.ArchiveReadCallback callback = (data, size, _userData) -> {
                if (captured[0] != null) {
                    return 1; // already failed, skip remaining chunks
                }
                try {
                    // Reinterpret the pointer so we can read 'size' bytes from it
                    byte[] chunk = data.reinterpret(size).toArray(JAVA_BYTE);
                    out.write(chunk);
                    return 0; // Success
                } catch (Throwable t) {
                    captured[0] = t;
                    return 1; // Failure
                }
            };

            MemorySegment callbackStub = EpubNativeHeaders.archiveReadCallbackUpcallStub(callback, callbackArena);

            int errorCode = EpubNativeHeaders.epub_native_archive_read_entry_to_callback(
                archivePointer,
                entryPathSegment,
                callbackStub,
                MemorySegment.NULL
            );

            if (captured[0] != null) {
                if (captured[0] instanceof IOException io) {
                    throw io;
                }
                if (captured[0] instanceof RuntimeException re) {
                    throw re;
                }
                if (captured[0] instanceof Error err) {
                    throw err;
                }
                throw new EpubNativeException("Callback failed while streaming entry: " + entryPath, captured[0]);
            }

            if (errorCode == EPUB_NATIVE_ERROR_NOT_FOUND) {
                throw new EpubNativeException("Entry not found: " + entryPath);
            }
            checkError(errorCode);

        } catch (IOException | EpubNativeException e) {
            throw e;
        } catch (Throwable e) {
            throw new EpubNativeException("Failed to stream entry: " + entryPath, e);
        }
    }

    /**
     * Check if an entry exists in the archive.
     *
     * @param entryPath Path of the entry to check
     * @return true if entry exists
     * @throws EpubNativeException if check fails
     */
    public boolean entryExists(String entryPath) {
        checkOpen();

        if (!nativeAvailable) {
            throw new EpubNativeException("Native archive library not available");
        }

        try (Arena localArena = Arena.ofConfined()) {
            MemorySegment entryPathSegment = toNativeString(entryPath, localArena);

            int errorCode = EpubNativeHeaders.epub_native_archive_entry_exists(
                archivePointer,
                entryPathSegment
            );

            return errorCode == EPUB_NATIVE_SUCCESS;

        } catch (Throwable e) {
            throw new EpubNativeException("Failed to check entry existence: " + entryPath, e);
        }
    }

    /**
     * Check if native library is available.
     *
     * @return true if libarchive-based native handling is available
     */
    public boolean isNativeAvailable() {
        return nativeAvailable;
    }

    private void checkOpen() {
        if (closed) {
            throw new IllegalStateException("Archive is closed");
        }
    }

    @Override
    public void close() {
        if (!closed) {
            if (archivePointer != null && !archivePointer.equals(MemorySegment.NULL)) {
                try {
                    EpubNativeHeaders.epub_native_archive_free(archivePointer);
                } catch (Throwable e) {
                    // Log but don't throw
                }
            }
            arena.close();
            closed = true;
        }
    }
}
