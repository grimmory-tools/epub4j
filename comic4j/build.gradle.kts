plugins {
    signing
    `maven-publish`
    alias(libs.plugins.jmh)
}

repositories {
    mavenCentral()
}

dependencies {
    api(libs.nightcompress)

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
    systemProperty("java.awt.headless", "true")
    // NightCompress needs libarchive on the library path
    val libPaths = listOf("/lib64", "/usr/lib", "/usr/local/lib", "/opt/homebrew/lib")
        .filter { file(it).isDirectory }
    if (libPaths.isNotEmpty()) {
        systemProperty("java.library.path", libPaths.joinToString(separator = File.pathSeparator))
    }
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
    val signingKey: String? = findProperty("signingKey") as String?
    val signingPassword: String? = findProperty("signingPassword") as String?
    if (signingKey != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
    }
    sign(publishing.publications)
}

tasks.withType<Sign>().configureEach {
    onlyIf {
        findProperty("signingKey") != null || System.getenv("CI") == null
    }
}

val centralUser: String = findProperty("centralPortalUsername") as String? ?: ""
val centralPass: String = findProperty("centralPortalPassword") as String? ?: ""

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = "org.grimmory"
            artifactId = "comic4j"
            version = project.version.toString()
            pom {
                name = "comic4j"
                description = "A Java library for reading, writing, and manipulating comic book archives (CBZ, CBR, CB7, CBT) with ComicInfo.xml support."
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
        maven {
            name = "MavenCentral"
            url = uri("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/")
            credentials {
                username = centralUser
                password = centralPass
            }
        }
    }
}
