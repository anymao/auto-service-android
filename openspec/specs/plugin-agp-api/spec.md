# AGP 插件 API 规范

## Requirements

### Requirement: 仅支持 Android Application

插件 SHALL 通过 typed AGP API 配置 Android Application，SHALL 拒绝 Android Library 和非 Android Application 项目。

#### Scenario: Application 模块

- **WHEN** 项目应用 `com.android.application` 和 `auto-service`
- **THEN** 插件为每个应用变体注册变换任务

#### Scenario: 非 Application 模块

- **WHEN** Library 或普通 Gradle 项目应用 `auto-service`
- **THEN** 配置失败并说明插件只能应用于 Android Application

### Requirement: typed Android Components 变体注册

插件 SHALL 使用 `ApplicationAndroidComponentsExtension.onVariants`，SHALL NOT 反射查找 `toTransform` 或回退到 `applicationVariants`。

#### Scenario: Debug 和 Release

- **WHEN** Android 项目包含 debug、release 变体
- **THEN** 分别注册 `androidAutoServiceRegisterDebug`、`androidAutoServiceRegisterRelease`
- **AND** 两个任务接入各自变体的 class artifact 链

### Requirement: 全范围 class 发现

插件 SHALL 使用 `ScopedArtifacts.Scope.ALL` 与 `ScopedArtifact.CLASSES` 聚合当前变体全部 class。

#### Scenario: 五类来源

- **GIVEN** 实现分别位于 Application、Android Library、Java Library、直接外部 AAR、桥接 AAR 的传递依赖
- **WHEN** 构建应用变体
- **THEN** 五个实现全部注册
- **AND** priority 与 alias 行为一致

#### Scenario: Library 声明实现

- **WHEN** Android Library 声明 `@AutoService` 实现但不应用插件
- **THEN** 最终 Application 插件发现该实现

### Requirement: metadata catalog

扫描 SHALL 先建立 class origin 和服务候选 catalog，再执行排序、排除、require 校验和代码生成。

#### Scenario: 稳定排序

- **WHEN** 同一接口存在多个候选
- **THEN** 注册项按 priority 升序、实现类全限定名升序排列

#### Scenario: 排除候选

- **WHEN** 候选命中类名/alias 正则
- **THEN** 不写入运行时注册表
- **AND** debuggable 诊断保留 `EXCLUDED` 状态与命中规则

#### Scenario: required service

- **GIVEN** `checkImplementation=true`
- **WHEN** required 接口或 alias 在最终 catalog 中没有注册项
- **THEN** 构建失败并区分完全缺失与全部被排除

### Requirement: 重复类安全

除生成保留类外，插件 SHALL 拒绝不同来源的同名普通 class。

#### Scenario: 普通重复类

- **WHEN** 两个 JAR 均包含 `test.duplicate.Duplicate`
- **THEN** 构建失败
- **AND** 错误包含 class 名和两个 JAR 来源

#### Scenario: 注册表存根

- **WHEN** 输入中存在 `ServiceRegistry` 或 `ServiceRegistryDiagnostics` 存根
- **THEN** writer 移除存根并只写入一份生成实现

### Requirement: 变体感知诊断

插件 SHALL 使用 `variant.debuggable` 控制诊断内容，SHALL NOT 仅按变体名称判断。

#### Scenario: debuggable 变体

- **WHEN** `variant.debuggable=true`
- **THEN** `ServiceLoader.diagnose()` 返回 AVAILABLE
- **AND** 报告包含 REGISTERED、EXCLUDED、priority、alias、singleton 与排除规则

#### Scenario: 非 debuggable 变体

- **WHEN** `variant.debuggable=false`
- **THEN** 正常服务加载保持完整
- **AND** 诊断返回 `UNAVAILABLE_IN_NON_DEBUG_BUILD` 与空 entries
- **AND** 诊断 class 常量池不包含候选类名、alias 或排除正则

### Requirement: 确定性输出

插件 SHALL 以排序 entry、固定 ZIP 元数据写出单一 JAR。

#### Scenario: 相同输入

- **WHEN** 相同 classpath 与配置重复执行
- **THEN** 输出字节和 SHA-256 一致

#### Scenario: AGP artifact 接续

- **WHEN** 注册任务完成
- **THEN** 输出 JAR 继续参与 dex、lint 和 APK/AAB 构建
- **AND** 原始服务实现 class 不丢失
