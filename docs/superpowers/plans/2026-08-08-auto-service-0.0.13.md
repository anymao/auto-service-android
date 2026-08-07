# Auto Service v0.0.13 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 Application 变体发现应用、所有子模块、直接与传递 AAR/JAR 中的服务实现，并提供安全、可缓存、可诊断的 v0.0.13 运行时与构建体验。

**Architecture:** 使用 AGP 8.13 `ScopedArtifacts.Scope.ALL` 把最终变体的完整 class 集交给单一转换任务。任务通过元数据扫描器构建 `ServiceCatalog`，再分别驱动校验、注册表生成、Debug 诊断生成和确定性 jar 合并；运行时诊断契约位于 registry 模块，loader 只提供入口。

**Tech Stack:** Kotlin 2.1.0、Groovy、JavaPoet 1.13.0、Javassist 3.28.0、Android Gradle Plugin 8.13.0、Gradle TestKit 8.13、JDK 17、JUnit 4。

## Global Constraints

- Android Gradle Plugin 固定为 8.13.0，Gradle Wrapper 固定为 8.13，构建 JDK 固定为 17。
- 插件只应用于 `com.android.application`；Android Library、Java/Kotlin 模块和外部 AAR/JAR 只贡献服务实现。
- 完整运行时诊断由 `variant.debuggable` 决定，不能通过变体名称判断。
- `ServiceLoader.load()`、priority、alias、singleton、require 和排除规则的公开语义保持兼容。
- 普通重复 class 必须失败；仅精确保留类 `ServiceRegistry` 和 `ServiceRegistryDiagnostics` 可由变体生成版本替换。
- v0.0.13 保留 Javassist 与 `@AutoService` 的 `RUNTIME` 保留策略；ASM 和 `BINARY` 分别留到后续版本评估。
- 所有新增代码注释、构建错误和项目文档使用中文；公开类型名与 API 标识符使用英文。
- 每次修改已有函数、类或方法前，必须调用 GitNexus `impact(..., direction: "upstream")` 并向用户报告直接调用者、受影响流程与风险；HIGH/CRITICAL 时先警告再继续。
- 每次提交前只暂存该任务文件，调用 GitNexus `detect_changes(scope: "staged")`，检查 `rtk git diff --cached --check` 后再提交。
- 不修改用户现有的 `.idea/misc.xml` 和未跟踪文件 `docs/superpowers/specs/2026-08-02-phase1-developer-experience-design.md`。

---

### Task 1: 建立 registry 诊断契约与无环运行时依赖

**Files:**
- Modify: `settings.gradle`
- Modify: `auto-service-registry/build.gradle`
- Modify: `auto-service-registry/src/main/java/com/anymore/auto/ServiceRegistry.kt`
- Create: `auto-service-registry/src/main/java/com/anymore/auto/ServiceDiagnosticAvailability.kt`
- Create: `auto-service-registry/src/main/java/com/anymore/auto/ServiceDiagnosticStatus.kt`
- Create: `auto-service-registry/src/main/java/com/anymore/auto/ServiceDiagnosticEntry.kt`
- Create: `auto-service-registry/src/main/java/com/anymore/auto/ServiceDiagnosticReport.kt`
- Create: `auto-service-registry/src/main/java/com/anymore/auto/ServiceRegistryDiagnostics.kt`
- Create: `auto-service-registry/src/test/kotlin/com/anymore/auto/ServiceDiagnosticReportTest.kt`
- Modify: `auto-service-loader/build.gradle`
- Modify: `auto-service-loader/src/main/java/com/anymore/auto/ServiceLoader.kt`
- Create: `auto-service-loader/src/test/kotlin/com/anymore/auto/ServiceLoaderDiagnosticTest.kt`

**Interfaces:**
- Consumes: existing `ServiceRegistry.get(Class<S>, String)` and `ServiceLoader.load(Class<T>, String)` contracts.
- Produces: `ServiceRegistryDiagnostics.get(Class<*>, String): ServiceDiagnosticReport` and four `ServiceLoader.diagnose` overloads used by generated diagnostics and functional tests.

- [ ] **Step 1: 运行公共符号影响分析**

Run GitNexus:

```text
impact({repo: "auto-service-android", target: "ServiceRegistry", direction: "upstream", includeTests: true})
impact({repo: "auto-service-android", target: "ServiceLoader", direction: "upstream", includeTests: true})
```

Expected: 报告 `MainActivity`、loader/registry 之间的直接依赖；记录风险等级。若为 HIGH/CRITICAL，先向用户说明影响范围。

- [ ] **Step 2: 先写 registry 诊断模型失败测试**

Create `ServiceDiagnosticReportTest.kt` with these assertions:

```kotlin
package com.anymore.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceDiagnosticReportTest {
    @Test
    fun `报告计算注册数量匹配数量和可用别名`() {
        val report = ServiceDiagnosticReport(
            serviceClassName = Runnable::class.java.name,
            requestedAlias = "debug",
            availability = ServiceDiagnosticAvailability.AVAILABLE,
            entries = listOf(
                ServiceDiagnosticEntry("sample.MainTask", -10, "", true, ServiceDiagnosticStatus.REGISTERED, null),
                ServiceDiagnosticEntry("sample.DebugTask", 0, "debug", false, ServiceDiagnosticStatus.REGISTERED, null),
                ServiceDiagnosticEntry("sample.LegacyTask", 5, "debug", false, ServiceDiagnosticStatus.EXCLUDED, "className /.*Legacy.*/")
            )
        )

        assertEquals(2, report.registeredCount)
        assertEquals(1, report.matchingCount)
        assertEquals(linkedSetOf("", "debug"), report.availableAliases)
        assertTrue(report.toString().contains("sample.LegacyTask"))
    }

    @Test
    fun `未应用插件时返回稳定的不可用报告`() {
        val report = ServiceRegistryDiagnostics.get(Runnable::class.java, "")
        assertEquals(ServiceDiagnosticAvailability.UNAVAILABLE_PLUGIN_NOT_APPLIED, report.availability)
        assertTrue(report.entries.isEmpty())
    }
}
```

- [ ] **Step 3: 验证测试先失败**

Run:

```bash
rtk ./gradlew :auto-service-registry:test --tests com.anymore.auto.ServiceDiagnosticReportTest
```

Expected: FAIL，诊断类型尚不存在；若提示找不到 `:auto-service-registry` 以外的 plugin 子项目不影响本步骤。

