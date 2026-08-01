# AGP 8 完整适配实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 auto-service Gradle 插件迁移到 AGP 8.13 的公开 Variant / Scoped Artifacts API，使其在不触碰 AGP 内部目录和任务的情况下向每个变体注入 `ServiceRegistry` 字节码。

**Architecture:** 插件在 `ApplicationAndroidComponentsExtension.onVariants` 中为每个应用变体注册一个惰性任务。任务以 `ScopedArtifact.CLASSES` 的 PROJECT 范围输入读取本项目已编译的 Kotlin / Java 类，生成并编译 `ServiceRegistry`，再通过 `toAppend` 将唯一的输出 JAR 追加回 AGP 类产物管线。AGP 自行负责 Lint、Dex、打包和任务依赖。

**Tech Stack:** Gradle 8.13、AGP 8.13 Variant API、Groovy、Javassist、JavaPoet、Java Compiler API、Gradle TestKit。

## Global Constraints

- 基线固定为 Gradle 8.13、AGP 8.13.0、Kotlin 2.1.0、JDK 17。
- 仅使用 AGP 公开 API：`ApplicationAndroidComponentsExtension`、`onVariants`、`ScopedArtifacts`、`ScopedArtifact.CLASSES`。
- 不使用 `afterEvaluate`、`AppExtension`、`AppPlugin`、`applicationVariants`、AGP 任务名或 `intermediates/javac` 路径。
- 保持 `autoService` DSL 和已生成的服务映射语义不变。
- 不修改用户已有未提交文件：`.idea/misc.xml`、`CLAUDE.md`、`settings.gradle`、`.claude/skills/`、`AGENTS.md`。
- 不发布到远程 Maven 仓库；发布新插件坐标需另行授权。

---

### Task 1: 建立本地插件的 AGP 8 功能测试基线

**Files:**
- Modify: `auto-service-plugin/build.gradle`
- Create: `auto-service-plugin/src/test/groovy/com/anymore/auto/gradle/AutoServiceAgp8FunctionalTest.groovy`
- Create: `auto-service-plugin/src/test/resources/fixtures/agp8/settings.gradle`
- Create: `auto-service-plugin/src/test/resources/fixtures/agp8/build.gradle`
- Create: `auto-service-plugin/src/test/resources/fixtures/agp8/app/build.gradle`
- Create: `auto-service-plugin/src/test/resources/fixtures/agp8/app/src/main/AndroidManifest.xml`
- Create: `auto-service-plugin/src/test/resources/fixtures/agp8/app/src/main/java/test/sample/ServiceImpl.java`

**Interfaces:**
- Consumes: Gradle TestKit 的 `GradleRunner` 与当前插件的 `auto-service` 插件描述符。
- Produces: `AutoServiceAgp8FunctionalTest#releaseBuildProducesRegistry()`，可复现当前 Release 隐式依赖故障。

- [ ] **Step 1: 写出失败的功能测试**

```groovy
def 'release build produces and packages ServiceRegistry'() {
    copyFixture('agp8')

    def result = GradleRunner.create()
        .withProjectDir(testProjectDir)
        .withPluginClasspath()
        .withArguments(':app:assembleRelease', '--stacktrace', '--console=plain')
        .build()

    assert result.output.contains('BUILD SUCCESSFUL')
    assert new File(testProjectDir, 'app/build/outputs/apk/release/app-release-unsigned.apk').isFile()
    assert registryClassIsPresent(testProjectDir, 'release')
}
```

- [ ] **Step 2: 运行测试并确认其因现有 Release 隐式依赖校验失败**

Run: `./gradlew :auto-service-plugin:test --tests '*AutoServiceAgp8FunctionalTest.releaseBuildProducesRegistry' --console=plain`

Expected: FAIL；输出含 `generateReleaseLintVitalReportModel` 和 `implicit dependency`，不是 fixture 或 SDK 缺失。

- [ ] **Step 3: 最小化测试基础设施**

```groovy
dependencies {
    testImplementation gradleTestKit()
    testImplementation localGroovy()
    testImplementation 'junit:junit:4.13.2'
}

test {
    useJUnit()
}
```

