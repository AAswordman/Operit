pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://dl.bintray.com/rikkaw/Shizuku") }
        maven { url = uri("https://api.xposed.info/") }
        maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots") }
    }
}

rootProject.name = "Operit"
include(":app")
include(":terminal")
include(":mnn")
project(":mnn").projectDir = file("llm/mnn")
include(":llama")
project(":llama").projectDir = file("llm/llama")
include(":showerclient")
include(":quickjs")

include(":a2ui-compat")

include(":material-icons-subset")
