## Context

当前 auto-service-plugin 使用 Groovy 编写，基于 AGP 7.4.0 的 API。主要使用的 API 包括：

- `AppExtension` 获取 Android 扩展
- `android.applicationVariants` 遍历所有 variant
- `variant.javaCompileProvider` 获取 Java 编译任务
- `project.buildDir` 获取构建目录（已废弃）

当前 Gradle 版本为 7.5，Kotlin 版本为 1.8.10。AGP 8.13.0 要求 Gradle 至少 8.9，并且废弃了许多旧 API。

## AGP 8 API 变更详细对比

### 核心架构变化

```
┌─────────────────────────────────────────────────────────────┐
│                    AGP 7.x 旧模式                             │
├─────────────────────────────────────────────────────────────┤
│  AppExtension                                                 │
│  ├── applicationVariants 遍历变体                            │
│  ├── 使用 afterEvaluate 配置                                 │
│  └── 直接操作 Tasks                                          │
└─────────────────────────────────────────────────────────────┘

                            ↓ 迁移

┌─────────────────────────────────────────────────────────────┐
│                    AGP 8.x 新模式                             │
├─────────────────────────────────────────────────────────────┤
│  AndroidComponentsExtension                                  │
│  ├── onVariants() - 变体配置后回调                          │
│  ├── beforeVariants() - 变体创建前配置                        │
│  ├── finalizeSymlinks() - 最终符号链接配置                    │
│  └── onVariants(selector) - 精确的变体选择                   │
└─────────────────────────────────────────────────────────────┘
```

### 废弃 API 映射表

| 旧 API (AGP 7.x) | 新 API (AGP 8.x) | 说明 |
|------------------|------------------|------|
| `AppExtension` | `AndroidComponentsExtension` | 插件入口 |
| `applicationVariants` | `onVariants()` | 变体遍历 |
| `variant.javaCompileProvider` | `variant.getTaskProvider("compileJava")` | 编译任务 |
| `project.buildDir` | `project.layout.buildDirectory` | 构建目录 |
| `variant.getCompileConfiguration()` | `variant.compileConfiguration` | 依赖配置 |
| `variant.getVariantData()` | 直接使用 variant 对象 | 变体数据 |
| `variant.getAssemble()` | `variant.tasks.assemble` | assemble 任务 |
| `variant.name` | `variant.name` | 变体名称（保持不变） |
| `variant.buildType.name` | `variant.buildType` | build type |

### AndroidComponentsExtension 主要方法

```
beforeVariants { variant ->
    // 在变体创建前配置，可以修改变体属性
    // 此时尚未创建 Tasks
}

onVariants { variant ->
    // 在变体配置完成后操作，创建 Task
    // variant 编译任务已完成配置，可以获取
}

onVariants(selector().all()) { variant ->
    // 精确匹配特定变体类型
}

finalizeSymlinks { /* 配置符号链接 */ }
```

### AGP 8 插件开发流程

```
┌─────────────────┐
│  1. 插件入口     │
│  Plugin.apply() │
└────────┬────────┘
         │
         ▼
┌─────────────────────────────────────────┐
│  2. 注册扩展                             │
│  extensions.create("myExtension")         │
└────────┬────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────┐
│  3. 获取 AndroidComponentsExtension      │
│  project.extensions.getByType(...)     │
└────────┬────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────┐
│  4. 使用 onVariants() 注册回调            │
│  androidComponents.onVariants {           │
│      // 在此处创建和配置 Tasks             │
│  }                                       │
└────────┬────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────┐
│  5. 创建 Task                             │
│  project.tasks.register(...)             │
└────────┬────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────┐
│  6. 配置 Task 依赖关系                              │
│  task.configure {                         │
│      dependsOn(/*其他任务*/)              │
│  }                                        │
└─────────────────────────────────────────┘
```

### 代码对比示例

**AGP 7.4.0 方式**:
```groovy
void apply(Project project) {
    project.afterEvaluate {
        final android = project.extensions.getByType(AppExtension.class)
        android.applicationVariants.forEach { variant ->
            final classpath = variant.javaCompileProvider.get().classpath
            final workDir = project.file("${project.buildDir}/intermediates/...")
            // 创建 Task...
        }
    }
}
```

