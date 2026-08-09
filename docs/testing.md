# 测试覆盖与缺口

本文基于 `0.0.13` 分支当前测试代码，说明现有 56 个 `@Test` 验证了什么，以及哪些公开行为仍缺少自动化证据。

测试数量不等于行覆盖率。本项目尚未配置覆盖率插件，下面的结论来自逐项读取断言、夹具和生产源码。

## 运行测试

```bash
./gradlew \
  :auto-service-annotation:test \
  :auto-service-registry:test \
  :auto-service-loader:test \
  :auto-service-plugin:test \
  :app:testDebugUnitTest
```

验证真实 Android 产物：

```bash
./gradlew :app:assembleDebug :app:assembleRelease
```

设备或模拟器在线时，单独运行模板 instrumentation 测试：

```bash
./gradlew :app:connectedDebugAndroidTest
```

插件测试包含多次嵌套 Gradle/AGP 构建，首次运行可能需要数分钟。当前 JVM 测试共 55 个；另有 1 个需要 Android 设备的 instrumentation 测试。

## 本次审查结果

2026-08-09 在 Gradle 8.13 / JDK 17 下执行 JVM/TestKit 命令、Debug/Release assemble 和 `Pixel_Fold_API_31` 模拟器测试：

| 指标 | 结果 |
| --- | ---: |
| JVM/TestKit 测试 | 55 |
| failures | 0 |
| errors | 0 |
| skipped | 0 |
| Debug APK | 通过 |
| Release APK | 通过 |
| connectedDebugAndroidTest | 1 个测试通过 |

instrumentation 用例目前是 demo package name smoke test；它已在真实模拟器执行通过，但不替代库核心的 JVM/TestKit 覆盖。

## 测试分布

| 区域 | `@Test` 数 | 主要测试类 |
| --- | ---: | --- |
| Registry 诊断模型 | 3 | `ServiceDiagnosticReportTest` |
| Loader | 2 | `ServiceLoaderDiagnosticTest`、`ServiceLazyTest` |
| Plugin 单元、TestKit 与发布配置 | 43 | scanner、catalog、validator、generator、writer、task、extension、功能夹具、发布配置 |
| Demo app JVM 测试 | 7 | `ExampleUnitTest`、`DemoScenarioRunnerTest` 等 |
| Demo app instrumentation | 1 | `ExampleInstrumentedTest` |
| Annotation | 0 | 暂无独立测试 |
| 合计 | 56 | 不含 fixture 中用于生成 class 的示例方法 |

## 已覆盖行为

### 运行时与诊断

| 行为 | 证据 |
| --- | --- |
| 报告计算注册数、alias 匹配数和可用 alias | `ServiceDiagnosticReportTest` |
| 未应用插件时返回稳定 unavailable 报告 | `ServiceDiagnosticReportTest`、`ServiceLoaderDiagnosticTest` |
| 未生成注册表时给出 Application 插件提示 | `ServiceDiagnosticReportTest` |
| 单个惰性供应器在 8 线程并发下只创建一次 | `ServiceLazyTest` |
| 生成注册表可被真实 `ServiceLoader` 加载 | `generatedRegistryLoadsServicesThroughServiceLoaderAtRuntime` |
| alias 过滤返回正确实现 | `generatedRegistryFiltersMultipleServicesByAliasAtRuntime` |
| 排除项不进入加载结果但保留在 Debug 诊断 | `excludedServiceIsNotReturnedByGeneratedRegistry` |
| Debug 完整诊断、Release 空诊断且常量池不泄露候选 | `verifyAllScopeRuntime` |

### 扫描、排序与校验

| 行为 | 证据 |
| --- | --- |
| 目录和 JAR 产生相同候选 | `ClassMetadataScannerTest` |
| RUNTIME 与 CLASS retention 都能被扫描 | `ClassMetadataScannerTest` |
| priority 优先、类名次序稳定 | `ServiceCatalogTest`、`AutoServiceExtensionTest` |
| 排除候选保留命中规则 | `ClassMetadataScannerTest` |
| 损坏 class 报告容器和 entry | `ClassMetadataScannerTest` |
| 两个同名目录输入仍报告重复 class | `ClassMetadataScannerTest` |
| require 缺失、全部排除、alias 不匹配分别给出行动提示 | `RequiredServiceValidatorTest` |
| 普通重复类报告两个来源，保留生成类允许替换 | `ServiceCatalogTest`、`DeterministicJarWriterTest`、TestKit |

### AGP 集成和依赖范围

