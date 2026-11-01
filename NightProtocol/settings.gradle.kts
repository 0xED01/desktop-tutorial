pluginManagement {
    repositories {
        google()
        // Google 托管的 Maven Central 镜像（缓解 repo.maven.apache.org 限流）
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Google 托管的 Maven Central 镜像（缓解 repo.maven.apache.org 限流）
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
    }
}
rootProject.name = "NightProtocol"
include(":app")