**AGP 8.13.0 方式**:
```groovy
void apply(Project project) {
    val androidComponents = project.extensions
        .getByType(AndroidComponentsExtension::class.java)

    androidComponents.onVariants { variant ->
        val classpath = variant.compileConfiguration
        val workDir = project.layout.buildDirectory
            .dir("intermediates/auto_service/${variant.name}")
            .get().asFile

        val task = project.tasks.register(
            "androidAutoServiceRegisterTask${variant.name.capitalize()}",
            AutoServiceRegisterTask.class
        )

        task.configure {
            it.setClasspath(classpath)
            it.setTargetDir(workDir)
        }
    }
}
```

## Goals / Non-Goals

**Goals:**
- 将 auto-service-plugin 迁移到 AGP 8.13.0 API
- 升级 Gradle 版本到 8.9 或更高
- 升级 Kotlin 版本到 2.1.0 或更高
- 使用 AGP 8.13.0 推荐的 API 而非已废弃的 API
- 保持插件功能不变（优先级排序、单例、别名、排除规则等）

**Non-Goals:**
- 不重构插件为 Kotlin（保持 Groovy 实现）
- 不添加新功能
- 不改变插件的基本架构和逻辑

## Decisions

### 1. 使用 AndroidComponentsExtension 而非 AppExtension

**决策**: 使用 `AndroidComponentsExtension` API 替代 `AppExtension`

**理由**:
- `AppExtension` 在 AGP 7.0+ 中已被标记为 experimental
- `AndroidComponentsExtension` 是 AGP 8.x 的标准 API
- 新 API 提供更好的类型安全性和生命周期控制

**替代方案考虑**: 继续使用 `AppExtension`（不可行，API 已废弃）

### 2. 使用 onVariants API 替代 applicationVariants

**决策**: 使用 `androidComponents.onVariants()` API 替代 `android.applicationVariants`

**理由**:
- `onVariants` 是 AGP 8.x 推荐的 variant 配置方式
- 提供更好的 Task 依赖管理
- 支持延迟求值，提高构建性能

**替代方案考虑**:
- 继续使用 `applicationVariants`（API 已废弃）
- 使用 `beforeVariants`（只能配置 variant，不能创建 Task）

### 3. 使用 ProjectLayout 替代 buildDir

**决策**: 使用 `project.layout.buildDirectory` 替代 `project.buildDir`

**理由**:
- `buildDir` 已被废弃
- `ProjectLayout` 提供类型安全的目录访问
- 符合 Gradle 现代化 API 规范

### 4. 使用 variant.artifacts 替代 javaCompileProvider

**决策**: 使用 `variant.artifacts` API 获取编译输出

**理由**:
- `javaCompileProvider` API 已被废弃
- `artifacts` API 提供更统一的产物访问方式
- 支持多种编译类型（Java、Kotlin 等）

### 5. 升级依赖版本

**决策**:
- Gradle: 7.5 → 8.9
- AGP: 7.4.0 → 8.13.0
- Kotlin: 1.8.10 → 2.1.0

**理由**:
- 满足 AGP 8.13.0 的最低要求
- 确保构建系统的兼容性
- Kotlin 2.x 提供更好的性能和安全性

### 6. 保持 Groovy 实现

**决策**: 不重构为 Kotlin，继续使用 Groovy

**理由**:
- 降低迁移风险
- 减少工作量
- Groovy 在 Gradle 插件中仍然是有效的选择

## Risks / Trade-offs

### 关键差异点

#### 1. 生命周期管理

```
AGP 7.x: afterEvaluate 模式
project.afterEvaluate {
    // 此时所有配置已解析，但执行时机不够精确
    // 所有变体已创建，但无法精确控制顺序
}

AGP 8.x: 专用生命周期钩子
androidComponents.beforeVariants { /* 变体创建前，可修改属性 */ }
androidComponents.onVariants { /* 变体配置后，可创建 Task */ }
```

#### 2. DirectoryProvider 替代 File

```groovy
// AGP 7.x - 直接使用 File
val workDir = project.file("$project.buildDir/intermediates/...")

// AGP 8.x - 使用 DirectoryProvider，支持延迟求值
val workDir = project.layout.buildDirectory
    .dir("intermediates/auto_service/${variant.name}")
    .get().asFile
```

#### 3. Task 依赖配置

```groovy
// AGP 7.x
variant.assembleProvider.get().dependsOn(myTask)

// AGP 8.x
variant.tasks.assemble.configure {
    dependsOn(myTask)
}
```

#### 4. Kotlin 编译产物处理

