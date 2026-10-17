pluginManagement {
    includeBuild("build-logic")
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        google()
        mavenCentral()
    }
}

rootProject.name = "JianZhang"
include(
    ":app",
    ":core:model",
    ":core:common",
    ":core:database",
    ":core:data",
    ":core:navigation",
    ":core:designsystem",
    ":feature:overview",
    ":feature:ledger",
    ":feature:record",
    ":feature:stats",
    ":feature:accounts",
    ":feature:planning",
    ":feature:analysis",
    ":feature:settings",
    ":feature:lock",
)