fixture 的 `app/build.gradle` 必须应用 `com.android.application`、`org.jetbrains.kotlin.android` 与被测的 `auto-service` 插件，并包含一个带 `@AutoService` 的实现类；不依赖远程 `com.anymore:auto-service-register`。

- [ ] **Step 4: 重跑失败测试，确认失败原因保持为产品缺陷**

Run: `./gradlew :auto-service-plugin:test --tests '*AutoServiceAgp8FunctionalTest.releaseBuildProducesRegistry' --console=plain`

Expected: FAIL；唯一阻断仍是现有插件对 AGP 内部 Java classes 目录的隐式依赖。

- [ ] **Step 5: 提交测试基线**

```bash
git add auto-service-plugin/build.gradle auto-service-plugin/src/test
git commit -m "test: reproduce AGP 8 release registry failure"
```

### Task 2: 将注册任务重构为声明式的 Scoped Artifact 代码生成任务

**Files:**
- Modify: `auto-service-plugin/src/main/groovy/com/anymore/auto/gradle/AutoServiceRegisterTask.groovy`
- Modify: `auto-service-plugin/src/main/groovy/com/anymore/auto/gradle/AutoServiceRegisterAction.groovy`
- Test: `auto-service-plugin/src/test/groovy/com/anymore/auto/gradle/AutoServiceRegisterTaskTest.groovy`

**Interfaces:**
- Consumes: `ListProperty<RegularFile> inputJars`、`ListProperty<Directory> inputDirectories`、`ConfigurableFileCollection compileClasspath`、`Property<String> sourceCompatibility`、`DirectoryProperty workDirectory`、`RegularFileProperty outputJar`。
- Produces: `AutoServiceRegisterTask#outputJar`，其中含生成的 `com/anymore/auto/ServiceRegistry*.class`，可由 `ScopedArtifact.CLASSES` 追加。

- [ ] **Step 1: 写出失败的任务单元测试**

```groovy
def 'task compiles the generated registry into its declared output jar'() {
    def task = project.tasks.create('register', AutoServiceRegisterTask)
    task.inputDirectories.add(compiledFixtureClasses)
    task.compileClasspath.from(compiledFixtureClasses, loaderJar)
    task.workDirectory.set(project.layout.buildDirectory.dir('test-work'))
    task.outputJar.set(project.layout.buildDirectory.file('test-output/registry.jar'))

    task.run()

    assert zipEntries(task.outputJar.get().asFile).any { it.name == 'com/anymore/auto/ServiceRegistry.class' }
}
```

- [ ] **Step 2: 运行单元测试并确认失败**

Run: `./gradlew :auto-service-plugin:test --tests '*AutoServiceRegisterTaskTest.task compiles the generated registry into its declared output jar' --console=plain`

Expected: FAIL；现有任务没有公开输入、工作目录或输出 JAR 属性。

- [ ] **Step 3: 实现最小声明式任务模型**

```groovy
@InputFiles @PathSensitive(PathSensitivity.RELATIVE)
abstract ListProperty<RegularFile> getInputJars()

@InputFiles @PathSensitive(PathSensitivity.RELATIVE)
abstract ListProperty<Directory> getInputDirectories()

@Classpath
abstract ConfigurableFileCollection getCompileClasspath()

@OutputDirectory
abstract DirectoryProperty getWorkDirectory()

@OutputFile
abstract RegularFileProperty getOutputJar()
```

任务执行时将输入目录与 JAR 交给 `AutoServiceRegisterAction` 扫描；把 JavaPoet 生成的源码写入 `workDirectory/src`；使用 `ToolProvider.systemJavaCompiler` 和 `compileClasspath + inputJars + inputDirectories` 编译到 `workDirectory/classes`；最后仅将生成的 `.class` 文件写入 `outputJar`。Java 编译失败必须抛出带诊断信息的 `GradleException`。

- [ ] **Step 4: 运行任务单元测试并确认通过**

Run: `./gradlew :auto-service-plugin:test --tests '*AutoServiceRegisterTaskTest.task compiles the generated registry into its declared output jar' --console=plain`

Expected: PASS；输出 JAR 只包含生成的注册表类及其匿名内部类。

