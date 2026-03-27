plugins {
    signing
    `maven-publish`
    alias(libs.plugins.gversion)
    alias(libs.plugins.jmh)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kxml2)
    implementation(libs.xmlpull)
    api(libs.nightcompress)

    // Optional: when epubcheck is on classpath, EpubValidator uses it for deep validation
    compileOnly(libs.epubcheck)

    // Detected at runtime via reflection so consumers get native acceleration transparently
    runtimeOnly(project(":epub4j-native"))

    testImplementation(libs.epubcheck)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    jmh(libs.jmh.core)
    jmhAnnotationProcessor(libs.jmh.generator.annprocess)
}

jmh {
    warmupIterations = 2
    iterations = 3
    fork = 1
    jvmArgs = listOf("--enable-preview", "--enable-native-access=ALL-UNNAMED")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    // NightCompress needs libarchive on the library path
    val libPaths = listOf("/lib64", "/usr/lib", "/usr/local/lib", "/opt/homebrew/lib")
        .filter { file(it).isDirectory }
    if (libPaths.isNotEmpty()) {
        systemProperty("java.library.path", libPaths.joinToString(separator = File.pathSeparator))
    }
}

gversion {
    srcDir = "src/main/java/"
    classPackage = "org.grimmory.epub4j.util"
    className = "GVersion"
    dateFormat = "yyyy-MM-dd'T'HH:mm:ss z"
    timeZone = "UTC"
    debug = false
    language = "java"
    explicitType = false
}

tasks.named("compileJava") {
    dependsOn("createVersionFile")
}

tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).apply {
        addBooleanOption("Xdoclint:none", true)
        addStringOption("sourcepath", "")
        addBooleanOption("html5", true)
        addBooleanOption("-enable-preview", true)
        source = "25"
    }
    isFailOnError = false
}

java {
    withJavadocJar()
    withSourcesJar()
}

signing {
    val signingKey = findProperty("signingKey") as String? ?: System.getenv("GPG_PRIVATE_KEY")
    val signingPassword = findProperty("signingPassword") as String? ?: System.getenv("GPG_PASSPHRASE")
    if (signingKey != null && signingPassword != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications)
    }
}

// nmcp plugin in settings.gradle.kts handles Maven Central publishing;
// only mavenLocal is needed here for local testing.
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = "org.grimmory"
            artifactId = "epub4j-core"
            version = project.version.toString()
            pom {
                name = "epub4j"
                description = "A Java library for reading, writing, and manipulating EPUB files."
                url = "https://github.com/grimmory-tools/epub4j"
                packaging = "jar"
                licenses {
                    license {
                        name = "The Apache License, Version 2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
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
