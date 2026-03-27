plugins {
    application
    alias(libs.plugins.shadow)
}

dependencies {
    implementation(project(":epub4j-core"))

    implementation(libs.htmlcleaner) {
        exclude(group = "org.jdom", module = "jdom")
        exclude(group = "org.apache.ant", module = "ant")
    }
    implementation(libs.commons.vfs2)
    implementation(libs.commons.lang3)
    implementation(libs.commons.io)
    implementation(libs.commons.text)
}

application {
    mainClass = "org.grimmory.epub4j.Fileset2Epub"
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>().configureEach {
    manifest {
        attributes(
            "Implementation-Title" to "Fileset2Epub Command Line Tool",
            "Main-Class" to "org.grimmory.epub4j.Fileset2Epub",
        )
    }
}