AGP 8.x 统一处理 Java 和 Kotlin 编译产物，使用 `variant.artifacts` API：

```groovy
// 获取编译输出（推荐方式）
val compileOutput = variant.artifacts.get(
    InternalArtifactType.JAVAC,
    ArtifactCategory.INTERMEDIATE,
    InternalArtifactType.JAVAC.getClass()
)

// 或者使用 variant.compileConfiguration
val classpath = variant.compileConfiguration
```

#### 5. 命名空间要求

AGP 8.x 要求使用 `namespace` 替代 `applicationId`：

```groovy
android {
    namespace "com.example.app"  // 替代 applicationId
}
```

#### 6. 避免 afterEvaluate

AGP 8.x 推荐使用专用钩子而非 `afterEvaluate`：
- `afterEvaluate` 会延迟所有配置，影响构建性能
- 无法精确控制配置顺序
- 不符合新的构建模型

## 重要注意事项

### 1. Provider 类型延迟求值

AGP 8.x 大量使用 `Provider<T>` 类型：
- 在配置阶段使用 `.get()` 会导致 eager evaluation
- 应尽可能保留 Provider 类型，让 Gradle 延迟求值
- 只在真正需要值的时候才调用 `.get()`

### 2. Groovy vs Kotlin DSL

- AGP 8.x 官方文档主要使用 Kotlin DSL
- Groovy 仍然支持，但需要注意类型推断
- 某些泛型调用可能需要显式指定类型

### 3. 增量构建支持

AGP 8.x 对增量构建有更严格的要求：
- Task 需要正确声明输入输出
- 使用 `@InputFiles`、`@OutputDirectory` 等注解
- 避免在 `@TaskAction` 中读取外部状态

### 4. 多编译产物支持

项目可能同时使用 Java 和 Kotlin：
- 需要同时处理两种编译产物
- 使用 `variant.artifacts` API 获取所有产物
- 确保 classpath 包含所有编译输出

**风险**: AGP 8.x API 与旧 API 差异较大，可能引入 bug

**缓解措施**:
- 仔细阅读 AGP 8.13.0 迁移指南
- 逐步测试每个 variant 类型的 Task 创建
- 充分测试编译生成过程

### 风险 2: 产物路径变化

**风险**: AGP 8.x 的产物路径可能与 7.x 不同

**缓解措施**:
- 使用 `variant.artifacts` API 动态获取路径
- 避免硬编码路径
- 在测试中验证生成的 ServiceRegistry.java 位置

### 风险 3: Task 依赖关系变化

**风险**: 新 API 的 Task 依赖关系配置方式可能不同

**缓解措施**:
- 使用 `dependsOn` 明确设置依赖
- 验证 Task 执行顺序正确
- 测试增量构建

### 风险 4: 构建性能下降

**风险**: 迁移过程中可能导致构建性能下降

**缓解措施**:
- 使用 `onVariants` 的延迟求值特性
- 避免不必要的 eager evaluation
- 使用 Gradle Build Scan 分析性能

### 权衡 1: 向后兼容性

**权衡**: 只支持 AGP 8.x，放弃对 AGP 7.x 的支持

**理由**:
- 维护多版本兼容性成本高
- AGP 8.x 已是主流版本
- 简化代码，减少复杂度

## 开发和调试流程

在开发和调试 auto-service-plugin 模块时，使用 buildSrc 模式可以快速迭代，无需每次都发布到 Maven 仓库。

### 启用 buildSrc 开发模式

在开始开发前，需要执行以下配置步骤：

1. **修改 buildSrc 目录下 build.gradle.bak 为 build.gradle**
   ```bash
   mv buildSrc/build.gradle.bak buildSrc/build.gradle
   ```

2. **注释掉根目录下 build.gradle 中 auto-service 插件依赖**
   ```groovy
   // dependencies {
   //     classpath("com.anymore:auto-service-register:x.x.x")
   // }
   ```

3. **app/build.gradle 文件中，注释掉 id 'auto-service' 引用**
   ```groovy
   // plugins {
   //     id 'auto-service'
   // }
   ```

4. **app/build.gradle 文件中，直接应用插件类**
   ```groovy
   import com.anymore.auto.gradle.AutoServiceRegisterPlugin
   apply plugin: AutoServiceRegisterPlugin
   ```

### 开发流程图

