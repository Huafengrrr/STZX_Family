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
        // 1. 阿里云公共代理仓库（极其重要，国内下载基础库全靠它加速）
        maven { url = uri("https://maven.aliyun.com/repository/public") }

        google()
        mavenCentral()

        // 2. 允许下载 Github 上的其他开源库
        maven { url = uri("https://jitpack.io") }
    }
      }

rootProject.name = "STZX_Family"
include(":app")