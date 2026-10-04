plugins {
    id("java")
    // 说明：由于需要兼容 IntelliJ IDEA 2022.1，必须使用旧的 Gradle IntelliJ Plugin 1.x。
    // 新的 IntelliJ Platform Gradle Plugin 2.x 最低只支持目标平台 2023.3。
    // 1.10.1 与 Gradle 7.6.4（内嵌 Kotlin 1.7.10）兼容；1.17.x 需要 Gradle 8.4+。
    id("org.jetbrains.intellij") version "1.10.1"
}

group = "com.ideahelper"
version = "1.0.0"

repositories {
    // 优先走国内镜像，回退到中央仓库
    maven("https://maven.aliyun.com/repository/public")
    mavenCentral()
}

java {
    // IDEA 2022.1 运行在 Java 11 上，因此字节码目标设为 11，
    // 这样编译产物既能被 2022.1 加载，也能被更新的 IDE（21/25）加载。
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    // 仅使用 IntelliJ 平台自带的 Gson（com.google.gson），不额外打包任何第三方库。
}

intellij {
    // 针对 2022.1 编译，保证只用到该版本就存在的 API
    version.set("2022.1.4")
    type.set("IC")
    plugins.set(listOf())
    updateSinceUntilBuild.set(false)
    downloadSources.set(false)
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-Xlint:none")
    }

    patchPluginXml {
        // 兼容 2022.1 及之后所有版本（不设置 untilBuild 即无上限）
        sinceBuild.set("221")
        untilBuild.set(provider { null })
        pluginDescription.set(
            """
            <p><b>Dev Helper</b> 是一个面向 IntelliJ IDEA 的本地开发助手插件，包含两个实用功能：</p>
            <ul>
              <li><b>CodeBuddy 积分</b>：在右下角状态栏显示当前 CodeBuddy 账号的剩余积分，鼠标悬停查看明细；
                  右侧工具栏提供完整视图，支持一键签到、刷新、查看套餐余量。</li>
              <li><b>JRebel 激活服务</b>：右侧工具栏内置一个本地 JRebel License Server，可一键启停、
                  自定义端口，并生成可用于激活的 UUID 与激活地址。</li>
            </ul>
            <p>兼容 IntelliJ IDEA 2022.1 及之后的所有版本。</p>
            """.trimIndent()
        )
        changeNotes.set("<ul><li>首个版本：CodeBuddy 积分状态栏 / 签到，JRebel 本地激活服务。</li></ul>")
    }

    // 构建可搜索选项会临时启动 IDE，无实际价值，关闭以加快构建
    buildSearchableOptions {
        enabled = false
    }
    signPlugin {
        enabled = false
    }
    publishPlugin {
        enabled = false
    }
}