- [ ] **Step 4: 实现 registry 公共模型和存根**

Implement exact enum values:

```kotlin
enum class ServiceDiagnosticAvailability {
    AVAILABLE,
    UNAVAILABLE_IN_NON_DEBUG_BUILD,
    UNAVAILABLE_PLUGIN_NOT_APPLIED
}

enum class ServiceDiagnosticStatus {
    REGISTERED,
    EXCLUDED
}
```

Implement entries and derived report fields:

```kotlin
data class ServiceDiagnosticEntry(
    val implementationClassName: String,
    val priority: Int,
    val alias: String,
    val singleton: Boolean,
    val status: ServiceDiagnosticStatus,
    val exclusionRule: String?
)

data class ServiceDiagnosticReport(
    val serviceClassName: String,
    val requestedAlias: String,
    val availability: ServiceDiagnosticAvailability,
    val entries: List<ServiceDiagnosticEntry>
) {
    val registeredCount: Int
        get() = entries.count { it.status == ServiceDiagnosticStatus.REGISTERED }

    val matchingCount: Int
        get() = entries.count {
            it.status == ServiceDiagnosticStatus.REGISTERED &&
                (requestedAlias.isEmpty() || it.alias == requestedAlias)
        }

    val availableAliases: Set<String>
        get() = entries.asSequence()
            .filter { it.status == ServiceDiagnosticStatus.REGISTERED }
            .map { it.alias }
            .toCollection(linkedSetOf())

    override fun toString(): String = buildString {
        append(serviceClassName).append(": ")
        if (availability != ServiceDiagnosticAvailability.AVAILABLE) {
            append("diagnostics unavailable (").append(availability).append(')')
            return@buildString
        }
        append(entries.size).append(" candidates, ").append(registeredCount).append(" registered\n")
        entries.forEach { entry ->
            append(if (entry.status == ServiceDiagnosticStatus.REGISTERED) "  ✓ " else "  × ")
            append(entry.implementationClassName)
                .append(" (priority=").append(entry.priority)
                .append(", alias=\"").append(entry.alias).append('\"')
            if (entry.singleton) append(", singleton")
            append(')')
            entry.exclusionRule?.let { append(" — excluded by ").append(it) }
            append('\n')
        }
        append("Available aliases: ").append(availableAliases)
        if (requestedAlias.isNotEmpty()) {
            append("\nRequested alias \"").append(requestedAlias).append("\": ")
                .append(matchingCount).append(" matches")
        }
    }
}
```

Implement the registry stubs:

```kotlin
object ServiceRegistryDiagnostics {
    @JvmStatic
    @JvmOverloads
    fun get(clazz: Class<*>, alias: String = "") = ServiceDiagnosticReport(
        clazz.name,
        alias,
        ServiceDiagnosticAvailability.UNAVAILABLE_PLUGIN_NOT_APPLIED,
        emptyList()
    )
}
```

Change `ServiceRegistry.get()` to throw an `IllegalStateException` whose Chinese message says the Application module must apply the `auto-service` plugin.

- [ ] **Step 5: 消除 registry → loader 反向依赖并接好 loader API**

In `settings.gradle`, always include `':auto-service-plugin'`; remove the conditional around it so later plugin tests are addressable.

In `auto-service-registry/build.gradle`, remove `compileOnly("com.anymore:auto-service-loader:$VERSION")` and add `testImplementation 'junit:junit:4.13.2'`.

In `auto-service-loader/build.gradle`, replace remote dependencies with:

```groovy
api project(':auto-service-annotation')
api project(':auto-service-registry')
```

Add to `ServiceLoader` companion object:

```kotlin
@JvmStatic
@JvmOverloads
fun diagnose(clazz: Class<*>, alias: String = ""): ServiceDiagnosticReport =
    ServiceRegistryDiagnostics.get(clazz, alias)

inline fun <reified T> diagnose(alias: String = ""): ServiceDiagnosticReport =
    diagnose(T::class.java, alias)
```

- [ ] **Step 6: 写 loader 入口测试并运行两个模块测试**

Create `ServiceLoaderDiagnosticTest.kt`:

```kotlin
package com.anymore.auto

import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceLoaderDiagnosticTest {
    @Test
    fun `泛型诊断入口委托给诊断注册表`() {
        val report = ServiceLoader.diagnose<Runnable>("debug")
        assertEquals(Runnable::class.java.name, report.serviceClassName)
        assertEquals("debug", report.requestedAlias)
        assertEquals(ServiceDiagnosticAvailability.UNAVAILABLE_PLUGIN_NOT_APPLIED, report.availability)
    }
}
```

Run:

```bash
rtk ./gradlew :auto-service-registry:test :auto-service-loader:test
```

Expected: PASS。

- [ ] **Step 7: 检查影响并提交运行时契约**

Stage only Task 1 files, then run:

```text
detect_changes({repo: "auto-service-android", scope: "staged"})
```

Run:

```bash
rtk git diff --cached --check
rtk git commit -m "feat: add structured service diagnostics contract"
```

Expected: 仅 runtime/build graph 文件进入提交，现有用户改动仍未暂存。

---

### Task 2: 引入元数据扫描器与 ServiceCatalog

**Files:**
- Create: `auto-service-plugin/src/main/groovy/com/anymore/auto/gradle/ClassOrigin.groovy`
- Create: `auto-service-plugin/src/main/groovy/com/anymore/auto/gradle/ServiceCandidateStatus.groovy`
- Create: `auto-service-plugin/src/main/groovy/com/anymore/auto/gradle/ServiceCandidate.groovy`
- Create: `auto-service-plugin/src/main/groovy/com/anymore/auto/gradle/ServiceCatalog.groovy`
- Create: `auto-service-plugin/src/main/groovy/com/anymore/auto/gradle/ClassMetadataScanner.groovy`
- Create: `auto-service-plugin/src/test/groovy/com/anymore/auto/gradle/ClassMetadataScannerTest.groovy`
- Create: `auto-service-plugin/src/test/groovy/com/anymore/auto/gradle/ServiceCatalogTest.groovy`
- Modify: `auto-service-plugin/src/main/groovy/com/anymore/auto/gradle/AutoServiceRegisterAction.groovy`
- Modify: `auto-service-plugin/src/test/groovy/com/anymore/auto/gradle/AutoServiceExtensionTest.groovy`
- Modify: `auto-service-plugin/build.gradle`

