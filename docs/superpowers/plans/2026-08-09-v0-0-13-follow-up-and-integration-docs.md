# v0.0.13 后续修复与接入文档实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: 使用 `superpowers:executing-plans` 按任务逐项执行本计划；步骤使用复选框跟踪。

**目标：** 修复 0.0.13 审查后确认的重复类识别、插件源码包和 Android instrumentation 构建问题，并把当前版本整理为外部接入方可直接使用的完整 API 与集成文档。

**架构：** 保持现有编译期显式注册架构不变。重复类修复只调整扫描器传给 `ClassOrigin` 的输入身份；源码包修复只扩展 `sourcesJar` 的源集合；Android 测试修复采用与当前 AGP/targetSdk 兼容的 AndroidX Test 依赖；文档以公开 API、Gradle DSL、模块边界、Debug/Release 诊断语义和验证流程为主。

**技术栈：** Groovy/Gradle TestKit、Kotlin、Android Gradle Plugin 8.13.0、Gradle 8.13、JDK 17、Markdown。

## 全局约束

- 只修改 `codex/auto-service-0.0.13` 工作树；保留已有标题修复及 GitNexus 索引更新。
- 每次修改生产类、方法或函数前先执行 GitNexus upstream impact；若工具无法解析 Groovy 符号，记录限制并以源码调用关系和测试补充证据。
- 先写回归测试并观察失败，再写最小生产修复；配置依赖问题按单变量假设验证。
- 文档、代码注释和用户可见说明全部使用中文；发布凭据只说明注入方式，不记录任何真实值。
- 最终不把设备缺失、网络阻塞或旧报告当作通过；connected test 必须有真实设备执行证据。

## 任务

### 任务 1：重复类输入身份

- [ ] 为两个同名目录输入增加重复类回归测试，断言错误包含两个实际来源。
- [ ] 执行测试并确认旧实现失败。
- [ ] 使用规范化绝对路径作为目录/JAR 的输入身份，保留同一物理输入重复列出的兼容行为。
- [ ] 重新执行 scanner/catalog 相关测试。

### 任务 2：插件源码包

- [ ] 增加发布配置测试，验证 `sourcesJar` 同时包含 `main` 和 `pluginEntry` 源码。
- [ ] 执行测试并确认旧 `maven_publish.gradle` 失败。
- [ ] 扩展 `sourcesJar` 源集合，避免不存在 `pluginEntry` 的普通 Java 模块受影响。
- [ ] 检查实际 `auto-service-plugin` sources JAR 中包含 Kotlin 插件入口。

### 任务 3：Android instrumentation 构建

- [ ] 复现当前 manifest merger 失败并确认触发依赖链。
- [ ] 用兼容的 AndroidX Test 版本组合修复测试依赖，避免用手工 manifest 覆盖掩盖旧依赖问题。
- [ ] 重新执行 `connectedDebugAndroidTest`；若设备、下载或权限阻塞，明确记录为未执行或阻塞。
- [ ] 保留已完成的真实 APK 手工诊断面板验收证据，并与 instrumentation 结果分开表述。

### 任务 4：外部接入文档

- [ ] 完善 API 参考：注解、加载、选择、生命周期、诊断 DTO、DSL、生成保留类型、版本契约和 Java/Kotlin 形式。
- [ ] 新增按“依赖配置 → 模块声明 → Application 插件 → 使用 → 诊断 → Release 注意事项 → 验证”的集成指南。
- [ ] 更新 README 导航、测试文档、故障排查和 v0.0.13 变更日志，准确区分已验证能力与人工发布门禁。

### 任务 5：最终验证

- [ ] 执行插件单测/TestKit、loader/registry/annotation 单测、Debug/Release assemble。
- [ ] 检查 sources JAR、`git diff --check`、工作树范围和未覆盖的敏感信息。
- [ ] 执行 GitNexus `detect_changes()`，确认变更只影响预期符号/流程。
- [ ] 输出当前版本功能、修复项、接入步骤、验证结果和剩余人工门禁。

---

## 验收标准

- 两个不同物理输入即使 basename 相同也不能静默合并；同一物理输入重复列出仍不误报。
- 发布的 `-sources.jar` 包含插件 Kotlin 入口源文件。
- `connectedDebugAndroidTest` 能在在线模拟器上通过；否则报告真实阻塞原因，不宣称通过。
- 外部开发者只看 README 与集成指南即可完成四组件对齐、插件配置、服务声明、加载和诊断。
- 文档中的测试数量、命令和当前实现一致。