```
┌─────────────────────────────────────────────────────────────┐
│  启用 buildSrc 模式（一次性配置）                          │
│  1. mv buildSrc/build.gradle.bak buildSrc/build.gradle      │
│  2. 注释依赖插件                                          │
│  3. 直接应用插件类                                        │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│  开发迭代循环                                            │
├─────────────────────────────────────────────────────────────┤
│                                                         │
│  ┌─────────────┐                                        │
│  │ 1. 修改代码  │                                        │
│  └──────┬──────┘                                        │
│         │                                                 │
│         ▼                                                 │
│  ┌─────────────┐                                        │
│  │ 2. 清理缓存  │                                        │
│  │ ./gradlew clean                                         │
│  └──────┬──────┘                                        │
│         │                                                 │
│         ▼                                                 │
│  ┌───────────────────────────┐                            │
│  │ 3. 构建应用 (插件自动编译)  │                            │
│  │ ./gradlew :app:assembleDebug                          │
│  └──────┬────────────────────┘                            │
│         │                                                 │
│         ▼                                                 │
│  ┌─────────────┐                                        │
│  │ 4. 验证结果  │                                        │
│  └──────┬──────┘                                        │
│         │                                                 │
│         └──────────────────┘                                │
│                    循环                                   │
└─────────────────────────────────────────────────────────────┘
```

### buildSrc 模式 vs 模块模式对比

| 特性 | buildSrc 模式 | 模块模式 |
|------|----------------|----------|
| 配置复杂度 | 简单（一次性配置） | 简单（无需配置） |
| 构建速度 | 快（修改立即生效） | 较慢（需先构建插件） |
| 发布流程 | 需要额外步骤 | 标准流程 |
| 调试便利性 | 高 | 中 |
| 适合场景 | 日常开发调试 | 发布准备、CI/CD |

### 恢复到模块模式

开发完成后，恢复到模块模式：

1. 恢复 buildSrc 配置
   ```bash
   mv buildSrc/build.gradle buildSrc/build.gradle.bak
   ```

2. 恢复根目录 build.gradle 中的插件依赖
   ```groovy
   dependencies {
       classpath("com.anymore:auto-service-register:0.0.9")
   }
   ```

3. 恢复 app/build.gradle 中的插件引用
   ```groovy
   plugins {
       id 'auto-service'
   }
   ```

### 调试技巧

#### 启用详细日志

在 app/build.gradle 中配置日志级别：
```groovy
autoService {
    logLevel = "VERBOSE"  // DEBUG, INFO, VERBOSE, WARN, ERROR
}
```

#### 查看 Task 执行情况

```bash
# 查看所有 Task
./gradlew :app:tasks --all

# 查看构建依赖图
./gradlew :app:assembleDebug --dry-run

# 查看特定 Task 的依赖
./gradlew :app:tasks --group=build
```

#### 验证插件加载

```bash
# 查看项目结构
./gradlew projects

# 应该看到 autoService 扩展已注册
```

### buildSrc 依赖注意事项

在 AGP 8.13.0 迁移后，buildSrc/build.gradle 需要更新：

```groovy
buildscript {
    dependencies {
        // 升级到 Kotlin 2.1.0
        classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.0'
    }
}

dependencies {
    // 升级到 AGP 8.13.0
    compileOnly("com.android.tools.build:gradle:8.13.0")
    implementation("com.android.tools.build:gradle:8.13.0")
}
```

## Migration Plan

### 步骤 1: 升级依赖版本
- 更新 `gradle-wrapper.properties` 中的 Gradle 版本
- 更新 `build.gradle` 中的 AGP 版本
- 更新 Kotlin 版本
- 更新 buildSrc/build.gradle 中的依赖（如果使用 buildSrc 模式）

### 步骤 2: 更新 API 调用
- 替换 `AppExtension` 为 `AndroidComponentsExtension`
- 替换 `applicationVariants` 为 `onVariants`
- 替换 `buildDir` 为 `layout.buildDirectory`
- 替换 `javaCompileProvider` 为适当的 artifacts API

### 步骤 3: 更新 Task 配置
- 更新 Task 创建和依赖关系配置
- 使用新的 Task 配置 API
- 验证 Task 输入输出正确

### 步骤 4: 测试
- 测试 application 模块编译
- 测试多 variant 构建（debug/release）
- 测试增量构建
- 验证 ServiceRegistry.java 正确生成

### 步骤 5: 文档更新
- 更新 CLAUDE.md 中的版本要求
- 更新 README 中的使用说明