**Interfaces:**
- Consumes: input directories/jars and `Set<ExclusiveRule>`.
- Produces: `ClassMetadataScanner.scan(Collection<File>): ServiceCatalog`; catalog queries feed all later generators and validators.

- [ ] **Step 1: 运行扫描核心影响分析**

```text
impact({repo: "auto-service-android", target: "AutoServiceRegisterAction", direction: "upstream", includeTests: true})
impact({repo: "auto-service-android", target: "Element", direction: "upstream", includeTests: true})
```

Expected: 识别 task、extension test 和代码生成路径；HIGH/CRITICAL 时先报告。

- [ ] **Step 2: 写 catalog 排序、排除和重复类失败测试**

Create `ServiceCatalogTest.groovy` covering this contract:

```groovy
@Test
void '注册项按优先级和类名排序并保留排除项'() {
    def catalog = new ServiceCatalog()
    catalog.addClass(new ClassOrigin('sample.Zebra', 'app', 'sample/Zebra.class', 'a'))
    catalog.addClass(new ClassOrigin('sample.Alpha', 'lib.jar', 'sample/Alpha.class', 'b'))
    catalog.addCandidate(new ServiceCandidate('java.lang.Runnable', 'sample.Zebra', 0, '', false,
            ServiceCandidateStatus.REGISTERED, null, catalog.classOrigin('sample.Zebra')))
    catalog.addCandidate(new ServiceCandidate('java.lang.Runnable', 'sample.Alpha', -1, 'debug', true,
            ServiceCandidateStatus.EXCLUDED, 'alias /debug/', catalog.classOrigin('sample.Alpha')))

    assertEquals(['sample.Zebra'], catalog.registeredFor('java.lang.Runnable')*.implementationClassName)
    assertEquals(['sample.Alpha'], catalog.excludedCandidates()*.implementationClassName)
    assertEquals(2, catalog.candidateImplementationCount())
}

@Test
void '不同来源出现同名普通类时失败'() {
    def catalog = new ServiceCatalog()
    catalog.addClass(new ClassOrigin('sample.Duplicate', 'first.jar', 'sample/Duplicate.class', 'a'))
    def exception = assertThrows(GradleException) {
        catalog.addClass(new ClassOrigin('sample.Duplicate', 'second.jar', 'sample/Duplicate.class', 'b'))
    }
    assertTrue(exception.message.contains('first.jar'))
    assertTrue(exception.message.contains('second.jar'))
}
```

- [ ] **Step 3: 写目录与 jar 注解元数据扫描失败测试**

In `ClassMetadataScannerTest`, compile a Java source with `ToolProvider.systemJavaCompiler` and the test runtime classpath:

```groovy
String source = '''
package sample;
import com.anymore.auto.AutoService;
@AutoService(value = { Runnable.class }, priority = -5, alias = "debug", singleton = true)
public final class DebugTask implements Runnable { public void run() {} }
'''
```

Assert directory scan and a jar containing the resulting class both produce one candidate with:

```groovy
assertEquals('java.lang.Runnable', candidate.serviceClassName)
assertEquals('sample.DebugTask', candidate.implementationClassName)
assertEquals(-5, candidate.priority)
assertEquals('debug', candidate.alias)
assertTrue(candidate.singleton)
```

Compile a second isolated fixture that declares the same annotation FQCN with `@Retention(RetentionPolicy.CLASS)` and assert the runtime-invisible annotation produces the same candidate. Add a truncated `.class` input and assert scanning fails with a `GradleException` containing its normalized container and entry names rather than a raw Javassist stack trace.

Run:

```bash
rtk ./gradlew :auto-service-plugin:test --tests '*ServiceCatalogTest' --tests '*ClassMetadataScannerTest'
```

Expected: FAIL because the new scanner/catalog types are absent.

- [ ] **Step 4: 实现不可变来源和候选模型**

Implement constructors, final fields, equality where needed, and candidate ordering:

```groovy
int compareTo(ServiceCandidate other) {
    int priorityOrder = priority <=> other.priority
    return priorityOrder != 0 ? priorityOrder : implementationClassName <=> other.implementationClassName
}
```

Use exact status values `REGISTERED` and `EXCLUDED`. `ClassOrigin.containerName` stores a normalized file name, never an absolute path.

- [ ] **Step 5: 实现 catalog 查询和保留类例外**

Define exact reserved checks:

```groovy
static boolean isGeneratedReservedClass(String className) {
    className == 'com.anymore.auto.ServiceRegistry' ||
            className == 'com.anymore.auto.ServiceRegistryDiagnostics' ||
            className.startsWith('com.anymore.auto.ServiceRegistryDiagnostics$')
}
```

`addClass` ignores duplicate validation only for those reserved names. All public query results are unmodifiable and stably sorted.

- [ ] **Step 6: 实现 Javassist ClassFile 元数据扫描器**

Use the annotation name constant rather than linking the annotation class:

```groovy
private static final String AUTO_SERVICE_ANNOTATION = 'com.anymore.auto.AutoService'
```

Read classes with `new ClassFile(new DataInputStream(stream))`; inspect both `AnnotationsAttribute.visibleTag` and `invisibleTag`. Parse `ArrayMemberValue`, `IntegerMemberValue`, `StringMemberValue`, and `BooleanMemberValue`, supplying defaults when a member value is absent. Compute SHA-256 while reading class bytes and use the same byte array to build `ClassFile`.

Match exclusions before adding each candidate:

```groovy
ExclusiveRule matched = rules.find {
    className.matches(it.className) && alias.matches(it.alias)
}
```

- [ ] **Step 7: 让 Action 消费 catalog，删除旧 Element 数据流**

Replace `load()`, `loadClass()`, `loadJar()`, `loadAutoServices()` and nested `Element` with `ClassMetadataScanner`. For this task, keep existing JavaPoet generation inside Action, converting `catalog.registeredByService()` to the stable queue/list structure it needs.

Update `AutoServiceExtensionTest` to construct `ServiceCandidate` instead of `AutoServiceRegisterAction.Element`.

Remove plugin production dependency on `com.anymore:auto-service-annotation:$VERSION`; retain `testImplementation project(':auto-service-annotation')` for Java fixture compilation.

- [ ] **Step 8: 运行扫描与现有插件测试**

```bash
rtk ./gradlew :auto-service-plugin:test
```

Expected: PASS；现有 registry 生成、排除规则与排序测试保持通过。

- [ ] **Step 9: 检查影响并提交扫描模型**

Stage Task 2 files, run staged `detect_changes`, then:

