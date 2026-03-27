## Context

当前项目是一个 Android Gradle 插件项目，核心模块 `auto-service-plugin` 直接依赖 AGP API：
- AGP 4.2.2（发布于 2021 年）
- Gradle 6.7.1
- Kotlin 1.5.31
- JDK 8

AGP 7.4.0 是一个重要里程碑版本，带来：
- JDK 11+ 要求（强制）
- 新的 Variant API（`AndroidComponentsExtension`）
- DSL 语法简化（`compileSdkVersion` → `compileSdk`）
- 更好的构建性能和配置缓存支持
- 兼容 Android Studio 2022.1+（Chipmunk）

### 插件当前使用的 AGP API

```groovy
// AutoServiceRegisterPlugin.groovy 关键代码
import com.android.build.gradle.AppExtension      // 需验证路径
import com.android.build.gradle.AppPlugin         // 包名不变

// 获取 Android 扩展
final android = project.extensions.getByType(AppExtension.class)

// 遍历构建变体
android.applicationVariants.forEach { variant ->
    // 获取 classpath
    variant.javaCompileProvider.get().classpath
    variant.javaCompileProvider.get().destinationDir

    // 获取 boot classpath
    android.bootClasspath

    // 设置任务依赖
    variant.assembleProvider.get().dependsOn(...)
}
```

## Goals / Non-Goals

**Goals:**
- 升级到 AGP 7.4.0，确保项目能正常编译和运行
- 适配 AGP 7.x 的 DSL 语法变更（compileSdk 等）
- 验证并适配 AGP 7.x 的插件 API 变更
- 保持插件核心功能不变（ServiceLoader 注册机制）
- 确保 buildSrc 模式调试正常工作
- 记录 AGP 7.4.0 的 API 变化和自定义插件开发流程

**Non-Goals:**
- 不迁移到 Kotlin DSL（保持 Groovy DSL）
- 不重构插件内部实现逻辑
- 不迁移到新的 `AndroidComponentsExtension` API（保持现有 `applicationVariants`）
- 不更改版本发布流程
- 不升级到 AGP 8.x（AGP 8.x 有更多 API 变更）

## Decisions

### 1. Gradle 版本选择：7.5

- **原因**: AGP 7.4.0 要求最低 Gradle 7.2，Gradle 7.5 是稳定版本
- **替代方案**: Gradle 7.6.x（更激进，可能引入额外风险）
- **变更文件**: `gradle/wrapper/gradle-wrapper.properties`
- **变更内容**: `distributionUrl=https\://services.gradle.org/distributions/gradle-7.5-bin.zip`

### 2. Kotlin 版本选择：1.8.10

- **原因**: AGP 7.4.0 兼容 Kotlin 1.6.x - 1.8.x，1.8.10 是稳定版本
- **替代方案**: Kotlin 1.9.x（需要验证 AGP 兼容性）
- **变更文件**: `build.gradle`（根目录）
- **变更内容**: `ext.kotlin_version = "1.8.10"`

### 3. JDK 要求：11+

- **原因**: AGP 7.x 强制要求 JDK 11+，JDK 11 是 LTS 版本
- **影响**: 所有开发者和 CI 环境需要升级 JDK
- **验证**: `java -version` 应显示 11 或更高版本

### 4. 插件 AGP 依赖升级策略

当前 `buildSrc/build.gradle` 和 `auto-service-plugin/build.gradle` 使用极旧的 AGP 版本：

```groovy
// 当前版本（需要升级）
compileOnly("com.android.tools.build:gradle:1.3.1")   // 极旧
implementation("com.android.tools.build:gradle:3.5.0") // 旧

// 升级后
compileOnly("com.android.tools.build:gradle:7.4.0")
implementation("com.android.tools.build:gradle:7.4.0")
```

**原因**: 统一使用 7.4.0，确保 API 一致性，避免版本冲突。

### 5. AGP API 适配详细分析

#### 5.1 AppPlugin 类路径

| 版本 | 类路径 | 变化 |
|------|--------|------|
| AGP 4.x | `com.android.build.gradle.AppPlugin` | 原位置 |
| AGP 7.x | `com.android.build.gradle.AppPlugin` | **保持不变** |

**结论**: `AppPlugin` 类路径不变，无需修改。

#### 5.2 AppExtension 类路径

| 版本 | 类路径 | 变化 |
|------|--------|------|
| AGP 4.x | `com.android.build.gradle.AppExtension` | 原位置 |
| AGP 7.x | `com.android.build.gradle.AppExtension` | **保持不变** |

**重要**: 在 AGP 7.x 中，`AppExtension` 是 `ApplicationExtension` 的别名/子类，两者都可用。
推荐继续使用 `AppExtension` 以保持向后兼容。

#### 5.3 applicationVariants API

| 版本 | API | 返回类型 | 变化 |
|------|-----|----------|------|
| AGP 4.x | `applicationVariants` | `DomainObjectSet<ApplicationVariant>` | - |
| AGP 7.x | `applicationVariants` | `DomainObjectSet<ApplicationVariant>` | **保持兼容** |

