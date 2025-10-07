pluginManagement {
    repositories {
        google()
        mavenCentral()
        maven(url = "https://chaquo.com/maven")
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Zam"
include(":app")
 