```bash
rtk git diff --cached --check
rtk git commit -m "refactor: introduce service metadata catalog"
```

---

### Task 3: 拆分生成器并实现 Debug/Release 诊断与 require 校验

**Files:**
- Create: `auto-service-plugin/src/main/groovy/com/anymore/auto/gradle/ServiceRegistryGenerator.groovy`
- Create: `auto-service-plugin/src/main/groovy/com/anymore/auto/gradle/ServiceRegistryDiagnosticsGenerator.groovy`
- Create: `auto-service-plugin/src/main/groovy/com/anymore/auto/gradle/RequiredServiceValidator.groovy`
- Create: `auto-service-plugin/src/main/groovy/com/anymore/auto/gradle/AutoServiceLog.groovy`
- Create: `auto-service-plugin/src/test/groovy/com/anymore/auto/gradle/ServiceRegistryGeneratorTest.groovy`
- Create: `auto-service-plugin/src/test/groovy/com/anymore/auto/gradle/ServiceRegistryDiagnosticsGeneratorTest.groovy`
- Create: `auto-service-plugin/src/test/groovy/com/anymore/auto/gradle/RequiredServiceValidatorTest.groovy`
- Create: `auto-service-plugin/src/test/groovy/com/anymore/auto/gradle/AutoServiceLogTest.groovy`
- Modify: `auto-service-plugin/src/main/groovy/com/anymore/auto/gradle/AutoServiceRegisterAction.groovy`
- Modify: `auto-service-plugin/src/main/groovy/com/anymore/auto/gradle/AutoServiceRegisterTask.groovy`
- Modify: `auto-service-plugin/src/main/groovy/com/anymore/auto/gradle/AutoServiceExtension.groovy`
- Modify: `auto-service-plugin/src/test/groovy/com/anymore/auto/gradle/AutoServiceAgp8FunctionalTest.groovy`
- Modify: `auto-service-plugin/src/test/resources/fixtures/agp8/app/build.gradle`

**Interfaces:**
- Consumes: `ServiceCatalog`, required service map, `diagnosticsEnabled`, source directory and log level.
- Produces: `ServiceRegistry.java`, `ServiceRegistryDiagnostics.java`, deterministic validation errors and one INFO summary.

- [ ] **Step 1: 运行生成与校验符号影响分析**

```text
impact({repo: "auto-service-android", target: "createServiceRegistry", direction: "upstream", includeTests: true})
impact({repo: "auto-service-android", target: "preCheckRequiredServices", direction: "upstream", includeTests: true})
impact({repo: "auto-service-android", target: "AutoServiceExtension", direction: "upstream", includeTests: true})
```

- [ ] **Step 2: 写注册表和诊断生成失败测试**

Build a catalog with one registered and one excluded candidate. Assert `ServiceRegistryGenerator.generate(catalog).toString()` contains registered implementation construction but not excluded construction.

For Debug diagnostics, assert generated source contains both candidates plus:

```text
ServiceDiagnosticAvailability.AVAILABLE
ServiceDiagnosticStatus.REGISTERED
ServiceDiagnosticStatus.EXCLUDED
```

For Release diagnostics, assert source contains `UNAVAILABLE_IN_NON_DEBUG_BUILD` and does not contain either implementation class name, alias, or exclusion rule.

Run:

```bash
rtk ./gradlew :auto-service-plugin:test --tests '*ServiceRegistryGeneratorTest' --tests '*ServiceRegistryDiagnosticsGeneratorTest'
```

Expected: FAIL because generators are not extracted.

- [ ] **Step 3: 写三类 require 失败测试**

Create cases for:

```groovy
validator.validate(['sample.Missing': [''] as Set], emptyCatalog)
validator.validate(['sample.Task': [''] as Set], allExcludedCatalog)
validator.validate(['sample.Task': ['production'] as Set], aliasMismatchCatalog)
```

Assert messages respectively contain:

```text
No @AutoService implementation was found
all were excluded
Available aliases: ["dev", "staging"]
```

Also assert implementations are listed in priority/class-name order.

- [ ] **Step 4: 提取 ServiceRegistryGenerator**

Move JavaPoet registry generation out of Action without changing generated runtime behavior. Generator receives only `ServiceCatalog`; it must not read files, Gradle Project, environment variables or current time. Generate `ServiceRegistry` as `public final` so its JVM visibility matches the public registry stub.

Keep singleton supplier reuse keyed by implementation class, so one singleton registered to multiple interfaces still shares one supplier. Add a source assertion that both interface registrations reference the same generated `supplier0` variable and only one `newInstance()` body is emitted.

- [ ] **Step 5: 实现诊断生成器**

Generate this public static contract in both variants:

```java
public static ServiceDiagnosticReport get(Class<?> clazz, String alias)
```

Debug implementation uses a static unmodifiable map from service class name to unmodifiable `List<ServiceDiagnosticEntry>`. Release implementation constructs a report with `UNAVAILABLE_IN_NON_DEBUG_BUILD` and `Collections.emptyList()`; it must not emit candidate literals.

- [ ] **Step 6: 实现校验器与实例级日志器**

`RequiredServiceValidator.validate()` throws one `GradleException` containing all failed requirements in stable order. It uses the full catalog for context but evaluates success against registered candidates only.

Replace static mutable logging state with:

```groovy
final class AutoServiceLog {
    final int level
    void debug(String message) { if (level <= Logger.DEBUG) println("[auto-service][DEBUG] $message") }
    void info(String message) { if (level <= Logger.INFO) println("[auto-service] $message") }
}
```

Pass one logger instance through Action/scanner/generators. Do not mutate a global log level, so parallel variants cannot affect each other.

Inject a `PrintStream` into `AutoServiceLog` with `System.out` as the production default. In `AutoServiceLogTest`, capture INFO output and assert an exact summary with unique class、candidate、binding、interface、excluded and elapsed counts. Add a zero-registration case asserting the actionable `@AutoService`/plugin hint is printed.

- [ ] **Step 7: 让 Action 只负责编排并编译两个生成源**

Change Action flow to:

```groovy
ServiceCatalog catalog = scanner.scan(classpath.files)
validator.validate(requiredServices, catalog)
registryGenerator.write(catalog, targetDir)
diagnosticsGenerator.write(catalog, targetDir, diagnosticsEnabled)
log.info(summaryFor(catalog, elapsedMillis))
return catalog
```

Remove the incorrect `@TaskAction` annotation from Action; only `AutoServiceRegisterTask.run()` remains a task action.

