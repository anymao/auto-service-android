# AGP 8 完整适配设计

## 目标

在保持 `autoService` DSL 和 `ServiceRegistry` 生成结果兼容的前提下，使项目在 AGP 8.13、Gradle 8.13 和 JDK 17 下稳定完成 Debug 与 Release 构建，并通过 Gradle 的任务依赖验证。

## 当前问题

现有插件通过 `afterEvaluate` 和旧版 `applicationVariants` 遍历变体，并直接将生成类写入 `intermediates/javac/<variant>/.../classes`。该目录属于 AGP 的内部产物；Release Lint 会读取它，却不知道自定义编译任务的依赖关系，导致 `generateReleaseLintVitalReportModel` 因隐式依赖校验失败。

## 方案

### Variant 配置

插件仅在 `com.android.application` 应用后配置。使用 `AndroidComponentsExtension.onVariants` 注册每个变体的任务，移除 `afterEvaluate`、`AppExtension`、`AppPlugin` 与 `applicationVariants`。

### 生成与编译边界

每个变体的注册表源码写入插件专属目录 `build/generated/autoService/<variant>/src`，注册任务显式声明：

- 编译产物与依赖类路径为输入；
- `autoService` 扩展中的校验与排除规则为输入；
- 生成源码目录为输出。

生成源码由变体的 Java 源集合消费，交给 AGP 的官方编译任务编译。插件不再创建第二个 `JavaCompile` 任务，也不直接修改 AGP 的 `intermediates/javac` 目录。

### 任务顺序

注册任务依赖对应变体的 Kotlin/Java 编译产物可用时机；变体编译任务消费其生成源码。任务依赖通过 `TaskProvider`、`Provider` 与 AGP Variant API 建模，不按任务名称查找 dex、assemble 或 lint 任务。

### 兼容性

- 保留 `autoService` 的 `checkImplementation`、`require`、`exclude*`、`sourceCompatibility` 和日志配置。
- 保持 Debug、Release 与多变体下 `ServiceRegistry` 的内容一致性。
- 从 Manifest 删除已废弃且被忽略的 `package` 属性，唯一命名空间来源仍为 `android.namespace`。

## 验收标准

1. `:app:assembleDebug` 与 `:app:assembleRelease` 成功。
2. Debug 和 Release 均生成并编译 `ServiceRegistry`，其中包含现有 `Impl1`、`Impl2` 服务映射。
3. `:app:testDebugUnitTest` 通过。
4. Release 构建不再出现 `generateReleaseLintVitalReportModel` 对自定义任务的隐式依赖错误。
5. 插件源码中不再使用 `afterEvaluate`、`AppExtension`、`AppPlugin`、`applicationVariants` 或硬编码 AGP `intermediates/javac` 路径。
6. 构建完成后不新增非构建产物的工作区改动。

## 非目标

- 不升级到 AGP 9 或 Gradle 9。
- 不改变库的发布坐标、公共运行时 API 或 `autoService` DSL 语义。
- 不修改用户现有的未提交配置和 IDE 文件。
