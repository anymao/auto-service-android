# Demo 场景验收面板 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** 将 app 改为无需查看 Logcat 即可验证 auto-service 运行时能力的单页场景面板，并为场景计算增加可重复的 JVM 测试。

**Architecture:** 新增纯 Kotlin 的 DemoScenarioRunner，通过 DemoServiceSource 读取服务、优先级和诊断数据并输出不可变场景结果。生产来源唯一负责调用 ServiceLoader；MainActivity 只负责后台触发与 XML 渲染。

**Tech Stack:** Kotlin、Android View/XML、JUnit 4、现有 ServiceLoader 与 ServiceDiagnosticReport API。

## Global Constraints

- 保持 AGP 8.13、Gradle 8.13、JDK 17；不引入 Compose、协程或第三方依赖。
- app 页面仅验收运行时 API；AAR、DSL、缓存、重复类仍由 Plugin TestKit 覆盖。
- 所有新增文案和注释用中文；修改既有方法或类前先运行 GitNexus impact。
- 新增生产行为必须测试先行，并验证失败后再实现。

---

## 文件结构

| 文件 | 责任 |
| --- | --- |
| app/src/main/java/com/anymore/auto_service_android/demo/DemoScenarioResult.kt | 场景结果与总状态计算。 |
| app/src/main/java/com/anymore/auto_service_android/demo/DemoServiceSource.kt | 可替换服务来源；生产实现调用 ServiceLoader。 |
| app/src/main/java/com/anymore/auto_service_android/demo/DemoScenarioRunner.kt | 七个场景、异常隔离、并发检查。 |
| app/src/main/java/com/anymore/auto_service_android/demo/MainActivity.kt | 启动、重新执行与动态渲染。 |
| app/src/main/res/layout/activity_main.xml | 标题、总状态、按钮和结果容器。 |
| app/src/test/java/.../DemoScenarioRunnerTest.kt | 执行器的确定性 JVM 测试。 |
| app/src/androidTest/java/.../DemoScenarioPanelInstrumentedTest.kt | 有设备时的 UI 冒烟测试。 |

## Task 1: 场景执行器

**Files:**
- Create: app/src/main/java/com/anymore/auto_service_android/demo/DemoScenarioResult.kt
- Create: app/src/main/java/com/anymore/auto_service_android/demo/DemoServiceSource.kt
- Create: app/src/main/java/com/anymore/auto_service_android/demo/DemoScenarioRunner.kt
- Test: app/src/test/java/com/anymore/auto_service_android/demo/DemoScenarioRunnerTest.kt

**Interfaces:**
- DemoScenarioResult(title: String, passed: Boolean, summary: String, details: String)。
- DemoServiceSource: runnables(alias: String): Iterable<Runnable>、callables(): Iterable<Callable<*>>、firstRunnable(): Runnable?、lastRunnable(): Runnable?、runnableDiagnostics(): ServiceDiagnosticReport。
- DemoScenarioRunner(source: DemoServiceSource, diagnosticsExpected: Boolean).runAll(): List<DemoScenarioResult>。

- [ ] **Step 1: 写会失败的场景测试**

~~~
@Test
fun resultsContainSevenPassingScenarios() {
    val results = DemoScenarioRunner(FakeDemoServiceSource(), true).runAll()

    assertEquals(7, results.size)
    assertTrue(results.all { it.passed })
    assertTrue(results.single { it.title == "Alias 精确加载" }.details.contains("lym23"))
    assertTrue(results.single { it.title == "服务诊断" }.details.contains("AVAILABLE"))
}

@Test
fun oneSourceFailureDoesNotBlockOtherScenarios() {
    val results = DemoScenarioRunner(FakeDemoServiceSource(failRunnableLoad = true), true).runAll()

    assertFalse(results.single { it.title == "Runnable 基础加载" }.passed)
    assertTrue(results.single { it.title == "Callable 多接口加载" }.passed)
}

@Test
fun unavailableReleaseDiagnosticsIsExpectedSuccess() {
    val results = DemoScenarioRunner(FakeDemoServiceSource(nonDebugDiagnostic = true), false).runAll()

    assertTrue(results.single { it.title == "服务诊断" }.passed)
}
~~~

- [ ] **Step 2: 验证 RED**

Run: ./gradlew :app:testDebugUnitTest --tests '*DemoScenarioRunnerTest'

Expected: 因 DemoScenarioRunner、DemoServiceSource 和 DemoScenarioResult 不存在而编译失败。

- [ ] **Step 3: 写最小实现**

~~~
private fun scenario(title: String, action: () -> Pair<String, String>): DemoScenarioResult =
    try {
        val (summary, details) = action()
        DemoScenarioResult(title, true, summary, details)
    } catch (error: Throwable) {
        DemoScenarioResult(title, false, "执行失败", error.message ?: error.javaClass.name)
    }

fun runAll(): List<DemoScenarioResult> = listOf(
    runnableLoad(), callableLoad(), aliasLoad(), priority(), lifecycle(), concurrentLoad(), diagnostics()
)
~~~

实现 ServiceLoaderDemoServiceSource：runnables() 委托 ServiceLoader.load<Runnable>(alias)，优先级读取 firstPriority/lastPriority，诊断读取 ServiceLoader.diagnose<Runnable>()。并发场景以固定大小 ExecutorService、CountDownLatch 检查多次加载完成，并在 finally 关闭 executor。

- [ ] **Step 4: 验证 GREEN**

Run: ./gradlew :app:testDebugUnitTest --tests '*DemoScenarioRunnerTest'

Expected: 三个测试通过；单一异常只产生一个失败结果。

- [ ] **Step 5: 提交**