Update task source compilation to collect every generated `**/*.java` file in stable order and pass all paths to `ToolProvider.systemJavaCompiler`.

Update the functional fixture to copy the code source containing `ServiceDiagnosticReport` into `libs/auto-service-registry.jar`, then add that jar as an app `implementation` dependency. The existing file-based loader dependency does not carry Gradle POM transitivity, so this explicit fixture dependency is required before generated diagnostics can compile and load.

- [ ] **Step 8: 稳定扩展集合与任务输入**

Change `AutoServiceExtension` requires/exclusives backing collections to `LinkedHashMap` and `LinkedHashSet`. Add task inputs:

```groovy
@Input abstract Property<Boolean> getDiagnosticsEnabled()
@Input abstract Property<Integer> getLogLevel()
```

Give both properties conventions so unit tests can instantiate the task before plugin wiring: `false` and `Logger.INFO`.

- [ ] **Step 9: 运行生成、校验和运行时回归测试**

```bash
rtk ./gradlew :auto-service-plugin:test :auto-service-registry:test :auto-service-loader:test
```

Expected: PASS。

- [ ] **Step 10: 检查影响并提交生成与诊断逻辑**

Stage Task 3 files, run staged `detect_changes`, then:

```bash
rtk git diff --cached --check
rtk git commit -m "feat: generate variant-aware service diagnostics"
```

---

### Task 4: 使用类型化 AGP 8.13 API 接入 Scope.ALL

**Files:**
- Delete: `auto-service-plugin/src/main/groovy/com/anymore/auto/gradle/AutoServiceRegisterPlugin.groovy`
- Create: `auto-service-plugin/src/main/kotlin/com/anymore/auto/gradle/AutoServiceRegisterPlugin.kt`
- Modify: `auto-service-plugin/build.gradle`
- Modify: `buildSrc/build.gradle`
- Modify: `auto-service-plugin/src/main/groovy/com/anymore/auto/gradle/AutoServiceExtension.groovy`
- Modify: `auto-service-plugin/src/test/groovy/com/anymore/auto/gradle/AutoServiceExtensionTest.groovy`
- Modify: `auto-service-plugin/src/test/groovy/com/anymore/auto/gradle/AutoServiceAgp8FunctionalTest.groovy`
- Modify: `auto-service-plugin/src/test/resources/fixtures/agp8/settings.gradle`
- Modify: `auto-service-plugin/src/test/resources/fixtures/agp8/app/build.gradle`
- Create: `auto-service-plugin/src/test/resources/fixtures/agp8/feature/build.gradle`
- Create: `auto-service-plugin/src/test/resources/fixtures/agp8/feature/src/main/AndroidManifest.xml`
- Create: `auto-service-plugin/src/test/resources/fixtures/agp8/feature/src/main/java/test/feature/FeatureTask.java`

**Interfaces:**
- Consumes: AGP 8.13 `ApplicationAndroidComponentsExtension`, `ScopedArtifacts.Scope.ALL`, `ScopedArtifact.CLASSES`.
- Produces: one `androidAutoServiceRegister<Variant>` task per Application variant with complete class scope and `debuggable`-derived diagnostics input.

- [ ] **Step 1: 运行插件入口与任务影响分析**

```text
impact({repo: "auto-service-android", target: "AutoServiceRegisterPlugin", direction: "upstream", includeTests: true})
impact({repo: "auto-service-android", target: "AutoServiceRegisterTask", direction: "upstream", includeTests: true})
```

- [ ] **Step 2: 先写功能测试断言完整作用域和变体诊断开关**

Extend the functional fixture with an Android Library `:feature` containing `feature.FeatureTask`. Add `implementation project(':feature')` to the app.

Use this minimal feature build:

```groovy
plugins { id 'com.android.library' version '8.13.0' }
android {
    namespace 'test.feature'
    compileSdk 35
    defaultConfig { minSdk 23 }
}
dependencies { compileOnly files('../libs/auto-service-annotation.jar') }
```

Add `include ':feature'` to fixture settings and annotate `FeatureTask` as a `Runnable` service. This test must fail under `Scope.PROJECT` because the feature class is outside the app project scope.

Extend `verifyAutoServiceOutputs` to assert:

```groovy
assert debugArchive.getEntry('feature/FeatureTask.class') != null
assert releaseArchive.getEntry('feature/FeatureTask.class') != null
assert tasks.named('androidAutoServiceRegisterDebug').get().diagnosticsEnabled.get()
assert !tasks.named('androidAutoServiceRegisterRelease').get().diagnosticsEnabled.get()
```

Run:

```bash
rtk ./gradlew :auto-service-plugin:test --tests '*AutoServiceAgp8FunctionalTest.debugAndReleaseTransformsPreserveServiceClassesAndGenerateRegistry'
```

Expected: FAIL because current scope is PROJECT and diagnostics is not wired.

- [ ] **Step 3: 将插件入口转换为 Kotlin 类型 API**

Implement the variant registration with no reflection or proxy:

```kotlin
val extension = project.extensions.create(
    "autoService",
    AutoServiceExtension::class.java,
    false,
    LinkedHashMap<String, Set<String>>()
)

val task = project.tasks.register<AutoServiceRegisterTask>(
    "androidAutoServiceRegister${variant.name.replaceFirstChar(Char::uppercase)}"
) {
    compileClasspath.from(variant.compileClasspath)
    sourceCompatibility.set(extension.sourceCompatibility)
    diagnosticsEnabled.set(variant.debuggable)
    logLevel.set(extension.logLevel)
    serviceRequirements.set(if (extension.checkImplementation) extension.requireServices else emptyMap())
    excludedClassNamePatterns.set(extension.exclusiveRules.map { it.className })
    excludedAliasPatterns.set(extension.exclusiveRules.map { it.alias })
}

variant.artifacts.forScope(ScopedArtifacts.Scope.ALL)
    .use(task)
    .toTransform(
        ScopedArtifact.CLASSES,
        { it.inputJars },
        { it.inputDirectories },
        { it.outputJar }
    )
```

Keep the implementation class FQCN unchanged so plugin metadata remains compatible.

- [ ] **Step 4: 明确非 Application 使用错误**

Track whether `com.android.application` configures the plugin. If `com.android.library` is present, throw `GradleException("auto-service 只能应用于 Android Application 模块")`. After project evaluation, throw the same actionable error when no Application plugin was applied.

