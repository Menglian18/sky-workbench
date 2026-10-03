pluginManagement {
    repositories {
        // 国内镜像优先（快），官方仓库保底
        maven { url = uri("https://mirrors.huaweicloud.com/repository/maven/") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://mirrors.huaweicloud.com/repository/maven/") }
        google()
        mavenCentral()
    }
}

rootProject.name = "nana-workbench"
include(":app")
