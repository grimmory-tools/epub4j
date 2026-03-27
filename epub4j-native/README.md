# epub4j-native: C++ XML/HTML Parser with Panama FFM Bindings

This module provides native C++ implementations of XML/HTML parsing for epub4j using:
- **pugixml** for XML parsing (PackageDocument, NCXDocument)
- **Gumbo** for HTML5 parsing (HtmlCleaner replacement)
- **Java Panama FFM API** for zero-overhead native bindings

## Project Structure

```
epub4j-native/
+-- cpp/                    # C++ native implementation
|   +-- CMakeLists.txt
|   +-- include/
|   |   +-- epub_native.h       # Main C API header
|   |   +-- package_reader.h    # PackageDocumentReader equivalent
|   |   +-- ncx_document.h      # NCXDocument equivalent
|   |   +-- nav_document.h      # NavDocument equivalent
|   |   +-- html_cleaner.h      # HtmlCleanerBookProcessor equivalent
|   +-- src/
|       +-- epub_native.cpp     # C API implementation
|       +-- package_reader.cpp
|       +-- ncx_document.cpp
|       +-- nav_document.cpp
|       +-- html_cleaner.cpp
+-- java/
|   +-- src/main/java/org/grimmory/epub4j/native/
|       +-- PanamaConstants.java    # Constants and library loading
|       +-- NativePackageReader.java # Panama FFM bindings for package reading
|       +-- NativeNCXDocument.java   # Panama FFM bindings for NCX
|       +-- NativeNavDocument.java   # Panama FFM bindings for Nav
|       +-- NativeHtmlCleaner.java   # Panama FFM bindings for HTML cleaning
+-- build.gradle
```

## Build Requirements

- CMake 3.16+
- C++17 compatible compiler (GCC 9+, Clang 10+, MSVC 2019+)
- Java 21+ (for Panama FFM)
- pugixml (will be fetched by CMake)
- Gumbo (will be fetched by CMake)

For container builds, system packages can be used instead of fetching/building dependencies from source.

## Building

```bash
# Build native library
cd epub4j-native/cpp
mkdir build && cd build
cmake ..
cmake --build .

# Build Java bindings
cd ../../java
../gradlew build
```

### Use system libraries (recommended for Alpine/Docker CI)

When your container image already manages native dependencies, configure CMake to use system packages:

```bash
./gradlew :epub4j-native:buildNative -PuseSystemNativeDeps=true
./gradlew :epub4j-native:buildNativeRelease -PuseSystemNativeDeps=true
```

Example Alpine package prerequisites:

```bash
apk add --no-cache \
	build-base cmake pkgconf \
	gumbo-parser-dev pugixml-dev \
	libstdc++
```

For runtime containers (where you only run Java and load the native library), install at least:

```bash
apk add --no-cache libstdc++ gumbo-parser pugixml
```

At runtime in Alpine, either:

- Place `libepub4j_native.so` in the dynamic loader path and use `System.loadLibrary`, or
- Set `-Depub4j.native.path=/absolute/path/to/libepub4j_native.so`.

The Java loader now prefers system library loading first, then falls back to classpath-embedded platform binaries.

## Docker-first workflow (no vendored C++ deps)

In CI/CD containers, prefer system packages and avoid building dependency sources from CMake FetchContent.

```bash
./gradlew :epub4j-native:buildNativeRelease -PuseSystemNativeDeps=true
./gradlew :epub4j-native:test -PuseSystemNativeDeps=true
```

This keeps builds deterministic in Alpine-based pipelines and reduces network/source dependency drift.

## Maven Central publishing model

The module now supports publishing native binaries as classifier jars:

- `org.grimmory:epub4j-native:<version>`: Java API jar (no embedded native binary)
- `org.grimmory:epub4j-native:<version>:linux-x86_64`
- `org.grimmory:epub4j-native:<version>:linux-aarch64`
- `org.grimmory:epub4j-native:<version>:macos-x86_64`
- `org.grimmory:epub4j-native:<version>:macos-aarch64`
- `org.grimmory:epub4j-native:<version>:windows-x86_64`

Each classifier jar should contain exactly one native binary under `<platform-arch>/`.
Publishing does not require rebuilding everything locally if your CI uploads prebuilt binaries into `java/src/main/resources/<platform-arch>/` before `publish`.

Required classifier binaries for publication:

- `java/src/main/resources/linux-x86_64/libepub4j_native.so`
- `java/src/main/resources/linux-aarch64/libepub4j_native.so`
- `java/src/main/resources/macos-x86_64/libepub4j_native.dylib`
- `java/src/main/resources/macos-aarch64/libepub4j_native.dylib`
- `java/src/main/resources/windows-x86_64/epub4j_native.dll`

You can stage a built platform binary with:

```bash
./gradlew :epub4j-native:stageNativeClassifier \
	-Pclassifier=linux-aarch64 \
	-PnativeBinaryPath=/absolute/path/to/libepub4j_native.so
```

Supported classifier values are:

- `linux-x86_64`
- `linux-aarch64`
- `macos-x86_64`
- `macos-aarch64`
- `windows-x86_64`

The Gradle task `verifyNativeClassifiers` enforces this list before publishing.

### Generate all classifier binaries automatically (recommended)

Use the GitHub Actions workflow `.github/workflows/native-classifiers.yml`.

It builds exactly these targets on native runners:

- `linux-x86_64/libepub4j_native.so`
- `linux-aarch64/libepub4j_native.so`
- `macos-x86_64/libepub4j_native.dylib`
- `macos-aarch64/libepub4j_native.dylib`
- `windows-x86_64/epub4j_native.dll`

Run the workflow manually from GitHub Actions:

1. Open `Build Native Classifiers`
2. Click `Run workflow`
3. Choose `commit_to_branch=true` if you want CI to commit binaries directly into `java/src/main/resources/<classifier>/`

If `commit_to_branch=false`, download artifacts from the workflow run and stage them with:

```bash
./gradlew :epub4j-native:stageNativeClassifier -Pclassifier=linux-aarch64 -PnativeBinaryPath=/path/to/libepub4j_native.so
./gradlew :epub4j-native:stageNativeClassifier -Pclassifier=macos-x86_64 -PnativeBinaryPath=/path/to/libepub4j_native.dylib
./gradlew :epub4j-native:stageNativeClassifier -Pclassifier=macos-aarch64 -PnativeBinaryPath=/path/to/libepub4j_native.dylib
./gradlew :epub4j-native:stageNativeClassifier -Pclassifier=windows-x86_64 -PnativeBinaryPath=/path/to/epub4j_native.dll
```

## Publishing to Maven Central

Set credentials in `~/.gradle/gradle.properties`:

```properties
centralPortalUsername=YOUR_USER
centralPortalPassword=YOUR_PASS
```

Then publish:

```bash
./gradlew :epub4j-native:publishNativeToCentral
```

This command verifies required native classifier binaries and publishes:

- Java API jar (`org.grimmory:epub4j-native`)
- sources jar and javadoc jar
- all native classifier jars

## License

MIT License (pugixml) + Apache 2.0 (Gumbo) + original epub4j license