- [ ] **Step 5: 提交声明式任务改造**

```bash
git add auto-service-plugin/src/main/groovy auto-service-plugin/src/test/groovy
git commit -m "feat: make registry generation a scoped artifact task"
```

### Task 3: 用 Android Components API 接入每个应用变体

**Files:**
- Modify: `auto-service-plugin/src/main/groovy/com/anymore/auto/gradle/AutoServiceRegisterPlugin.groovy`
- Test: `auto-service-plugin/src/test/groovy/com/anymore/auto/gradle/AutoServiceAgp8FunctionalTest.groovy`

**Interfaces:**
- Consumes: `ApplicationAndroidComponentsExtension`、`ApplicationVariant`、`ScopedArtifacts.Scope.PROJECT`、`ScopedArtifact.CLASSES`。
- Produces: 对每个变体注册的 `androidAutoServiceRegisterTask<Variant>`；AGP 自动将其输出作为 class artifact 追加到 Dex / 打包链。

- [ ] **Step 1: 扩展失败测试以断言 Debug 与 Release 均通过**

```groovy
['Debug', 'Release'].each { variant ->
    def result = runner(':app:assemble' + variant)
    assert result.output.contains('BUILD SUCCESSFUL')
    assert registryClassIsPresent(testProjectDir, variant.toLowerCase(Locale.ROOT))
}
```

- [ ] **Step 2: 运行测试并确认 Release 仍失败**

Run: `./gradlew :auto-service-plugin:test --tests '*AutoServiceAgp8FunctionalTest' --console=plain`

Expected: FAIL；Release 出现旧链路的隐式依赖错误。

- [ ] **Step 3: 实现 Variant API 任务接线**

```groovy
project.pluginManager.withPlugin('com.android.application') {
    def components = project.extensions.getByType(ApplicationAndroidComponentsExtension)
    components.onVariants { variant ->
        def task = project.tasks.register(
            variant.computeTaskName('androidAutoServiceRegister', ''),
            AutoServiceRegisterTask
        ) { taskConfig ->
            taskConfig.compileClasspath.from(variant.compileClasspath)
            taskConfig.sourceCompatibility.set(autoServiceExtension.sourceCompatibility)
        }

        variant.artifacts.forScope(ScopedArtifacts.Scope.PROJECT)
            .use(task)
            .toTransform(
                ScopedArtifact.CLASSES,
                AutoServiceRegisterTask.&getInputJars,
                AutoServiceRegisterTask.&getInputDirectories,
                AutoServiceRegisterTask.&getOutputJar
            )
    }
}
```

实现时将 `toTransform` 改为由 Task 实际行为匹配的公开操作：若输出 JAR 复制所有输入 class，则使用 `toTransform`；若只输出新增注册表 class，则使用 `toAppend(ScopedArtifact.CLASSES, AutoServiceRegisterTask.&getOutputJar)`，并通过独立的、公开 artifact Provider 将 project class dirs/JAR 作为输入。不得使用 AGP 内部任务或目录。

- [ ] **Step 4: 运行功能测试并确认 Debug / Release 均通过**

Run: `./gradlew :auto-service-plugin:test --tests '*AutoServiceAgp8FunctionalTest' --console=plain`

Expected: PASS；没有 `implicit dependency`、`generateReleaseLintVitalReportModel`、`afterEvaluate` 或按任务名查找的输出。

- [ ] **Step 5: 提交 Variant API 迁移**

```bash
git add auto-service-plugin/src/main/groovy/com/anymore/auto/gradle/AutoServiceRegisterPlugin.groovy auto-service-plugin/src/test
git commit -m "feat: wire registry generation with AGP variant artifacts"
```

### Task 4: 在仓库真实示例中验证本地插件实现

