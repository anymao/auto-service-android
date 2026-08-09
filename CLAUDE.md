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

This project is indexed by GitNexus as **auto-service-android** (1023 symbols, 1199 relationships, 14 execution flows). Use the GitNexus MCP tools to understand code, assess impact, and navigate safely.

> Index stale? Run `node .gitnexus/run.cjs analyze` from the project root — it auto-selects an available runner. No `.gitnexus/run.cjs` yet? `npx gitnexus analyze` (npm 11 crash → `npm i -g gitnexus`; #1939).

## Always Do

- **MUST run impact analysis before editing any symbol.** Before modifying a function, class, or method, run `impact({target: "symbolName", direction: "upstream"})` and report the blast radius (direct callers, affected processes, risk level) to the user.
- **MUST run `detect_changes()` before committing** to verify your changes only affect expected symbols and execution flows. For regression review, compare against the default branch: `detect_changes({scope: "compare", base_ref: "master"})`.
- **MUST warn the user** if impact analysis returns HIGH or CRITICAL risk before proceeding with edits.
- When exploring unfamiliar code, use `query({search_query: "concept"})` to find execution flows instead of grepping. It returns process-grouped results ranked by relevance.
- When you need full context on a specific symbol — callers, callees, which execution flows it participates in — use `context({name: "symbolName"})`.
- For security review, `explain({target: "fileOrSymbol"})` lists taint findings (source→sink flows; needs `analyze --pdg`).

## Never Do

- NEVER edit a function, class, or method without first running `impact` on it.
- NEVER ignore HIGH or CRITICAL risk warnings from impact analysis.
- NEVER rename symbols with find-and-replace — use `rename` which understands the call graph.
- NEVER commit changes without running `detect_changes()` to check affected scope.

## Resources

| Resource | Use for |
|----------|---------|
| `gitnexus://repo/auto-service-android/context` | Codebase overview, check index freshness |
| `gitnexus://repo/auto-service-android/clusters` | All functional areas |
| `gitnexus://repo/auto-service-android/processes` | All execution flows |
| `gitnexus://repo/auto-service-android/process/{name}` | Step-by-step execution trace |

## CLI

| Task | Read this skill file |
|------|---------------------|
| Understand architecture / "How does X work?" | `.claude/skills/gitnexus/gitnexus-exploring/SKILL.md` |
| Blast radius / "What breaks if I change X?" | `.claude/skills/gitnexus/gitnexus-impact-analysis/SKILL.md` |
| Trace bugs / "Why is X failing?" | `.claude/skills/gitnexus/gitnexus-debugging/SKILL.md` |
| Rename / extract / split / refactor | `.claude/skills/gitnexus/gitnexus-refactoring/SKILL.md` |
| Tools, resources, schema reference | `.claude/skills/gitnexus/gitnexus-guide/SKILL.md` |
| Index, status, clean, wiki CLI commands | `.claude/skills/gitnexus/gitnexus-cli/SKILL.md` |

<!-- gitnexus:end -->
