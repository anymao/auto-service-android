## ADDED Requirements

### Requirement: 使用 beforeVariants API 注册回调
插件 SHALL 使用 `AndroidComponentsExtension.beforeVariants()` API 替代旧的 `variantManager.variantScopes()` API。

#### Scenario: 在 variant 创建前注册回调
- **WHEN** 项目应用 auto-service-plugin
- **THEN** 插件使用 `androidComponents.beforeVariants()` 注册回调函数

### Requirement: 使用 onVariants API 配置 Task
插件 SHALL 使用 `AndroidComponentsExtension.onVariants()` API 配置 Task 的创建和依赖关系。

#### Scenario: 在 variant 配置后创建 Task
- **WHEN** 插件为 application 模块注册
- **THEN** 插件使用 `androidComponents.onVariants().all()` 创建 AutoServiceRegisterTask

### Requirement: 访问 variant 属性使用新 API
插件 SHALL 使用 AGP 8.13.0 的 variant API 访问编译相关属性。

#### Scenario: 获取编译输出目录
- **WHEN** AutoServiceRegisterTask 需要扫描编译输出
- **THEN** Task 使用 `variant.getArtifacts()` API 获取编译输出目录

#### Scenario: 获取 Java 编译任务
- **WHEN** AutoServiceRegisterTask 需要依赖 Java 编译任务
- **THEN** Task 使用 `variant.getTaskProvider()` API 获取编译任务引用

### Requirement: 使用新的目录属性 API
插件 SHALL 使用 AGP 8.13.0 的目录 API 替代已废弃的 `buildDirectory` API。

#### Scenario: 获取构建输出目录
- **WHEN** 插件需要获取项目的构建输出目录
- **THEN** 插件使用 `project.layout.buildDirectory` 而非已废弃的 `project.buildDir`

#### Scenario: 配置生成文件路径
- **WHEN** AutoServiceRegisterTask 配置生成的 ServiceRegistry.java 路径
- **THEN** Task 使用 `layout.buildDirectory.dir()` API 正确计算路径
