plugins {
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.mavenPublish) apply false
}

subprojects {
    pluginManager.withPlugin("com.vanniktech.maven.publish") {
        extensions.configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
            publishToMavenCentral()
            if (providers.gradleProperty("signingInMemoryKey").isPresent) signAllPublications()
            pom {
                url = "https://github.com/DabbleStudios/protomorphic"
                inceptionYear = "2026"
                licenses {
                    license {
                        name = "The Apache License, Version 2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                        distribution = "repo"
                    }
                }
                developers {
                    developer {
                        id = "DabbleStudios"
                        name = "Dabble Studios"
                        url = "https://github.com/DabbleStudios"
                    }
                }
                scm {
                    url = "https://github.com/DabbleStudios/protomorphic"
                    connection = "scm:git:https://github.com/DabbleStudios/protomorphic.git"
                    developerConnection = "scm:git:ssh://git@github.com/DabbleStudios/protomorphic.git"
                }
            }
        }
    }
}