- [ ] **Step 5: 更新 plugin 模块和 buildSrc 编译源**

Apply Kotlin in `auto-service-plugin/build.gradle`; keep Groovy for the remaining sources. Ensure Java/Kotlin targets are 17 for plugin/buildSrc while generated registry source level continues to use the extension value.

In `buildSrc/build.gradle`:

- use Kotlin Gradle plugin 2.1.0;
- remove private Maven repositories and the old annotation 0.0.9 dependency;
- point Kotlin sources at `../auto-service-plugin/src/main/kotlin`;
- keep Groovy/resources source directories pointing at the plugin module;
- set Java/Kotlin JVM target to 17.

Change the generated-source default from `"1.7"` to `"1.8"` in `AutoServiceExtension` and add an extension test for the default. Keep the existing DSL override working; v0.0.13 does not silently force generated registry bytecode to JDK 17.

- [ ] **Step 6: 运行基本全作用域功能测试**

```bash
rtk ./gradlew :auto-service-plugin:test --tests '*AutoServiceAgp8FunctionalTest'
```

Expected: PASS for existing tests and new feature-module assertion；日志出现 Debug 与 Release 两个转换任务。

- [ ] **Step 7: 检查影响并提交 AGP 接入**

Stage Task 4 files, run staged `detect_changes`, then:

```bash
rtk git diff --cached --check
rtk git commit -m "feat: aggregate services from the full AGP class scope"
```

---

### Task 5: 实现确定性 class 合并与 Gradle Build Cache

**Files:**
- Create: `auto-service-plugin/src/main/groovy/com/anymore/auto/gradle/DeterministicJarWriter.groovy`
- Create: `auto-service-plugin/src/test/groovy/com/anymore/auto/gradle/DeterministicJarWriterTest.groovy`
- Modify: `auto-service-plugin/src/main/groovy/com/anymore/auto/gradle/AutoServiceRegisterTask.groovy`
- Modify: `auto-service-plugin/src/test/groovy/com/anymore/auto/gradle/AutoServiceRegisterTaskTest.groovy`
- Modify: `auto-service-plugin/src/test/groovy/com/anymore/auto/gradle/AutoServiceAgp8FunctionalTest.groovy`

**Interfaces:**
- Consumes: sorted input jars/directories and compiled generated classes directory.
- Produces: one output jar containing every non-reserved class exactly once plus generated reserved classes, with stable entry order and timestamps.

- [ ] **Step 1: 运行 task 合并方法影响分析**

```text
impact({repo: "auto-service-android", target: "run", file_path: "auto-service-plugin/src/main/groovy/com/anymore/auto/gradle/AutoServiceRegisterTask.groovy", direction: "upstream", includeTests: true})
impact({repo: "auto-service-android", target: "copyJar", direction: "upstream", includeTests: true})
impact({repo: "auto-service-android", target: "copyDirectory", direction: "upstream", includeTests: true})
```

- [ ] **Step 2: 写确定性 jar 失败测试**

Create two input jars/directories in different iteration orders, write outputs twice, and assert:

```groovy
assertArrayEquals(firstOutput.bytes, secondOutput.bytes)
assertEquals(0L, new JarFile(firstOutput).getJarEntry('sample/Task.class').time)
```

Add an input stub `com/anymore/auto/ServiceRegistry.class` and generated replacement; assert final jar contains one entry whose bytes equal the generated class. Assert ordinary duplicate `sample/Duplicate.class` from two containers throws with both container names.

- [ ] **Step 3: 验证新测试先失败**

```bash
rtk ./gradlew :auto-service-plugin:test --tests '*DeterministicJarWriterTest'
```

Expected: FAIL because writer does not exist/current copy order is not deterministic.

- [ ] **Step 4: 实现 writer**

Writer behavior:

```text
1. Enumerate input class entries into a sorted map keyed by entry name.
2. Ignore reserved registry entries from ordinary inputs.
3. Reject duplicate ordinary class entry names with two normalized origins.
4. Add generated class entries last and require them to be reserved names.
5. Write entries sorted by name with method DEFLATED and time 0L.
6. Do not copy non-class resources from ScopedArtifact.CLASSES inputs.
```

Use buffered streams and always close `JarFile`, input streams and `JarOutputStream` via `withCloseable`/`try-finally`.

- [ ] **Step 5: 使任务可缓存且输入完整**

Annotate task with `@CacheableTask`. Use:

```groovy
@InputFiles @Classpath
abstract ListProperty<RegularFile> getInputJars()

@InputFiles @PathSensitive(PathSensitivity.RELATIVE)
abstract ListProperty<Directory> getInputDirectories()

@CompileClasspath
abstract ConfigurableFileCollection getCompileClasspath()
```

Keep source compatibility、requirements、exclusions、diagnostics flag and log level as `@Input`, output jar as `@OutputFile`. Generated source and compiled class directories remain under `temporaryDir` and are not declared outputs.

- [ ] **Step 6: 增加 UP-TO-DATE、缓存和哈希功能测试**

Run the same fixture build twice and assert the second result for `:app:androidAutoServiceRegisterDebug` is `UP_TO_DATE`. Run once with a shared TestKit build cache, delete only the fixture `build/` directories, rerun and assert `FROM_CACHE`.

Compute SHA-256 for output jars from two clean identical fixture copies and assert equality.

- [ ] **Step 7: 运行 writer、task 和功能测试**

```bash
rtk ./gradlew :auto-service-plugin:test
```

Expected: PASS including deterministic output and cache assertions。

- [ ] **Step 8: 检查影响并提交缓存支持**

Stage Task 5 files, run staged `detect_changes`, then:

```bash
rtk git diff --cached --check
rtk git commit -m "perf: make service aggregation cacheable and reproducible"
```

---

### Task 6: 建立子模块、直接 AAR 与传递 AAR 端到端矩阵