### 回滚策略
- 保留 AGP 7.x 的代码分支
- 使用 Git 管理版本切换
- 如遇严重问题，可快速回退到 AGP 7.x

## Open Questions

1. **Q**: AGP 8.13.0 对 Kotlin 编译产物的访问方式是否与 Java 不同？
   **A**: 需要测试验证，使用 `variant.artifacts` API 统一获取编译产物

2. **Q**: AGP 8.x 是否支持 `afterEvaluate` 模式？
   **A**: 技术上仍支持，但应避免使用。推荐使用 `androidComponents` API 提供的生命周期钩子

3. **Q**: 是否需要处理 Compose 编译产物？
   **A**: 如果项目使用 Jetpack Compose，可能需要额外的配置。需要检查项目配置

4. **Q**: AGP 8.x 对 namespace 的要求是否变化？
   **A**: 是的，AGP 8.x 强制要求使用 `namespace` 而非 `applicationId`。插件本身不应受影响，但使用插件的应用需要更新

5. **Q**: Groovy 在 AGP 8.x 中是否有类型推断问题？
   **A**: 可能存在，特别是涉及泛型的调用。需要测试并考虑添加显式类型声明

6. **Q**: variant.compileConfiguration 在 AGP 8.x 中是否可用？
   **A**: 需要验证，可能需要使用 `variant.artifacts` API 替代

7. **Q**: 如何正确处理 Task 的增量构建注解？
   **A**: 需要为 Task 的输入输出添加正确的 Gradle 注解（`@InputFiles`、`@OutputDirectory` 等）

8. **Q**: AGP 8.x 对 WorkerExecutor 的支持如何？
   **A**: 需要验证异步任务执行是否仍可用

## 待验证的具体实现点

1. **variant 对象属性访问**
   - `variant.name` - 变体名称
   - `variant.buildType` - build type
   - `variant.flavorName` - flavor 名称
   - `variant.compileConfiguration` - 编译依赖配置

2. **Task 创建和配置**
   - `project.tasks.register()` - 注册 Task
   - `task.configure {}` - 配置 Task
   - Task 依赖关系设置

3. **文件和目录访问**
   - `project.layout.buildDirectory`
   - `project.layout.projectDirectory`
   - DirectoryProvider 的使用

4. **产物访问**
   - `variant.artifacts` API 的使用
   - Java 和 Kotlin 编 译产物的获取
   - 资源产物的访问

5. **编译任务引用**
   - 获取 Java 编译任务
   - 获取 Kotlin 编译任务
   - Task 依赖关系配置

## 具体 API 迁移点详解

### AutoServiceRegisterPlugin.groovy 逐行迁移映射

#### 导入语句变更（第 3-4 行）

```
需要删除的导入：
- import com.android.build.gradle.AppExtension
- import com.android.build.gradle.AppPlugin
- import org.gradle.api.tasks.compile.JavaCompile

需要添加的导入：
- import com.android.build.api.AndroidComponentsExtension
- import com.android.build.api.variant.ApplicationVariant
- import com.android.build.gradle.tasks.CompileJavaTask
- import org.gradle.api.provider.Provider
```

#### 插件入口重构（第 12-18 行）

```
当前代码（AGP 7.4.0）：
void apply(Project project) {
    final AutoServiceExtension autoServiceExtension = project.getExtensions()
        .create("autoService", AutoServiceExtension.class, true, new HashMap<String,Set<String>>())
    project.afterEvaluate {
        if (!project.plugins.hasPlugin(AppPlugin)) return
        Logger.level = autoServiceExtension.getLogLevel()
        final android = project.getExtensions().getByType(AppExtension.class)
        ...
    }
}

目标代码（AGP 8.13.0）：
void apply(Project project) {
    final AutoServiceExtension autoServiceExtension = project.getExtensions()
        .create("autoService", AutoServiceExtension.class, true, new HashMap<String,Set<String>>())

    // 设置日志级别
    Logger.level = autoServiceExtension.getLogLevel()

    // 获取 AndroidComponentsExtension
    val androidComponents = project.extensions
        .getByType(AndroidComponentsExtension::class.java)

    // 使用 onVariants 而非 afterEvaluate
    androidComponents.onVariants { variant ->
        // 在此处处理 variant
        ...
    }
}
```

#### Variant 遍历变更（第 18 行）