**结论**: `applicationVariants` API 在 AGP 7.x 中完全兼容，无需修改遍历逻辑。

#### 5.4 javaCompileProvider API

| 版本 | API | 变化 |
|------|-----|------|
| AGP 4.x | `variant.javaCompileProvider.get().classpath` | 返回 `FileCollection` |
| AGP 7.x | `variant.javaCompileProvider.get().classpath` | **保持不变** |
| AGP 4.x | `variant.javaCompileProvider.get().destinationDir` | 返回 `File` |
| AGP 7.x | `variant.javaCompileProvider.get().destinationDir` | **保持不变** |

**结论**: `javaCompileProvider` 相关 API 在 AGP 7.x 中完全兼容。

#### 5.5 bootClasspath API

| 版本 | API | 返回类型 | 变化 |
|------|-----|----------|------|
| AGP 4.x | `android.bootClasspath` | `List<File>` | - |
| AGP 7.x | `android.bootClasspath` | `List<File>` | **保持不变** |

**结论**: `bootClasspath` API 完全兼容。

#### 5.6 assembleProvider API

| 版本 | API | 变化 |
|------|-----|------|
| AGP 4.x | `variant.assembleProvider.get()` | 返回 `Task` |
| AGP 7.x | `variant.assembleProvider.get()` | **保持不变** |

**结论**: `assembleProvider` API 完全兼容。

#### 5.7 AGP 新 API（仅供参考，不使用）

AGP 7.x 引入了新的 `AndroidComponentsExtension` API，提供更强大的 Variant 处理能力：

```kotlin
// 新 API 示例（AGP 7.x+）
import com.android.build.api.variant.ApplicationAndroidComponentsExtension

val androidComponents = project.extensions.getByType(ApplicationAndroidComponentsExtension::class.java)
androidComponents.onVariants { variant ->
    // 使用 Property-based API
    variant.artifacts.use(taskProvider)
        .wiredWithFiles(...)
        .toTransform(SingleArtifact.MERGED_MANIFEST)
}
```

**决策**: 保持使用旧的 `applicationVariants` API，原因：
1. 当前实现稳定，变更风险低
2. 旧 API 在 AGP 7.x 中完全兼容
3. 迁移到新 API 需要重构大量代码
4. 如果未来升级到 AGP 8.x，那时再考虑迁移

### 6. DSL 语法变更（app 模块）

| 旧语法 (AGP 4.x) | 新语法 (AGP 7.x) | 变化说明 |
|------------------|------------------|----------|
| `compileSdkVersion 30` | `compileSdk 33` | 方法名简化 |
| `targetSdkVersion 30` | `targetSdk 33` | 方法名简化 |
| `minSdkVersion 17` | `minSdk 17` | 方法名简化 |
| `buildToolsVersion "30.0.3"` | **删除** | AGP 7.x 自动管理 |

**变更文件**: `app/build.gradle`

## Risks / Trade-offs

| 风险 | 可能性 | 缓解措施 |
|------|--------|----------|
| AGP API 类名/包名变更导致编译失败 | 低 | 根据文档分析，核心 API 保持兼容 |
| `applicationVariants` API 返回类型变更 | 低 | 文档确认 AGP 7.x 保持兼容 |
| Javassist 不兼容 JDK 11+ 编译的 class 文件 | 低 | Javassist 3.28.0-GA 已支持 JDK 11+ |
| 用户 JDK 版本不满足要求 | 中 | 在 CLAUDE.md 中明确 JDK 11+ 要求 |
| 构建缓存可能导致问题 | 中 | 升级后首次构建执行 `./gradlew clean` |
| buildSrc 模式下的依赖冲突 | 中 | 确保 buildSrc 和 plugin 模块 AGP 版本一致 |
| `destinationDir` API 未来废弃 | 低（AGP 8.x 才废弃） | AGP 7.x 仍支持，暂不处理 |

## Migration Strategy

### Phase 1: Gradle 环境升级
1. 更新 Gradle wrapper 到 7.5
2. 更新根目录 build.gradle 的 AGP 版本到 7.4.0
3. 更根目录 build.gradle 的 Kotlin 版本到 1.8.10

### Phase 2: DSL 语法适配
1. 更新 app/build.gradle 的 DSL 语法
2. 移除 buildToolsVersion 配置

### Phase 3: 插件依赖升级
1. 更新 buildSrc/build.gradle 的 AGP 依赖
2. 更新 auto-service-plugin/build.gradle 的 AGP 依赖（如果单独存在）

### Phase 4: 插件 API 验证
1. 编译插件模块：`./gradlew :buildSrc:compileGroovy`
2. 验证所有 import 和 API 调用

### Phase 5: 验证和测试
1. 清理构建：`./gradlew clean`
2. 构建 app：`./gradlew :app:assembleDebug`
3. 运行 app，验证 ServiceLoader 功能

### Phase 6: 文档更新
1. 更新 CLAUDE.md，添加 JDK 11+ 要求