**Files:**
- Modify: `auto-service-plugin/src/test/resources/fixtures/agp8/settings.gradle`
- Modify: `auto-service-plugin/src/test/resources/fixtures/agp8/app/build.gradle`
- Modify: `auto-service-plugin/src/test/resources/fixtures/agp8/app/src/main/java/test/sample/ServiceImpl.java`
- Modify: `auto-service-plugin/src/test/resources/fixtures/agp8/feature/build.gradle`
- Modify: `auto-service-plugin/src/test/resources/fixtures/agp8/feature/src/main/AndroidManifest.xml`
- Modify: `auto-service-plugin/src/test/resources/fixtures/agp8/feature/src/main/java/test/feature/FeatureTask.java`
- Create: `auto-service-plugin/src/test/resources/fixtures/agp8/java-services/build.gradle`
- Create: `auto-service-plugin/src/test/resources/fixtures/agp8/java-services/src/main/java/test/java/JavaModuleTask.java`
- Create: `auto-service-plugin/src/test/resources/fixtures/agp8/external-producer/build.gradle`
- Create: `auto-service-plugin/src/test/resources/fixtures/agp8/external-producer/src/main/AndroidManifest.xml`
- Create: `auto-service-plugin/src/test/resources/fixtures/agp8/external-producer/src/main/java/test/external/ExternalTask.java`
- Create: `auto-service-plugin/src/test/resources/fixtures/agp8/external-bridge/build.gradle`
- Create: `auto-service-plugin/src/test/resources/fixtures/agp8/external-bridge/src/main/AndroidManifest.xml`
- Create: `auto-service-plugin/src/test/resources/fixtures/agp8/external-bridge/src/main/java/test/external/BridgeTask.java`
- Modify: `auto-service-plugin/src/test/groovy/com/anymore/auto/gradle/AutoServiceAgp8FunctionalTest.groovy`

**Interfaces:**
- Consumes: full-scope transform and TestKit fixture-local Maven repository.
- Produces: evidence that app, project library, direct external AAR and transitive external AAR services are all registered and ordered.

- [ ] **Step 1: 写五来源运行时失败测试**

Define aliases/priorities:

```text
app ServiceImpl: priority 0, alias "app"
feature FeatureTask: priority 10, alias "feature"
java-services JavaModuleTask: priority 20, alias "java"
external-producer ExternalTask: priority 30, alias "external"
external-bridge BridgeTask: priority 40, alias "bridge"
```

The app depends on `project(':feature')`, `project(':java-services')` and Maven coordinate `test.external:external-bridge:1.0`; bridge has an API dependency on `test.external:external-producer:1.0` in its published POM.

Configure the Java module as:

```groovy
plugins { id 'java-library' }
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
dependencies { compileOnly files('../libs/auto-service-annotation.jar') }
```

Add all five fixture modules to `settings.gradle`.

Add a functional test that first publishes producer and bridge into fixture `test-repo`, then runs `:app:verifyAllScopeRuntime`. Assert the loaded class names are exactly the five names in priority order and each alias returns one matching implementation.

- [ ] **Step 2: 配置真实 AAR 发布夹具**

Both external modules apply `com.android.library` 8.13.0 and `maven-publish`. Configure release publication after evaluation:

```groovy
publishing {
    publications {
        release(MavenPublication) {
            groupId = 'test.external'
            artifactId = project.name
            version = '1.0'
            afterEvaluate { from components.release }
        }
    }
    repositories {
        maven { url = rootProject.layout.projectDirectory.dir('test-repo') }
    }
}
```

Give every library a minimal manifest and compile-only annotation jar. Set both external projects to `group = 'test.external'` and `version = '1.0'`. In the bridge source build use:

```groovy
dependencies {
    api project(':external-producer')
    compileOnly files('../libs/auto-service-annotation.jar')
}
```

The generated bridge POM must therefore contain `test.external:external-producer:1.0`. The bridge also contains `BridgeTask`, ensuring direct bridge and transitive producer are both observed.

- [ ] **Step 3: 添加 Debug 完整诊断断言**

In `verifyAllScopeRuntime`, call `ServiceLoader.diagnose(Runnable.class, '')` reflectively and assert:

```groovy
assert report.availability.toString() == 'AVAILABLE'
assert report.registeredCount == 5
assert report.availableAliases == ['app', 'feature', 'java', 'external', 'bridge'] as Set
```

Add an excluded external implementation and assert Debug entries include it with `EXCLUDED` and the configured rule.

- [ ] **Step 4: 添加 Release 轻量诊断断言**

Load Release transform output and assert `diagnose()` returns `UNAVAILABLE_IN_NON_DEBUG_BUILD` with empty entries while `ServiceLoader.load()` still returns all registered implementations.

Inspect only `ServiceRegistryDiagnostics.class` bytes/constant pool and assert they do not contain candidate class names, aliases or exclusion regex. Do not search the whole output jar because `ServiceRegistry` legitimately references implementations.

- [ ] **Step 5: 添加普通重复类冲突测试**

Create two fixture jars containing the same `test/duplicate/Duplicate.class`, add both as app implementation dependencies, run `assembleDebug` with `buildAndFail()`, and assert output contains the class name plus both jar names.

- [ ] **Step 6: 运行完整功能矩阵**

```bash
rtk ./gradlew :auto-service-plugin:test --tests '*AutoServiceAgp8FunctionalTest'
```

Expected: PASS；TestKit 构建证明直接和传递 AAR 均被发现，Debug/Release 诊断分离正确。

- [ ] **Step 7: 检查影响并提交端到端覆盖**

Stage Task 6 files, run staged `detect_changes`, then:

```bash
rtk git diff --cached --check
rtk git commit -m "test: cover project and external AAR service discovery"
```

---

### Task 7: 完成凭据、发布与版本一致性治理

**Files:**
- Modify: `gradle.properties`
- Modify: `build.gradle`
- Modify: `maven_publish.gradle`
- Modify: `auto-service-annotation/build.gradle`
- Modify: `auto-service-loader/build.gradle`
- Modify: `auto-service-registry/build.gradle`
- Modify: `auto-service-plugin/build.gradle`
- Create: `auto-service-plugin/src/test/groovy/com/anymore/auto/gradle/PublicationConfigurationTest.groovy`

**Interfaces:**
- Consumes: environment variables or user Gradle properties `ALIYUN_USERNAME` and `ALIYUN_PASSWORD` only for publish tasks.
- Produces: credential-free normal builds and aligned v0.0.13 POM dependencies.

- [ ] **Step 1: 写无凭据普通构建与发布失败测试**

Add a TestKit/build-script test that clears the two credential environment variables, then asserts:

```text
./gradlew projects            -> succeeds
./gradlew :app:assembleDebug  -> succeeds
./gradlew :auto-service-loader:publish -> fails before network with a Chinese missing-credentials message
```

Use a temporary Gradle user home without credential properties so the test cannot pass because of developer-local state.

- [ ] **Step 2: 验证当前配置不满足测试**

Run:

```bash
rtk ./gradlew :auto-service-plugin:test --tests '*PublicationConfigurationTest'
```