| 行为 | 证据 |
| --- | --- |
| Debug/Release 都生成注册表并保留业务 class | `debugAndReleaseTransformsPreserveServiceClassesAndGenerateRegistry` |
| 插件只允许 Android Application | 两个拒绝场景 TestKit 测试 |
| `checkImplementation=false` 跳过 require | `disabledPrecheckDoesNotRejectMissingRequiredService` |
| Application、Android Library、Java 项目模块、直接及传递 AAR 都能发现 | `discoversServicesFromProjectsAndDirectAndTransitiveAars` |
| Debug/Release 五类来源顺序和 alias 一致 | fixture 的 `verifyAllScopeRuntime` |
| 第二次运行 UP-TO-DATE，清理输出后 FROM-CACHE，SHA-256 不变 | `transformIsUpToDateAndRestoredFromCacheWithStableHash` |
| Release assemble 与 lint model 没有隐式任务依赖错误 | `releaseBuildTransformsClassesWithoutImplicitDependency` |
| 运行时类型和生成代码不引用 API 24 的 `java.util.function` 或 `Map` 默认方法 | `AndroidApiCompatibilityTest`、两个生成器测试、Debug/Release DEX 检查 |

### 发布安全

| 行为 | 证据 |
| --- | --- |
| 无发布凭据时 `projects` 可配置 | `projectsTaskSucceedsWithoutPublishingCredentials` |
| 无发布凭据时普通构建可执行 | `appDebugBuildSucceedsWithoutPublishingCredentials` |
| publish 在写仓库前以明确错误失败且不输出用户名 | `publishTaskRejectsMissingCredentialsBeforeRepositoryWrite` |
| sources JAR 同时包含 main 与 pluginEntry 源码 | `sourcesJarIncludesMainAndPluginEntrySources` |

## 当前缺口

### P1：直接影响公开语义

1. **`ServiceLoader` 选择 API 没有直接测试。** `firstPriority`、`lastPriority`、两个 `require*Priority()` 的空/非空行为只由实现推断。
2. **实例生命周期边界没有端到端测试。** 当前代码语义是：同一个 loader 重复遍历复用非单例实例，重新 `load()` 才创建新实例；`singleton=true` 跨 loader、跨接口复用。生成器只有源码字符串断言，没有运行时 identity 断言。
3. **`@AutoService` 自身没有契约测试。** retention、target、默认参数、多 value 和空 value 没有在 annotation 模块锁定。
4. **启用 `checkImplementation=true` 的完整构建路径没有 TestKit 测试。** validator 有单元测试，但缺少“满足 require 成功”和“三类 require 失败”的真实插件场景。

### P2：兼容性和边界

1. 缺少 Java 编译夹具验证 `@JvmStatic`、`@JvmOverloads`、属性 getter 和诊断 DTO 的 Java 调用形式。
2. 缺少自定义 build type 验证诊断确实按 `variant.debuggable`，而不是只覆盖名为 Debug/Release 的默认变体。
3. `excludeAlias()` 尚无端到端场景；组合 `exclude()` 和 `excludeClassName()` 已覆盖。
4. `logLevel` 的五个合法值及非法值回退没有完整测试。
5. 实现类缺少公开无参构造函数、未实现声明接口时，只有生成源码编译兜底，没有固定错误信息测试。

### P3：测试维护性

1. Demo app 的两个模板测试只验证 `2 + 2` 和 package name，对库行为没有回归价值。
2. TestKit 的 11 个测试会重复创建 AGP 构建，完整但较慢。可以拆分“提交必跑的快速单元测试”和“CI 必跑的全矩阵测试”，同时保留最终全量门禁。
3. 生成器单元测试主要断言源码片段。它们速度快，但不能替代真实编译；当前 TestKit 提供了互补证据，新增生成逻辑时应两层都补。

## 建议补测顺序

1. 为 `ServiceLoader` 增加手写测试 registry fixture，锁定选择、异常和实例生命周期。
2. 为 annotation 模块增加 Kotlin/Java 反射与默认值测试。
3. 为 require 开关和自定义 debuggable build type 增加 TestKit 场景。
4. 增加 Java consumer 编译测试。
5. 最后再处理日志级别和 demo 模板测试。

## 新增测试时的验收标准

- 一个公开语义至少有成功与失败/空结果两个方向。
- 生成源码断言必须有对应的真实编译或运行时场景。
- 涉及变体的逻辑至少覆盖一个自定义 build type。
- 涉及依赖范围的逻辑同时验证项目模块与外部 Maven AAR。
- 涉及 Release 隔离的逻辑检查产物内容，而不只检查返回值。

## 相关文档

- [API 参考](api-reference.md)
- [架构与类关系](architecture.md)
- [故障排查](troubleshooting.md)
