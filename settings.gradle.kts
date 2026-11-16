pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Maven Central 优先：dl.google.com 在当前网络不可达，
        // 而 AndroidX / Kotlin 制品在 Central 上都有镜像
        mavenCentral()
        // dl.google.com 不可达，改用阿里云的 Google Maven 镜像
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
    }
}

rootProject.name = "FormulaStudio"
include(":app")
include(":core")
