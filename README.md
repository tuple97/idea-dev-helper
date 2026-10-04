# Dev Helper · IntelliJ IDEA 插件

把两个本地开发小工具装进 IntelliJ IDEA：**CodeBuddy 剩余积分** 与 **JRebel 本地激活服务**。

兼容 **IntelliJ IDEA 2022.1 及之后的所有版本**（`since-build = 221`，不设上限）。

## 功能

### CodeBuddy 剩余积分

- 右下角**状态栏**常驻显示剩余积分；鼠标悬停查看总积分 / 剩余积分 / 剩余占比 / 更新时间；**点击即刷新**
- 右侧工具栏 `Dev Helper → CodeBuddy`：大字余量、套餐表格、**一键签到**、刷新、打开用量网页
- **自动读取登录态**：读取 IntelliJ 系 IDE 中 CodeBuddy 插件保存的登录态，支持多 IDE 自动扫描；读不到时可手动粘贴 Access Token
- 可配置刷新间隔、自动签到、喵喵旅行

### JRebel 本地激活服务

- 右侧工具栏 `Dev Helper → JRebel`：一键**启停**本地 License Server
- **端口**、协议、离线天数、工作模式可调
- **生成 UUID** 并一键复制 UUID / 激活地址，直接用于 JRebel 激活

## 安装

### 从 Release 安装（推荐）

下载本仓库 Releases 中的 `idea-dev-helper-<版本>.zip`，然后：
`Settings/Preferences → Plugins → 齿轮 ⚙ → Install Plugin from Disk...` 选择该 zip，重启 IDEA。

### 从源码构建后安装

见「本地开发与构建」，产物在 `build/distributions/idea-dev-helper-<版本>.zip`。

## 本地开发与构建

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| JDK | **17** | 运行 Gradle 与编译（产物字节码为 Java 11，兼容 2022.1 运行时） |
| Gradle | 由 **Gradle Wrapper** 提供（7.6.4） | 无需自行安装 |
| IntelliJ Platform | 2022.1.4 (IC) | 编译靶子，构建时自动下载到 Gradle 缓存 |

构建：

```powershell
cd <项目目录>
.\gradlew.bat buildPlugin      # 产物：build/distributions/idea-dev-helper-<版本>.zip
```

调试：在 IDEA 里以 Gradle 项目打开本仓库，Gradle 工具窗 → `Tasks → intellij → runIde`，右键选 **Debug**。
它会启动一个 **IDEA 2022.1.4 Community 沙箱**并自动装上插件，可直接打断点。沙箱的配置 / 日志 / 插件目录分别是
`build/idea-sandbox/` 下的 `config`、`system/log`、`plugins`。

注意事项：

- **构建前先关闭沙箱窗口**，否则 `prepareSandbox` 会因 jar 被占用而失败。
- 因为要兼容 2022.1，必须使用旧版 Gradle 插件 `org.jetbrains.intellij` **1.x**（2.x 最低只支持目标平台 2023.3）。
- wrapper 使用 **`-all`** 发行版：它自带 `src/`，可避免 IDEA 额外去下载 `gradle-<版本>-src.zip`。

## 项目结构

```
src/main/
├── java/com/ideahelper/
│   ├── cb/          CodeBuddy：登录态读取与鉴权、接口、服务、状态栏、面板
│   ├── jrebel/      JRebel：许可签名、本地激活服务、配置、面板
│   ├── ui/          工具窗工厂、图标
│   └── util/        Gson 封装
└── resources/
    ├── META-INF/plugin.xml
    └── icons/       状态栏 / 工具栏 / GitHub 图标
```

## 参考来源

| 功能 | 参考项目 |
| --- | --- |
| CodeBuddy 剩余积分 / 签到 | [wwenc6621/CodeBuddy-Usage](https://github.com/wwenc6621/CodeBuddy-Usage)（VS Code 扩展，MIT） |
| JRebel 本地激活服务 | [yu-xiaoyao/jrebel-license-active-server](https://github.com/yu-xiaoyao/jrebel-license-active-server)（Go 实现，MIT） |

- CodeBuddy 部分按该 VS Code 扩展的逻辑用 Java 重写；**登录态来源不同**——本插件读取的是 **IntelliJ 系 IDE 中 CodeBuddy 插件**保存的登录态。
- JRebel 部分按该 Go 服务用 Java 重写，签名私钥与接口行为保持一致。

## 许可证

[MIT](LICENSE)，并保留了两个上游项目的版权声明。

## 免责声明

- 插件只在**本机**读取你自己的 CodeBuddy 登录态，用于展示积分余量；凭据不会上传到任何第三方。
- JRebel 激活服务仅供本地学习与测试使用，请在条件允许时支持正版。