**Files:**
- Modify temporarily, then restore: `buildSrc/build.gradle.bak` ↔ `buildSrc/build.gradle`
- Modify temporarily, then restore: `build.gradle`
- Modify temporarily, then restore: `app/build.gradle`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/com/anymore/auto_service_android/demo/ServiceRegistryIntegrationTest.kt`

**Interfaces:**
- Consumes: buildSrc 模式下的 `AutoServiceRegisterPlugin` 与 app 的 `Impl1`、`Impl2`。
- Produces: 真实应用的 Debug、Release APK；注册表包含 `Callable`、`Runnable` 和 `lym23` 映射。

- [ ] **Step 1: 写出失败的运行时注册表测试**

```kotlin
@Test
fun generatedRegistryLoadsDefaultAndAliasedServices() {
    assertEquals(2, ServiceLoader.load(Runnable::class.java).size)
    assertEquals(1, ServiceLoader.load(Runnable::class.java, "lym23").size)
    assertEquals(1, ServiceLoader.load(Callable::class.java).size)
}
```

- [ ] **Step 2: 在 buildSrc 本地插件模式运行测试，确认迁移前 Release 构建失败**

Run: `./gradlew :app:testDebugUnitTest :app:assembleRelease --console=plain`

Expected: Release FAIL，错误为 `generateReleaseLintVitalReportModel` 的隐式依赖；该结果证明真实 app 同样覆盖功能测试场景。

- [ ] **Step 3: 接入本地插件并修复 Manifest**

按照 `CLAUDE.md` 的 buildSrc 模式仅用于本地验证：使 buildSrc 使用当前 AGP/Kotlin/annotation 版本，临时从 app 直接应用 `AutoServiceRegisterPlugin`，避免解析远程旧版 `0.0.11`。从 `AndroidManifest.xml` 删除 `package` 属性；`app/build.gradle` 的 `namespace` 保持为唯一来源。

- [ ] **Step 4: 运行真实 app 的完整验证**

Run: `./gradlew :app:clean :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease --configuration-cache --console=plain`

Expected: PASS；Debug 和 Release APK 均存在；`ServiceRegistry` 只通过 AGP Scoped Artifacts 进入 dex；没有 Manifest namespace、隐式依赖或 deprecated Variant API 警告。

- [ ] **Step 5: 恢复模块模式并提交可发布源码及测试**

恢复远程插件消费配置，不发布远程仓库；将源代码、测试与 Manifest 修改提交。若要让模块模式直接消费新实现，下一步必须发布递增版本的 `com.anymore:auto-service-register`，该操作不在本次授权内。

```bash
git add auto-service-plugin app/src/main/AndroidManifest.xml app/src/test
git commit -m "fix: complete AGP 8 registry integration"
```

### Task 5: 文档、变更审计与最终回归

**Files:**
- Modify: `CHANGELOG.md`
- Modify: `CLAUDE.md`
- Modify: `openspec/specs/gradle-build-config/spec.md`
- Modify: `openspec/specs/plugin-agp-api/spec.md`

**Interfaces:**
- Consumes: Tasks 1–4 的构建结果与最终插件 API。
- Produces: 与实际实现一致的 AGP 8 支持声明及可复现验证命令。

- [ ] **Step 1: 写出文档验收失败检查**

```bash
rg -n 'applicationVariants|afterEvaluate|intermediates/javac' \
  auto-service-plugin/src/main/groovy/com/anymore/auto/gradle/AutoServiceRegisterPlugin.groovy
```

Expected: 在迁移完成后无匹配；若仍有匹配则文档不能声明完整适配。

- [ ] **Step 2: 更新最小文档内容**

在 CHANGELOG 和规范中记录：AGP 8.13 使用 Android Components / Scoped Artifacts；Debug、Release 和 Gradle 任务依赖验证均已覆盖；明确远程发布不是本次变更的一部分。

- [ ] **Step 3: 运行最终回归**

Run: `./gradlew :auto-service-plugin:test :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease --configuration-cache --warning-mode all --console=plain`

Expected: PASS；无本插件引入的弃用或隐式依赖警告。

- [ ] **Step 4: 审计变更影响并提交**

Run: `git diff --check && git status --short`

随后运行 GitNexus `detect_changes(scope: "all")`，确认只影响插件、测试、Manifest 和 AGP 文档；不纳入用户已有未提交文件。

```bash
git add CHANGELOG.md CLAUDE.md openspec/specs auto-service-plugin app/src
git commit -m "docs: document complete AGP 8 support"
```