```
当前代码：
android.applicationVariants.forEach { variant ->
    // 处理 variant
}

目标代码：
androidComponents.onVariants { variant ->
    // 处理 variant
    // variant 类型为 ApplicationVariant
}
```

#### 工作目录计算变更（第 20 行）

```
当前代码：
final workDir = project.file("${project
    .buildDir}${File.separator}intermediates${File.separator}auto_service${File.separator}${variant.dirName}${File.separator}")

目标代码：
final workDir = project.layout.buildDirectory
    .dir("intermediates/auto_service/${variant.dirName}")
    .get().asFile
```

#### Classpath 获取变更（第 21 行）

```
当前代码：
final classpath = project.files(
    android.bootClasspath,
    variant.javaCompileProvider.get().classpath,
    variant.javaCompileProvider.get().destinationDir
)

目标代码（方案 1 - 如果 compileConfiguration 可用）：
final classpath = project.files(
    variant.compileConfiguration
)

目标代码（方案 2 - 使用 artifacts API）：
final classpath = project.files(
    variant.bootClasspath,
    variant.artifacts.get(
        InternalArtifactType.JAVAC,
        ArtifactCategory.INTERMEDIATE,
        InternalArtifactType.JAVAC.getClass()
    )
)
```

#### Task 创建方式变更（第 22 行）

```
当前代码：
final registerTask = project.tasks.create(
    "androidAutoServiceRegisterTask${variant.name.capitalize()}",
    AutoServiceRegisterTask.class
) {
    it.setClasspath(classpath)
    it.setTargetDir(new File(workDir, "src"))
    if (autoServiceExtension.checkImplementation) {
        it.setRequiredServices(autoServiceExtension.requireServices)
    }
    it.setExclusiveRules(autoServiceExtension.exclusiveRules)
}

目标代码：
final registerTask = project.tasks.register(
    "androidAutoServiceRegisterTask${variant.name.capitalize()}",
    AutoServiceRegisterTask.class
)

registerTask.configure {
    it.setClasspath(classpath)
    it.setTargetDir(new File(workDir, "src"))
    if (autoServiceExtension.checkImplementation) {
        it.setRequiredServices(autoServiceExtension.requireServices)
    }
    it.setExclusiveRules(autoServiceExtension.exclusiveRules)
}
```

#### 编译 Task 创建变更（第 32-39 行）

```
当前代码：
final compileTask = project.tasks.create(
    "compileAndroidAutoServiceRegistry${variant.name.capitalize()}",
    JavaCompile.class
) {
    it.setSource(new File(workDir, "src"))
    it.include("**/*.java")
    it.setClasspath(classpath)
    it.setDestinationDir(variant.getJavaCompileProvider().get().getDestinationDir())
    it.setSourceCompatibility(autoServiceExtension.sourceCompatibility)
    it.setTargetCompatibility(autoServiceExtension.sourceCompatibility)
}

目标代码：
val compileTask = project.tasks.register(
    "compileAndroidAutoServiceRegistry${variant.name.capitalize()}",
    JavaCompile.class
)

compileTask.configure {
    it.setSource(new File(workDir, "src"))
    it.include("**/*.java")
    it.setClasspath(classpath)
    // 需要获取正确的目标目录
    it.setDestinationDir(getCompileDestinationDir(variant))
    it.setSourceCompatibility(autoServiceExtension.sourceCompatibility)
    it.setTargetCompatibility(autoServiceExtension.sourceCompatibility)
}
```

#### Task 依赖关系变更（第 40-42 行）

```
当前代码：
registerTask.mustRunAfter(variant.javaCompileProvider.get())
compileTask.mustRunAfter(registerTask)
variant.assembleProvider.get().dependsOn(registerTask, compileTask)

目标代码：
// 获取编译 Task
val compileJavaTask = variant.getTaskProvider("compileJava")

// 配置依赖
registerTask.configure {
    mustRunAfter(compileJavaTask)
}

compileTask.configure {
    mustRunAfter(registerTask)
}

variant.tasks.assemble.configure {
    dependsOn(registerTask, compileTask)
}
```

## AGP 8.13.0 新 API 详细说明

### AndroidComponentsExtension 完整 API

```
androidComponents.beforeVariants { variant ->
    // 变体创建前执行
    // 可以修改变体属性
    // 不能创建 Task

    variant.enableUnitTest = false
    variant.minSdk.set(21)
}

androidComponents.onVariants { variant ->
    // 变体配置完成后执行
    // 可以创建 Task
    // 可以访问编译产物

    val task = project.tasks.register("myTask")
}

androidComponents.onVariants(
    selector().withBuildType("release")
) { variant ->
    // 只处理 release variant
}

androidComponents.finalizeSymlinks { /* 配置符号链接 */ }
```