Expected: FAIL because credentials currently live in repository properties and publishing prints the username.

- [ ] **Step 3: 移除仓库凭据并条件化私服解析**

Delete `ALIYUN_USERNAME` and `ALIYUN_PASSWORD` values from repository `gradle.properties`.

Resolve credentials in root build with environment first, user Gradle property second:

```groovy
ext.maven_username = providers.environmentVariable('ALIYUN_USERNAME')
        .orElse(providers.gradleProperty('ALIYUN_USERNAME')).orNull
ext.maven_password = providers.environmentVariable('ALIYUN_PASSWORD')
        .orElse(providers.gradleProperty('ALIYUN_PASSWORD')).orNull
ext.has_private_maven_credentials = maven_username && maven_password
```

Only add private resolution repositories when `has_private_maven_credentials` is true. Google and Maven Central remain unconditional.

- [ ] **Step 4: 让发布任务执行前明确校验凭据**

Remove `println("Maven Username: ...")`. Configure publication credentials from the resolved values and add to each `PublishToMavenRepository` task:

```groovy
doFirst {
    if (!maven_username || !maven_password) {
        throw new GradleException('发布到私有 Maven 仓库需要 ALIYUN_USERNAME 和 ALIYUN_PASSWORD')
    }
}
```

No logs may print either credential value.

- [ ] **Step 5: 对齐项目依赖与发布 POM**

Use project dependencies for all same-repository modules. Verify generated POMs contain version `0.0.13` coordinates:

```text
auto-service-loader -> auto-service-annotation + auto-service-registry
auto-service-register -> no runtime dependency on annotation after metadata-name refactor
```

Set `VERSION=0.0.13`. Keep artifact IDs from each module `gradle.properties`.

- [ ] **Step 6: 轮换凭据作为人工发布门禁**

Before any real publish, the repository owner must invalidate the previously exposed credential and provision a new value outside Git. Record only the completion state in release notes; never write the replacement value to workspace files or logs.

- [ ] **Step 7: 运行无凭据构建、测试和 POM 验证**

```bash
rtk ./gradlew projects
rtk ./gradlew :auto-service-annotation:test :auto-service-registry:test :auto-service-loader:test :auto-service-plugin:test :app:assembleDebug :app:assembleRelease
rtk ./gradlew :auto-service-loader:generatePomFileForMavenPublication :auto-service-plugin:generatePomFileForMavenPublication
```

Expected: no-credential normal build PASS；POM 版本和依赖关系与本任务定义一致。

- [ ] **Step 8: 检查影响并提交安全治理**

Stage Task 7 files, run staged `detect_changes`, then:

```bash
rtk git diff --cached --check
rtk git commit -m "build: secure publishing and align module dependencies"
```

---

### Task 8: 更新文档并执行发布级验收

**Files:**
- Modify: `README.md`
- Modify: `CHANGELOG.md`
- Modify: `CLAUDE.md`
- Modify: `openspec/specs/gradle-build-config/spec.md`
- Modify: `openspec/specs/plugin-agp-api/spec.md`
- Create: `docs/troubleshooting.md`
- Create: `docs/advanced-guide.md`

**Interfaces:**
- Consumes: implemented v0.0.13 API、构建行为和测试命令。
- Produces: one coherent consumer guide, migration notes and final release evidence.

- [ ] **Step 1: 运行文档涉及的公共符号上下文检查**

```text
context({repo: "auto-service-android", name: "ServiceLoader"})
context({repo: "auto-service-android", name: "AutoServiceExtension"})
```

Use returned signatures to prevent documentation drift; this step is read-only and does not replace impact checks for later code fixes.

- [ ] **Step 2: 更新 README 快速开始与行为契约**

Document exact support matrix and discovery scope:

```text
AGP 8.13.0 / Gradle 8.13 / JDK 17
Application plugin scans application, project modules, direct dependencies and transitive AAR/JAR classes.
Library modules declare @AutoService implementations but do not apply the plugin.
```

Add `ServiceLoader.diagnose()` examples and clearly state full diagnostics are only available in `debuggable=true` variants.

- [ ] **Step 3: 写排查手册和进阶指南**

`docs/troubleshooting.md` must provide exact decision paths for:

- load returns empty;
- requested alias has no match;
- implementation was excluded;
- duplicate class failure;
- plugin/loader version mismatch;
- diagnostics unavailable in Release;
- publish credentials missing.

`docs/advanced-guide.md` covers priority ordering、multi-interface singleton、require、regex exclusions、all-scope dependency discovery and build-cache behavior with runnable Groovy DSL examples.

- [ ] **Step 4: 更新变更日志、开发说明和 OpenSpec**

Add v0.0.13 release notes listing additive API, full-scope behavior, minimum toolchain, aligned component upgrade requirement and security migration. Remove statements that still describe AGP 7.4 compatibility, reflection-based `toTransform`, or a generated source location no longer used by the task.

Do not change `@AutoService` retention documentation to BINARY; explicitly state it remains RUNTIME in v0.0.13.

- [ ] **Step 5: 执行完整发布级验证**

Run under JDK 17:

```bash
rtk ./gradlew clean
rtk ./gradlew :auto-service-annotation:test :auto-service-registry:test :auto-service-loader:test :auto-service-plugin:test
rtk ./gradlew :app:assembleDebug :app:assembleRelease
rtk ./gradlew :auto-service-loader:generatePomFileForMavenPublication :auto-service-plugin:generatePomFileForMavenPublication
rtk git diff --check
```

Expected: all commands PASS; Debug and Release functional matrix, cache tests and dependency POM tests are included in plugin test suite.

- [ ] **Step 6: 执行最终 GitNexus 回归检查**

Before staging docs, inspect implementation relative to master:

```text
detect_changes({repo: "auto-service-android", scope: "compare", base_ref: "master"})
```

Review every changed symbol and affected flow. For any unexpected symbol, inspect with `context()` and fix or document it before release.

- [ ] **Step 7: 提交文档与发布说明**

Stage only Task 8 files, run staged `detect_changes`, then:

```bash
rtk git diff --cached --check
rtk git commit -m "docs: document v0.0.13 discovery and diagnostics"
```

- [ ] **Step 8: 最终工作区与提交审计**

```bash
rtk git status --short --branch
rtk git log --oneline --decorate -10
```

Expected: implementation commits are present; only the user pre-existing `.idea/misc.xml` and old untracked Phase 1 design remain outside the implementation commits。
