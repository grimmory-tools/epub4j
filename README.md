# epub4j

Java library for EPUB read, validate, repair, normalize, transform, and write workflows.

## What it does

- Read EPUB from path, stream, or resources
- Write EPUB with package and metadata updates
- Lazy load resources for lower memory usage
- Validate structure, metadata, manifest, spine, references, and accessibility
- Run diagnostics with severity, error codes, and auto fix hints
- Auto repair common issues in malformed EPUB files
- Prune broken TOC entries and promote valid child entries
- Remove unreferenced JavaScript resources from manifest resources
- Remove common non-content artifact files (iTunes metadata, authoring tool bookmarks, OS leftovers)
- Validate EPUB mimetype entry and report strict/recover behavior
- Normalize invalid language tags and remove stray img tags with missing src
- Rebuild and normalize spine reading order from manifest XHTML resources
- Reconcile spine href/idref alias drift to canonical manifest resources
- Harden XHTML pre-parse well-formedness before downstream XML processing
- Repair broken internal href/src/url link graph using safe alias rewrite heuristics
- Generate KOReader-compatible partial MD5 checksums for dedupe/progress-sync IDs
- Normalize mixed encodings to UTF-8
- Normalize metadata fields and infer missing metadata
- Detect cover and synthesize missing table of contents
- Manipulate spine and split or merge XHTML
- Run search and replace across content resources
- Estimate word count
- Deduplicate resources
- Convert to kepub

## Reliability and safety

- Strict and recover processing modes
- Archive path traversal protection
- Duplicate entry detection
- Archive level byte budget
- Per entry byte budget
- Total uncompressed byte budget
- Bounded stream copy for input streams
- Case stable path deduplication using Locale.ROOT

## Quick start

```java
EpubProcessingPolicy policy = EpubProcessingPolicy.defaultPolicy()
    .withMaxArchiveBytes(256L * 1024 * 1024)
    .withMaxEntryBytes(32L * 1024 * 1024)
    .withMaxTotalUncompressedBytes(512L * 1024 * 1024);

EpubReader reader = new EpubReader(null, policy);
Book book = reader.readEpub(Path.of("book.epub"));
```

## Strict mode

```java
EpubReader reader = new EpubReader(null, EpubProcessingPolicy.strictPolicy());
var book = reader.readEpubStrict(Path.of("book.epub"));
```

## Recover mode report

```java
EpubReader reader = new EpubReader();
EpubReader.ReadResult result = reader.readEpubWithReport(Path.of("book.epub"));

if (result.report().hasWarnings()) {
    result.report().warnings().forEach(w ->
        System.out.println(w.code() + ": " + w.message())
    );
}

if (result.report().hasCorrections()) {
    result.report().corrections().forEach(System.out::println);
}
```

## KOReader-compatible checksum

Ported from KOReader checksum behavior for lightweight file identity workflows:

```java
var byPath = KoReaderChecksum.calculate(Path.of("book.epub"));
var byBytes = KoReaderChecksum.calculate(epubBytes);
```

## Roadmap

- Broken link validation and auto-repair for guide, TOC, and in-document href/src
- Unused CSS and unused image detection/removal
- OPF metadata schema cleanup and stronger namespace normalization
- Optional OPF2 to OPF3 upgrade helpers with nav document regeneration
- Batch/background job execution API for large repair/validation runs
- Metadata backup snapshot export and restore hooks
- Ingest-safe MIME/content sniffing beyond extension checks
- Optional duplicate detection heuristics for library hygiene

## Build

```bash
./gradlew build
```

## Runtime And Toolchain Requirements

- Java 25
- JVM flags for preview:

```text
--enable-preview
```

## Quality Workflow

Run the verification path used by CI:

```bash
./gradlew check --warning-mode all
```

## License

This project is licensed under the [GNU Affero General Public License v3.0](LICENSE) (AGPL-3.0-or-later).

### Attribution

org.grimmory.epub4j is a fork of [epub4j](https://github.com/documentnode/epub4j) (Apache-2.0). The git history have been kept intact from the fork.

New code and substantial modifications are:

- Copyright (C) 2025-2026 Grimmory contributors
- Copyright (C) 2025-2026 Booklore contributors

Some artifacts were mistakenly published under the wrong license as part of an automation. These were removed since. If you downloaded any of those, they contain viral AGPL code.

Anything after 1.X.X the license, and attribution is correctly stated.