### ApplicationVariant 属性详解

```
基础属性：
- name: String - variant 名称（debug, release）
- build: String - build type（debug, release）
- productFlavors: List<String> - flavor 列表
- flavorName: String? - flavor 名称
- namespace: Provider<String> - namespace 配置
- minSdk: Provider<Int> - minSdk 配置
- targetSdk: Provider<Int> - targetSdk 配置

编译相关：
- compileConfiguration: Configuration - 编译依赖配置
- bootClasspath: FileCollection - Boot classpath
- artifacts: Artifacts - 产物访问 API

Task 相关：
- tasks: TaskContainer - Task 容器
- tasks.compileJava: TaskProvider<CompileJavaTask> - Java 编译 Task
- tasks.assemble: TaskProvider<*> - assemble Task

产物相关：
- artifacts: Artifacts - 产物管理
```

### Artifacts API 用法

```
获取单个产物：
val javacOutput = variant.artifacts.get(
    InternalArtifactType.JAVAC,
    ArtifactCategory.INTERMEDIATE,
    InternalArtifactType.JAVAC.getClass()
)

获取所有产物：
val allArtifacts = variant.artifacts.getAll()

获取目录：
val outputDir = variant.artifacts.get(
    InternalArtifactType.JAVAC,
    ArtifactCategory.INTERMEDIATE,
    InternalArtifactType.JAVAC.getClass()
).get().asFile
```

## 迁移策略

### 策略选择

**策略 1: 直接迁移到 AGP 8.13.0（推荐）**

优点：
- 一步到位，避免重复工作
- 直接使用最新 API，长期维护成本低
- 可以一次性验证所有功能

缺点：
- 变更范围大，调试难度较高
- 需要熟悉多个新 API

**策略 2: 渐进式迁移（AGP 8.0 → 8.13.0）**

优点：
- 可以逐步验证每个版本
- 遇到问题容易定位
- 降低单次迁移风险

缺点：
- 需要多次迁移
- 增加总工作量

**决策**: 采用策略 1，直接迁移到 AGP 8.13.0

### 迁移顺序

```
阶段 1: 基础环境准备（隔离风险）
├── 创建 Git 分支
├── 备份关键文件
└── 准备回退策略

阶段 2: 依赖升级（最底层，优先）
├── Gradle: 7.5 → 8.9
├── AGP: 7.4.0 → 8.13.0
└── Kotlin: 1.8.10 → 2.1.0

阶段 3: 基础 API 迁移（高层 API）
├── 替换导入语句
├── 替换插件入口 API
└── 移除 afterEvaluate

阶段 4: Variant API 迁移（中层 API）
├── 使用 onVariants
├── 更新 variant 属性访问
└── 测试 variant 遍历

阶段 5: Task API 迁移（低层 API）
├── 更新 Task 创建方式
├── 更新 Task 依赖关系
└── 测试 Task 执行

阶段 6: 产物访问迁移（数据层 API）
├── 使用 artifacts API
├── 验证产物路径
└── 测试代码生成

阶段 7: 全面测试和验证
├── 单 variant 测试
├── 多 variant 测试
└── 功能回归测试
```

## 风险缓解措施详细方案

### 风险 1: variant.compileConfiguration 不可用

**缓解措施**：
```groovy
// 尝试使用 compileConfiguration
try {
    val config = variant.compileConfiguration
    if (config != null) {
        classpath = project.files(config)
    } else {
        throw new GradleException("compileConfiguration is null")
    }
} catch (Exception e) {
    Logger.e("compileConfiguration not available: ${e.message}")
    // 回退到 artifacts API
    classpath = project.files(
        variant.artifacts.get(
            InternalArtifactType.JAVAC,
            ArtifactCategory.INTERMEDIATE,
            InternalArtifactType.JAVAC.getClass()
        )
    )
}
```

### 风险 2: Groovy 类型推断问题

**缓解措施**：
```groovy
// 使用显式类型声明
final AndroidComponentsExtension androidComponents =
    project.extensions.getByType(AndroidComponentsExtension.class)

// 对于泛型，显式指定类型
final Provider<String> namespaceProvider = variant.namespace

// 使用安全的 null 检查
final String buildType = variant.buildType
if (buildType != null) {
    // 处理 null 情况
}
```

