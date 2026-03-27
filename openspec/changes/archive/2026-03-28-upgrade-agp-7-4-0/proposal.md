## Why

当前项目使用 Android Gradle Plugin (AGP) 4.2.2 和 Gradle 6.7.1，这些版本已过时。升级到 AGP 7.4.0 可以获得更好的构建性能、对新版 JDK 的支持、以及最新的 Android 开发工具链兼容性。

本项目核心是 `auto-service-plugin` Gradle 插件，它直接依赖 AGP API 来：
- 遍历 `applicationVariants` 获取构建变体
- 通过 `javaCompileProvider` 获取编译 classpath 和输出目录
- 使用 `bootClasspath` 获取 Android SDK 类路径
- 依赖 `assembleProvider` 设置任务依赖关系

AGP 7.x 对这些 API 有重要变更，需要适配以确保插件继续正常工作。

## What Changes

### 构建环境升级
- 升级 Android Gradle Plugin 从 4.2.2 到 7.4.0
- 升级 Gradle Wrapper 从 6.7.1 到 7.5（AGP 7.4.0 要求最低 Gradle 7.2）
- 升级 Kotlin 版本从 1.5.31 到 1.8.10

### App 模块 DSL 语法适配
- **BREAKING**: 将 `compileSdkVersion` 改为 `compileSdk`
- **BREAKING**: 将 `targetSdkVersion` 改为 `targetSdk`
- **BREAKING**: 将 `minSdkVersion` 改为 `minSdk`
- **BREAKING**: 移除 `buildToolsVersion`（AGP 7.x 自动管理）

### 插件模块 API 适配
- 更新 AGP compileOnly/implementation 依赖版本到 7.4.0
- 适配 `AppExtension` → `ApplicationExtension`（类名变更）
- 适配 `applicationVariants` API（返回类型变更）
- 验证 `javaCompileProvider`、`bootClasspath`、`assembleProvider` API 兼容性

### 文档补充
- 新增 `agp-7-4-plugin-guide` 规格，记录 AGP 7.4.0 API 变化和自定义插件开发流程

## Capabilities

### New Capabilities

无新能力引入。

### Modified Capabilities

- `gradle-build-config`: 构建配置升级，适配 AGP 7.4.0 DSL 语法
- `plugin-agp-api`: 插件 AGP API 适配，确保与 AGP 7.x 兼容

## Impact

| 文件/模块 | 变更内容 |
|-----------|----------|
| `build.gradle` (根目录) | AGP 版本 4.2.2 → 7.4.0，Kotlin 版本 1.5.31 → 1.8.10 |
| `gradle-wrapper.properties` | Gradle 版本 6.7.1 → 7.5 |
| `app/build.gradle` | DSL 语法变更（compileSdk/targetSdk/minSdk） |
| `auto-service-plugin/build.gradle` | AGP 依赖版本升级到 7.4.0 |
| `AutoServiceRegisterPlugin.groovy` | API 适配（AppExtension → ApplicationExtension 等） |

**编译要求**: JDK 11+（AGP 7.x 强制要求）