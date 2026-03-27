rootProject.name = "epub4j"

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("com.gradleup.nmcp.settings") version "1.4.4"
}

nmcpSettings {
    centralPortal {
        username = providers.gradleProperty("sonatypeUsername")
            .orElse(providers.environmentVariable("SONATYPE_USERNAME"))
            .getOrElse("")
        password = providers.gradleProperty("sonatypePassword")
            .orElse(providers.environmentVariable("SONATYPE_PASSWORD"))
            .getOrElse("")
        publishingType = "AUTOMATIC"
    }
}

include("epub4j-core")
include("epub4j-tools")
include("epub4j-native")
include("comic4j")
