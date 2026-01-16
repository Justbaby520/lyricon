@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://api.xposed.info/") }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        maven { url = uri("https://jitpack.io") }
        google()
        mavenCentral()
        maven { url = uri("https://api.xposed.info/") }
    }
}

include(
    ":lyricon",
    ":app",
    ":bridge",
    ":xposed",
    ":common",
)

include(":lyric:bridge:central")
include(":lyric:bridge:provider")

include(":lyric:model")
include(":lyric:view")
include(":lyric:style")
include(":lyric:viewAppTest")

rootProject.name = "LyriconProject"