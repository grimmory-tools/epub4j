import java.util.Locale

plugins {
    `java-library`
    signing
    `maven-publish`
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
    withJavadocJar()
    withSourcesJar()
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// -- Platform helpers --

fun normalizeArch(arch: String?): String {
    val lower = (arch ?: "x86_64").lowercase(Locale.ROOT)
    return when (lower) {
        "amd64", "x86_64" -> "x86_64"
        "aarch64", "arm64" -> "aarch64"
        "x86", "i386", "i486", "i586", "i686" -> "x86"
        else -> lower
    }
}

fun nativeLibNameForOs(osName: String): String = when {
    osName.contains("win") -> "epub4j_native.dll"
    osName.contains("mac") -> "libepub4j_native.dylib"
    else -> "libepub4j_native.so"
}

val platformLibraryName = mapOf(
    "linux-x86_64"       to "libepub4j_native.so",
    "linux-aarch64"      to "libepub4j_native.so",
    "linux-musl-x86_64"  to "libepub4j_native.so",
    "linux-musl-aarch64" to "libepub4j_native.so",
    "macos-x86_64"       to "libepub4j_native.dylib",
    "macos-aarch64"      to "libepub4j_native.dylib",
    "windows-x86_64"     to "epub4j_native.dll",
)

val requiredNativeClassifiers = listOf(
    "linux-x86_64",
    "linux-aarch64",
    "linux-musl-x86_64",
    "linux-musl-aarch64",
    "macos-x86_64",
    "macos-aarch64",
    "windows-x86_64",
)

// -- Native C++ build tasks --

val buildNative by tasks.registering(Exec::class) {
    description = "Configure CMake build for native C++ library"
    workingDir = file("cpp")

    val os = System.getProperty("os.name").lowercase()
    val cmakeArgs = mutableListOf("-B", "build")

    if (project.findProperty("useSystemNativeDeps")?.toString()?.toBoolean() == true) {
        cmakeArgs += listOf("-DEPUB4J_NATIVE_USE_SYSTEM_PUGIXML=ON", "-DEPUB4J_NATIVE_USE_SYSTEM_GUMBO=ON")
    }
    if (os.contains("win")) {
        // Ninja is single-config: set build type at configure time and direct the DLL to Release/
        cmakeArgs += listOf("-G", "Ninja", "-DCMAKE_BUILD_TYPE=Release", "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY=Release")
    } else {
        // Single-config generators (Unix Makefiles) ignore --config; set build type at configure time
        cmakeArgs += "-DCMAKE_BUILD_TYPE=Release"
    }

    commandLine(listOf("cmake") + cmakeArgs)
}

val buildNativeRelease by tasks.registering(Exec::class) {
    description = "Build native C++ library in release mode"
    workingDir = file("cpp/build")
    dependsOn(buildNative)
    commandLine("cmake", "--build", ".", "--config", "Release")
}

val copyNativeLibrary by tasks.registering(Copy::class) {
    description = "Copy native library to resources"
    dependsOn(buildNativeRelease)

    val os = System.getProperty("os.name").lowercase()
    val arch = normalizeArch(System.getProperty("os.arch"))
    val platform = when {
        os.contains("win") -> "windows-$arch"
        os.contains("mac") -> "macos-$arch"
        else -> "linux-$arch"
    }
    val libName = nativeLibNameForOs(os)

    if (os.contains("win")) {
        from(file("cpp/build/Release/$libName"))
    } else {
        from(file("cpp/build/$libName"))
    }
    into(file("java/src/main/resources/$platform"))
}

// -- Source sets: Java sources live in java/ subdirectory --

sourceSets {
    main {
        java { srcDir("java/src/main/java") }
        resources { srcDir("java/src/main/resources") }
    }
    test {
        java { srcDir("java/src/test/java") }
        resources { srcDir("java/src/test/resources") }
    }
}

// -- Testing --

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    dependsOn(buildNativeRelease)
    jvmArgs("--enable-native-access=ALL-UNNAMED")

    // Build places binaries in different subfolders across OSes/toolchains
    val libPath = listOf(
        "${projectDir}/cpp/build",
        "${projectDir}/cpp/build/Release",
        "${projectDir}/cpp/build/Debug",
    ).joinToString(separator = File.pathSeparator)
    jvmArgs("-Djava.library.path=$libPath")
}

// Keep native binaries out of the main jar; they are published as classifier artifacts.
tasks.named<Jar>("jar") {
    exclude(requiredNativeClassifiers.map { "$it/**" })
}

// -- Per-platform classifier JARs --

