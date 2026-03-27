rootProject.name = "epub4j"

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("com.gradleup.nmcp.settings") version "1.4.4"
}

nmcpSettings {
    centralPortal {
        username = providers.gradleProperty("centralPortalUsername")
            .orElse(providers.environmentVariable("CENTRAL_PORTAL_USERNAME"))
            .getOrElse("")
        password = providers.gradleProperty("centralPortalPassword")
            .orElse(providers.environmentVariable("CENTRAL_PORTAL_PASSWORD"))
            .getOrElse("")
        // Uploads are automatically released without manual confirmation
        publishingType = "AUTOMATIC"
    }
}

include("epub4j-core")
include("epub4j-tools")
include("epub4j-native")
include("comic4j")
