## ADDED Requirements

### Requirement: AGP API 类路径兼容性
auto-service-plugin SHALL 使用 AGP 7.x 中正确的类名和包名。

根据 AGP 源码分析，以下类路径在 AGP 7.x 中保持不变：

| 类名 | AGP 4.x 路径 | AGP 7.x 路径 | 变化 |
|------|-------------|-------------|------|
| AppPlugin | `com.android.build.gradle.AppPlugin` | `com.android.build.gradle.AppPlugin` | **不变** |
| AppExtension | `com.android.build.gradle.AppExtension` | `com.android.build.gradle.AppExtension` | **不变** |

#### Scenario: AppPlugin 导入正确
- **WHEN** 检查 AutoServiceRegisterPlugin.groovy 的 import 语句
- **THEN** AppPlugin 导入路径为 `com.android.build.gradle.AppPlugin`

#### Scenario: AppExtension 导入正确
- **WHEN** 检查 AutoServiceRegisterPlugin.groovy 的 import 语句
- **THEN** AppExtension 导入路径为 `com.android.build.gradle.AppExtension`

**说明**: AGP 7.x 中 AppExtension 是 ApplicationExtension 的别名，两者都可用。

### Requirement: applicationVariants API 兼容性
插件 SHALL 正确使用 AGP 7.x 的 applicationVariants API。

**API 兼容性分析**:
- `applicationVariants` 在 AGP 7.x 中完全兼容
- 返回类型仍为 `DomainObjectSet<ApplicationVariant>`
- forEach 遍历方式不变

#### Scenario: 遍历构建变体
- **WHEN** 插件执行 `android.applicationVariants.forEach { variant -> ... }`
- **THEN** 能正确遍历所有构建变体（debug、release 等）

#### Scenario: 获取变体名称
- **WHEN** 插件访问 `variant.name` 或 `variant.dirName`
- **THEN** 返回正确的变体名称

### Requirement: javaCompileProvider API 兼容性
插件 SHALL 正确获取编译 classpath 和输出目录。

**API 兼容性分析**:
- `javaCompileProvider.get().classpath` 返回 `FileCollection`
- `javaCompileProvider.get().destinationDir` 返回 `File`
- 两者在 AGP 7.x 中均保持不变

#### Scenario: 获取 classpath
- **WHEN** 插件调用 `variant.javaCompileProvider.get().classpath`
- **THEN** 返回正确的编译类路径（包含所有依赖的 .class 和 .jar）

#### Scenario: 获取 destinationDir
- **WHEN** 插件调用 `variant.javaCompileProvider.get().destinationDir`
- **THEN** 返回正确的编译输出目录（如 `build/intermediates/javac/debug/classes`）

#### Scenario: 组合 classpath
- **WHEN** 插件创建组合 classpath
  ```groovy
  project.files(android.bootClasspath, variant.javaCompileProvider.get().classpath, variant.javaCompileProvider.get().destinationDir)
  ```
- **THEN** 返回包含 Android SDK boot classpath、项目 classpath 和输出目录的 FileCollection

### Requirement: bootClasspath API 兼容性
插件 SHALL 正确获取 Android SDK boot classpath。

**API 兼容性分析**:
- `android.bootClasspath` 在 AGP 7.x 中保持不变
- 返回类型仍为 `List<File>`
- 包含 android.jar 等 SDK 类

#### Scenario: 获取 boot classpath
- **WHEN** 插件调用 `android.bootClasspath`
- **THEN** 返回正确的 Android SDK 类路径列表

### Requirement: assembleProvider API 兼容性
插件 SHALL 正确设置任务依赖关系。

**API 兼容性分析**:
- `variant.assembleProvider.get()` 返回 assemble Task
- `dependsOn()` 方法在 AGP 7.x 中保持不变

#### Scenario: 任务依赖设置
- **WHEN** 插件调用
  ```groovy
  variant.assembleProvider.get().dependsOn(registerTask, compileTask)
  ```
- **THEN** 任务依赖关系正确建立，assemble 依赖于 register 和 compile 任务

### Requirement: 插件编译通过
buildSrc 模块 SHALL 在 AGP 7.4.0 下编译通过。

#### Scenario: buildSrc 模块编译
- **WHEN** Gradle Sync 或执行构建命令
- **THEN** buildSrc 编译成功无错误

#### Scenario: Groovy 代码编译
- **WHEN** 编译 AutoServiceRegisterPlugin.groovy 和 AutoServiceRegisterAction.groovy
- **THEN** 无 import 错误、无 API 调用错误

### Requirement: Javassist 兼容性
插件 SHALL 能正确解析 JDK 11+ 编译的 class 文件。

**当前版本**: Javassist 3.28.0-GA

#### Scenario: 解析 class 文件
- **WHEN** 插件使用 Javassist 解析编译后的 class 文件
- **THEN** 能正确读取 class 信息和注解

#### Scenario: 解析 jar 文件
- **WHEN** 插件使用 Javassist 解析依赖 jar 文件
- **THEN** 能正确读取所有 class 信息

### Requirement: 任务创建 API 兼容性
插件 SHALL 使用兼容的任务创建方式。

#### Scenario: 创建 registerTask
- **WHEN** 插件调用
  ```groovy
  project.tasks.create("androidAutoServiceRegisterTask${variant.name.capitalize()}", AutoServiceRegisterTask.class)
  ```
- **THEN** 任务成功创建

#### Scenario: 创建 compileTask
- **WHEN** 插件调用
  ```groovy
  project.tasks.create("compileAndroidAutoServiceRegistry${variant.name.capitalize()}", JavaCompile.class)
  ```
- **THEN** JavaCompile 任务成功创建

### Requirement: 代码生成功能正常
插件 SHALL 能正确生成 ServiceRegistry.java。

#### Scenario: 生成 ServiceRegistry.java
- **WHEN** 插件执行注册任务
- **THEN** 在 `build/intermediates/auto_service/{variant}/src/` 目录生成 ServiceRegistry.java

#### Scenario: ServiceRegistry 内容正确
- **WHEN** 检查生成的 ServiceRegistry.java
- **THEN** 包含正确的 register() 方法和 get() 方法
- **AND** 包含所有 @AutoService 注解的服务实现类注册代码