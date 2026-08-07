# CLAUDE.md

本文件说明仓库开发约束。面向用户的接入方法见 [README.md](README.md)，故障决策见 [docs/troubleshooting.md](docs/troubleshooting.md)。

## 构建基线

- JDK 17
- Gradle Wrapper 8.13
- Android Gradle Plugin 8.13.0
- Kotlin 2.1.0
- 当前组件版本 0.0.13

不要新增 AGP 7.x、`applicationVariants`、`javaCompileProvider`、反射式 `toTransform` 或旧 Transform API 兼容路径。

## 模块边界

| 模块 | 边界 |
| --- | --- |
| `auto-service-annotation` | RUNTIME `@AutoService` 公共注解。 |
| `auto-service-registry` | 生成注册表存根和诊断 DTO。 |
| `auto-service-loader` | `load()`、`diagnose()` 和实例供应器。API 依赖 annotation 与 registry。 |
| `auto-service-plugin` | AGP Application 插件、全范围 metadata catalog、校验和确定性生成。 |
| `buildSrc` | 与插件模块同步的本地开发入口。 |
| `app` | 使用 buildSrc 插件的真实 APK 验收应用。 |

插件只能应用于 Android Application。Library/Java 模块可以声明实现，但不得各自生成注册表。

## 当前生成链路

1. Kotlin 插件入口通过 `ApplicationAndroidComponentsExtension` 遍历变体。
2. `ScopedArtifacts.Scope.ALL` + `ScopedArtifact.CLASSES` 提供 Application、项目模块、直接与传递 AAR/JAR class。
3. `ClassMetadataScanner` 只读取 class metadata，`ServiceCatalog` 统一排序、排除和重复类检测。
4. `RequiredServiceValidator` 在启用时校验最终 catalog。
5. `ServiceRegistryGenerator` 和 `ServiceRegistryDiagnosticsGenerator` 生成 Java 源；`DeterministicJarWriter` 写入唯一注册表类。
6. `variant.debuggable` 控制诊断内容：可调试变体完整，非调试变体为空报告。

`AutoServiceRegisterTask` 是 `@CacheableTask`，classpath 使用 `@CompileClasspath`。不要恢复依赖遍历顺序决定输出、当前时间戳或普通重复类覆盖行为。

生成源位于任务临时目录，例如 `app/build/tmp/androidAutoServiceRegisterDebug/src/`。最终变换产物由 AGP 管理在 `build/intermediates/classes/<variant>/ALL/` 下；不要把旧 `build/intermediates/auto_service/...` 路径写入代码或文档契约。

## 本地插件开发

仓库当前已配置 buildSrc 版本的插件入口，`app/build.gradle` 直接应用 `AutoServiceRegisterPlugin`。生产模块与 buildSrc 的以下文件需要保持行为同步：

- Kotlin 插件入口；
- Groovy 扫描、catalog、生成和确定性 JAR writer；
- AGP 8.13 / JDK 17 编译配置。

修改一个实现时，检查另一份是否需要同步。发布组件由 `auto-service-plugin` 模块生成，不要把 buildSrc 加入 Maven 发布。

## 常用验证

```bash
./gradlew projects
./gradlew :auto-service-annotation:test :auto-service-registry:test :auto-service-loader:test
./gradlew :auto-service-plugin:test
./gradlew :app:assembleDebug :app:assembleRelease
./gradlew :auto-service-loader:generatePomFileForMavenPublication \
  :auto-service-plugin:generatePomFileForMavenPublication
```

插件功能测试必须覆盖：

- debug/release 注册与诊断隔离；
- Application、Android Library、Java Library、直接 AAR、传递 AAR；
- alias、priority、singleton、require 和排除；
- 普通重复类失败；
- UP-TO-DATE、FROM-CACHE 与稳定哈希。

## 发布安全

- 仓库文件不得包含 `ALIYUN_USERNAME` 或 `ALIYUN_PASSWORD` 的值。
- 普通配置、测试和 assemble 不需要私服凭据。
- 发布任务只从环境变量或用户级 Gradle 属性读取凭据，缺失时必须在网络请求前失败。
- 不得输出用户名或密码。
- 历史中暴露的凭据需要仓库所有者在服务端轮换；删除 Git 工作树中的值不能替代轮换。
- annotation、registry、loader、plugin 必须使用同一 `VERSION` 发布。

## 公共行为约束

- `@AutoService` retention 在 0.0.13 仍为 RUNTIME。
- 排序键为 priority 升序、实现类名升序。
- 同一 singleton 实现注册多个接口时复用同一个实例。
- 非 debuggable 产物不能包含候选实现名、alias 或排除规则。
- 除生成注册表保留类外，同名普通 class 必须失败并报告两个来源。

<!-- gitnexus:start -->
# GitNexus — Code Intelligence

This project is indexed by GitNexus as **auto-service-android**. Use GitNexus to understand code, assess impact, and navigate safely.

> If the index is unavailable or stale, use `.gitnexus/run.cjs analyze` when the runner exists. Otherwise report the index limitation and verify signatures from source; do not invent graph results.

## Always Do

- Run upstream impact analysis before editing a function, class, or method.
- Warn before proceeding when impact is HIGH or CRITICAL.
- Run `detect_changes()` before every commit. For release regression review compare with `master`.
- Use `context()` for callers/callees and execution-flow participation when the index is healthy.

## Never Do

- Never edit a symbol before impact analysis.
- Never ignore HIGH or CRITICAL risk.
- Never rename symbols with text replacement; use graph-aware rename.
- Never commit without change detection.

<!-- gitnexus:end -->