### 风险 3: DirectoryProvider 路径不兼容

**缓解措施**：
```groovy
// 使用 try-catch 处理路径问题
try {
    final workDir = project.layout.buildDirectory
        .dir("intermediates/auto_service/${variant.name}")
        .get().asFile

    if (!workDir.exists()) {
        workDir.mkdirs()
    }
} catch (Exception e) {
    Logger.e("Failed to create work directory: ${e.message}")
    throw new GradleException("Failed to create work directory", e)
}
```

### 风险 4: Task 依赖关系配置失败

**缓解措施**：
```groovy
// 使用延迟配置确保 Task 已创建
androidComponents.onVariants { variant ->
    val registerTask = project.tasks.register(...)
    val compileTask = project.tasks.register(...)

    // 在 configure 块中设置依赖
    variant.tasks.assemble.configure { assemble ->
        try {
            assemble.dependsOn(registerTask, compileTask)
        } catch (Exception e) {
            Logger.e("Failed to configure task dependencies: ${e.message}")
        }
    }
}
```

## 回退策略详细方案

### Git 分支管理

```
主分支：
- master/main: AGP 7.4.0 稳定版本

开发分支：
- feature/agp-8-13-0: AGP 8.13.0 迁移分支

临时备份：
- backup/agp-7.4.0: 关键文件备份分支
```

### 文件备份清单

```
需要备份的文件：
□ auto-service-plugin/src/main/groovy/.../AutoServiceRegisterPlugin.groovy
□ gradle-wrapper.properties
□ build.gradle (根目录)
□ buildSrc/build.gradle.bak (如果已启用）
□ app/build.gradle
```

### 快速回退脚本

```bash
#!/bin/bash
# rollback.sh - 快速回退到 AGP 7.4.0

echo "Rolling back to AGP 7.4.0..."

# 1. 恢复 gradle-wrapper.properties
git checkout master -- gradle-wrapper.properties

# 2. 恢复 build.gradle
git checkout master -- build.gradle

# 3. 恢复插件代码
git checkout master -- auto-service-plugin/src/main/groovy/com/anymore/auto/gradle/AutoServiceRegisterPlugin.groovy

# 4. 清理构建缓存
./gradlew clean

echo "Rollback complete. Please verify build works."
```

## 验证清单

### 依赖验证

```
□ Gradle 版本
  - 检查: ./gradlew --version | grep "Gradle 8.9"
  - 预期: Gradle 8.9.x

□ AGP 版本
  - 检查: build.gradle 中包含 "com.android.tools.build:gradle:8.13.0"
  - 预期: AGP 8.13.0

□ Kotlin 版本
  - 检查: build.gradle 中包含 "kotlin-gradle-plugin:2.1.0"
  - 预期: Kotlin 2.1.0
```

### API 验证

```
□ 导入验证
  - [ ] AppExtension 已删除
  - [ ] AndroidComponentsExtension 已添加
  - [ ] AppPlugin 已删除
  - [ ] JavaCompile 已删除

□ API 调用验证
  - [ ] afterEvaluate 已移除
  - [ ] onVariants 已使用
  - [ ] buildDir 已替换为 layout.buildDirectory
  - [ ] javaCompileProvider 已替换
```

### 功能验证

```
□ 基础功能
  - [ ] 插件能加载
  - [ ] autoService 扩展可用
  - [ ] Task 能创建

□ 代码生成
  - [ ] ServiceRegistry.java 能生成
  - [ ] 文件路径正确
  - [ ] 文件内容格式正确

□ 多 variant 支持
  - [ ] debug variant 能正常工作
  - [ ] release variant 能正常工作
  - [ ] Task 为每个 variant 创建

□ 插件配置
  - [ ] checkImplementation 功能正常
  - [ ] excludeAlias 功能正常
  - [ ] excludeClassName 功能正常
  - [ ] logLevel 配置生效

□ 核心功能
  - [ ] 优先级排序正常
  - [ ] 单例模式正常
  - [ ] 别名机制正常
```

### 性能验证

```
□ 构建性能
  - [ ] 构建时间在合理范围内
  - [ ] 增量构建正确工作
  - [ ] 无不必要的 Task 执行

□ 资源使用
  - [ ] 内存使用正常
  - [ ] 无内存泄漏
```