~~~
git add app/src/main/java/com/anymore/auto_service_android/demo/DemoScenarioResult.kt \
  app/src/main/java/com/anymore/auto_service_android/demo/DemoServiceSource.kt \
  app/src/main/java/com/anymore/auto_service_android/demo/DemoScenarioRunner.kt \
  app/src/test/java/com/anymore/auto_service_android/demo/DemoScenarioRunnerTest.kt
git commit -m "feat: add demo service scenario runner"
~~~

## Task 2: 单页面板和 Activity

**Files:**
- Modify: app/src/main/res/layout/activity_main.xml
- Modify: app/src/main/res/values/strings.xml
- Modify: app/src/main/res/values/colors.xml
- Modify: app/src/main/java/com/anymore/auto_service_android/demo/MainActivity.kt
- Test: app/src/test/java/com/anymore/auto_service_android/demo/DemoScenarioResultTest.kt

**Interfaces:**
- 产出 layout id: demo_overall_status、demo_run_again、demo_results_container。
- MainActivity 消费 DemoScenarioRunner.runAll() 和 DemoScenarioResult 四个属性。

- [ ] **Step 1: 写会失败的总状态测试**

~~~
@Test
fun summaryCountsPassingScenarios() {
    val summary = DemoScenarioResult.summarize(listOf(
        DemoScenarioResult("成功", true, "通过", ""),
        DemoScenarioResult("失败", false, "失败", "原因")
    ))

    assertEquals("1/2 个场景通过", summary)
}
~~~

- [ ] **Step 2: 验证 RED**

Run: ./gradlew :app:testDebugUnitTest --tests '*DemoScenarioResultTest'

Expected: 因 summarize 不存在而编译失败。

- [ ] **Step 3: 写最小实现**

~~~
companion object {
    fun summarize(results: List<DemoScenarioResult>): String =
        results.count { it.passed }.toString() + "/" + results.size + " 个场景通过"
}
~~~

将 XML 改为 NestedScrollView 内垂直 LinearLayout，包含标题、总状态 TextView、重新执行 Button 与结果容器。MainActivity 启动/点击时禁用按钮并显示“正在执行”；在 kotlin.concurrent.thread 中运行执行器，并用 runOnUiThread 清空并动态添加结果 TextView。通过 ApplicationInfo.FLAG_DEBUGGABLE 判断变体。删除原先十个线程与 Thread.sleep。

- [ ] **Step 4: 验证 GREEN**

Run: ./gradlew :app:testDebugUnitTest --tests '*DemoScenarioResultTest' :app:assembleDebug

Expected: 测试和 Debug APK 构建均成功。

- [ ] **Step 5: 提交**

~~~
git add app/src/main/res/layout/activity_main.xml app/src/main/res/values/strings.xml \
  app/src/main/res/values/colors.xml app/src/main/java/com/anymore/auto_service_android/demo/MainActivity.kt \
  app/src/main/java/com/anymore/auto_service_android/demo/DemoScenarioResult.kt \
  app/src/test/java/com/anymore/auto_service_android/demo/DemoScenarioResultTest.kt
git commit -m "feat: render interactive demo scenario panel"
~~~

## Task 3: 设备冒烟测试与回归

**Files:**
- Delete: app/src/test/java/com/anymore/auto_service_android/demo/ExampleUnitTest.kt
- Delete: app/src/androidTest/java/com/anymore/auto_service_android/demo/ExampleInstrumentedTest.kt
- Create: app/src/androidTest/java/com/anymore/auto_service_android/demo/DemoScenarioPanelInstrumentedTest.kt
- Modify: docs/testing.md

- [ ] **Step 1: 写会失败的设备测试**

~~~
@RunWith(AndroidJUnit4::class)
class DemoScenarioPanelInstrumentedTest {
    @Test
    fun panelShowsScenarioResultsAfterLaunch() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.demo_results_container)).check(matches(isDisplayed()))
            onView(withText("Runnable 基础加载")).check(matches(isDisplayed()))
        }
    }
}
~~~

- [ ] **Step 2: 验证 RED**

Run: ./gradlew :app:connectedDebugAndroidTest --tests '*DemoScenarioPanelInstrumentedTest'

Expected: 旧页面找不到 demo_results_container；没有设备时记录为环境限制，不能视为通过。

- [ ] **Step 3: 迁移测试并更新文档**

删除两个模板测试，加入新 instrumentation 测试。docs/testing.md 改为说明 app JVM 测试覆盖场景执行器，设备测试验证面板可见；保留未连接设备时“未执行”的报告规则。

- [ ] **Step 4: 全量验证**

Run:

~~~
./gradlew \
  :auto-service-annotation:test \
  :auto-service-registry:test \
  :auto-service-loader:test \
  :auto-service-plugin:test \
  :app:testDebugUnitTest \
  :app:assembleDebug \
  :app:assembleRelease
~~~

Expected: 所有 JVM/TestKit 测试通过，Debug/Release APK 成功。设备可用时另运行 connectedDebugAndroidTest。

- [ ] **Step 5: 变更检测与提交**

Run: git diff --check 和 git status --short。再运行 GitNexus detect_changes(scope: "all", base_ref: "master")，确认只影响 demo、测试和文档。

~~~
git add app/src/test app/src/androidTest docs/testing.md
git commit -m "test: verify interactive demo scenarios"
~~~

## 计划自检

- 七个场景、异常隔离、Release 诊断、页面显示和设备验收均有实施任务。
- AAR、DSL、重复类和缓存没有迁入 app，范围符合已确认规格。
- 所有接口、资源 id 和命令在后续任务中名称一致；不存在未决占位或未定义依赖。