val nativeClassifierTasks = mutableListOf<TaskProvider<Jar>>()
val nativeResourceRoot = file("java/src/main/resources")
if (nativeResourceRoot.exists()) {
    nativeResourceRoot.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
        val nativeFiles = dir.listFiles()?.filter { f ->
            f.isFile && (f.name.endsWith(".so") || f.name.endsWith(".dylib") || f.name.endsWith(".dll"))
        } ?: emptyList()
        if (nativeFiles.isNotEmpty()) {
            val taskName = "nativeJar_${dir.name.replace(Regex("[^A-Za-z0-9_]+"), "_")}"
            val taskProvider = tasks.register<Jar>(taskName) {
                archiveBaseName = "epub4j-native"
                archiveClassifier = dir.name
                from(dir) { into(dir.name) }
            }
            nativeClassifierTasks.add(taskProvider)
        }
    }
}

// -- Verification --

val verifyNativeClassifiers by tasks.registering {
    group = "verification"
    description = "Verify required native classifier binaries exist for Maven Central publication."

    doLast {
        val missing = requiredNativeClassifiers.mapNotNull { classifier ->
            val fileName = platformLibraryName[classifier]!!
            val binary = file("java/src/main/resources/$classifier/$fileName")
            if (!binary.exists()) "$classifier/$fileName" else null
        }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Missing required native classifier binaries for publication:\n" +
                    missing.joinToString("\n") { " - $it" } +
                    "\n\nBuild or copy these binaries into java/src/main/resources/<platform>/ before publishing."
            )
        }
    }
}

val stageNativeClassifier by tasks.registering(Copy::class) {
    group = "publishing"
    description = "Stage a native binary into java/src/main/resources/<classifier>/ for publication."

    val classifier = project.findProperty("classifier")?.toString()
    val nativeBinaryPath = project.findProperty("nativeBinaryPath")?.toString()

    doFirst {
        requireNotNull(classifier) { "Missing -Pclassifier. Expected one of: ${requiredNativeClassifiers.joinToString()}" }
        require(classifier in requiredNativeClassifiers) {
            "Unsupported classifier '$classifier'. Expected one of: ${requiredNativeClassifiers.joinToString()}"
        }
        requireNotNull(nativeBinaryPath) { "Missing -PnativeBinaryPath pointing to compiled native library file" }
    }

    from(provider { file(nativeBinaryPath!!) })
    into(provider { file("java/src/main/resources/$classifier") })
    rename { platformLibraryName[classifier]!! }
}

// -- Javadoc --

tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions).apply {
        addStringOption("Xdoclint:none", "-quiet")
        addBooleanOption("-enable-preview", true)
        source = "25"
    }
    isFailOnError = false
}

// -- Signing --

signing {
    val signingKey = findProperty("signingKey") as String? ?: System.getenv("GPG_PRIVATE_KEY")
    val signingPassword = findProperty("signingPassword") as String? ?: System.getenv("GPG_PASSPHRASE")
    if (signingKey != null && signingPassword != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
    }
}

afterEvaluate {
    signing {
        val signingKey = findProperty("signingKey") as String? ?: System.getenv("GPG_PRIVATE_KEY")
        if (signingKey != null) {
            sign(publishing.publications)
        }
    }
}

// -- Publishing --
// nmcp plugin in settings.gradle.kts handles Maven Central publishing;
// only mavenLocal is needed here for local testing.
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = "org.grimmory"
            artifactId = "epub4j-native"
            version = project.version.toString()

            nativeClassifierTasks.forEach { taskProvider ->
                artifact(taskProvider)
            }

            pom {
                name = "epub4j Native"
                description = "Native C++ XML/HTML parsing for epub4j via Panama FFM"
                url = "https://github.com/grimmory-tools/epub4j"
                packaging = "jar"
                licenses {
                    license {
                        name = "GNU Affero General Public License v3.0"
                        url = "https://www.gnu.org/licenses/agpl-3.0.txt"
                    }
                }
                developers {
                    developer {
                        id = "balazs-szucs"
                        name = "Balazs Szucs"
                        email = "bszucs1209@gmail.com"
                        organization = "Grimmory-tools"
                        organizationUrl = "https://github.com/grimmory-tools"
                    }
                }
                scm {
                    connection = "scm:git:git@github.com:grimmory-tools/epub4j.git"
                    developerConnection = "scm:git:git@github.com:grimmory-tools/epub4j.git"
                    url = "https://github.com/grimmory-tools/epub4j"
                }
            }
        }
    }
    repositories {
        mavenLocal()
    }
}

tasks.withType<PublishToMavenRepository>().configureEach {
    dependsOn(verifyNativeClassifiers)
}

tasks.register("publishNativeToCentral") {
    group = "publishing"
    description = "Verify classifier binaries then publish epub4j-native and all classifier artifacts via nmcp."
    dependsOn(verifyNativeClassifiers)
    // nmcp's publishAggregationToCentralPortal handles upload + release
}

tasks.register("nativeAll") {
    description = "Build everything (native + Java)"
    dependsOn(buildNativeRelease, tasks.named("build"))
}
