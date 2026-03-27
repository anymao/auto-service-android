## ADDED Requirements

### Requirement: 插件使用 AGP 8.13.0 的 API
auto-service-plugin 插件 SHALL 使用 AGP 8.13.0 的 `AndroidComponentsExtension` API 替代已废弃的 `VariantManager` API。

#### Scenario: 插件正确注册到 AGP 8.13.0
- **WHEN** 项目使用 AGP 8.13.0 并应用 auto-service-plugin
- **THEN** 插件使用 `AndroidComponentsExtension.beforeVariants()` API 注册 variant 回调

#### Scenario: 插件兼容 AGP 8.13.0 的依赖注入
- **WHEN** 插件尝试访问 AGP 提供的服务（如 ProjectLayout、WorkerExecutor）
- **THEN** 插件通过 `AndroidComponentsExtension` 提供的正确 API 获取服务

### Requirement: Task 使用新的 Task 配置 API
插件创建的 Task SHALL 使用 AGP 8.13.0 推荐的 Task 配置方式。

#### Scenario: Task 使用新 API 配置输入输出
- **WHEN** AutoServiceRegisterTask 被创建
- **THEN** Task 使用 `ProjectLayout` 和 `FileCollection` 正确配置输入输出文件

#### Scenario: Task 使用新 API 配置依赖关系
- **WHEN** AutoServiceRegisterTask 设置依赖的编译任务
- **THEN** Task 使用 `variantProvider.get()` 获取 variant 并正确配置依赖

### Requirement: 升级依赖版本
项目的构建配置 SHALL 升级到支持 AGP 8.13.0 的最低版本。

#### Scenario: Gradle 版本满足 AGP 8.13.0 要求
- **WHEN** 项目使用 auto-service-plugin
- **THEN** Gradle 版本至少为 8.9（AGP 8.13.0 最低要求）

#### Scenario: Kotlin 版本兼容 AGP 8.13.0
- **WHEN** 项目编译带有 auto-service-plugin 注解的代码
- **THEN** Kotlin 版本至少为 2.1.0
