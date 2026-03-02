
pluginManagement {
    repositories {
        mavenCentral()
        google()
       gradlePluginPortal()
        maven {
            url = uri("https://www.jitpack.io")
            credentials { username = "jp_v5dov1ma3oe85c8t4qe92duvqt"}

        }
    }
}
plugins {
  id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
        maven {
            url  = uri("https://www.jitpack.io")
            credentials { username ="jp_v5dov1ma3oe85c8t4qe92duvqt"}
        }
    }
}

rootProject.name = "YkisPAM"
include(":app")
