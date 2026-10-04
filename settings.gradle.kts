pluginManagement {
    repositories {
        // 国内网络访问 plugins.gradle.org 会被 TLS 劫持，改用阿里云镜像（内含 Gradle 插件门户镜像）
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "idea-dev-helper"
