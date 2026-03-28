plugins {
    alias(libs.plugins.axion.release)
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.spotbugs) apply false
}

// nmcp (settings plugin) injects a :nmcpTasks configuration into the root project;
// it needs a repository to resolve com.gradleup.nmcp:nmcp-tasks at task execution time.
repositories {
    mavenCentral()
}

scmVersion {
    repository {
        type.set("git")
        // SSH key only needed when pushing release tags to remote
        val rsaKey = file("${System.getProperty("user.home")}/.ssh/id_rsa")
        val ed25519Key = file("${System.getProperty("user.home")}/.ssh/id_ed25519")
        val sshKeyFile = when {
            rsaKey.exists() -> rsaKey
            ed25519Key.exists() -> ed25519Key
            else -> null
        }
        if (sshKeyFile != null) {
            customKeyFile.set(sshKeyFile)
            customKeyPassword.set("")
        }
    }
}

version = scmVersion.version

allprojects {
    group = "org.grimmory"
    version = rootProject.version
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "checkstyle")
    apply(plugin = "pmd")
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "com.github.spotbugs")

    repositories {
        mavenCentral()
    }

    val javaVersion = 25

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(javaVersion)
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release = javaVersion
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("--enable-preview"))
    }

    tasks.withType<Test>().configureEach {
        jvmArgs("--enable-preview")
    }

    tasks.withType<JavaExec>().configureEach {
        jvmArgs("--enable-preview")
    }

    dependencies {
        "compileOnly"("com.github.spotbugs:spotbugs-annotations:4.9.8")
        "testCompileOnly"("com.github.spotbugs:spotbugs-annotations:4.9.8")
        "testImplementation"(rootProject.libs.junit.jupiter)
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
        "testImplementation"(rootProject.libs.mockito.core)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    configure<CheckstyleExtension> {
        toolVersion = rootProject.libs.versions.checkstyle.get()
        configFile = rootProject.file("config/checkstyle/checkstyle.xml")
        isShowViolations = true
    }

    configure<PmdExtension> {
        toolVersion = rootProject.libs.versions.pmd.get()
        isConsoleOutput = true
        rulesMinimumPriority.set(5)
        ruleSetFiles = files(rootProject.file("config/pmd/ruleset.xml"))
        ruleSets = emptyList()
    }

    extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension>("spotless") {
        format("misc") {
            target("*.md", "*.kts", "*.gradle.kts", "**/*.yml", "**/*.yaml", "**/.gitignore")
            targetExclude("**/build/**", "**/.gradle/**", "**/cpp/**")
            trimTrailingWhitespace()
            endWithNewline()
        }
        java {
            target("src/*/java/**/*.java")
            targetExclude("**/build/**", "**/cpp/**", "**/util/GVersion.java")
            googleJavaFormat("1.35.0")
            removeUnusedImports()
            trimTrailingWhitespace()
            endWithNewline()
        }
    }

    tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
        excludeFilter.set(rootProject.file("config/spotbugs/exclude.xml"))
        reports.create("html") {
            required.set(true)
        }
        reports.create("xml") {
            required.set(false)
        }
    }

    tasks.withType<Checkstyle>().configureEach {
        exclude(
            "**/native_parsing/**/*.java",
            "**/native_parsing/EpubNative*.java",
            "**/native_parsing/*Headers.java"
        )
    }

    tasks.withType<Pmd>().configureEach {
        exclude(
            "**/native_parsing/**/*.java",
            "**/native_parsing/EpubNative*.java",
            "**/native_parsing/*Headers.java"
        )
    }

    tasks.named("check") {
        dependsOn("spotlessCheck")
    }
}

