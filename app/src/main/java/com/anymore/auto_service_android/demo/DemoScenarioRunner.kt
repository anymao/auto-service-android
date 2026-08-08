package com.anymore.auto_service_android.demo

import com.anymore.auto.ServiceDiagnosticAvailability
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DemoScenarioRunner(
    private val source: DemoServiceSource,
    private val diagnosticsExpected: Boolean
) {

    fun runAll(): List<DemoScenarioResult> = listOf(
        runnableLoad(),
        callableLoad(),
        aliasLoad(),
        priority(),
        lifecycle(),
        concurrentLoad(),
        diagnostics()
    )

    private fun runnableLoad(): DemoScenarioResult = scenario("Runnable 基础加载") {
        val runnables = source.runnables().toList()
        check(runnables.isNotEmpty()) { "未加载到 Runnable 服务" }
        runnables.forEach(Runnable::run)
        "已执行 ${runnables.size} 个 Runnable 服务" to "默认别名加载完成"
    }

    private fun callableLoad(): DemoScenarioResult = scenario("Callable 多接口加载") {
        val callables = source.callables().toList()
        check(callables.isNotEmpty()) { "未加载到 Callable 服务" }
        val values = callables.map { it.call() }
        "已执行 ${callables.size} 个 Callable 服务" to "返回值：$values"
    }

    private fun aliasLoad(): DemoScenarioResult = scenario("Alias 精确加载") {
        val alias = "lym23"
        val runnables = source.runnables(alias).toList()
        check(runnables.isNotEmpty()) { "Alias $alias 未加载到服务" }
        runnables.forEach(Runnable::run)
        "Alias $alias 加载成功" to "别名 $alias 匹配 ${runnables.size} 个 Runnable 服务"
    }

    private fun priority(): DemoScenarioResult = scenario("优先级加载") {
        val first = requireNotNull(source.firstRunnable()) { "未找到最高优先级 Runnable 服务" }
        val last = requireNotNull(source.lastRunnable()) { "未找到最低优先级 Runnable 服务" }
        "优先级服务加载成功" to "first=${first.javaClass.name}, last=${last.javaClass.name}"
    }

    private fun lifecycle(): DemoScenarioResult = scenario("单例生命周期") {
        val first = requireNotNull(source.firstRunnable()) { "未找到 Runnable 服务" }
        val reloaded = requireNotNull(source.firstRunnable()) { "重新加载时未找到 Runnable 服务" }
        check(first === reloaded) { "Runnable 服务未复用同一实例" }
        "单例服务复用成功" to "两次加载返回同一 Runnable 实例"
    }

    private fun concurrentLoad(): DemoScenarioResult = scenario("并发加载") {
        val loadCount = 4
        val completed = CountDownLatch(loadCount)
        val executor = Executors.newFixedThreadPool(loadCount)
        try {
            val futures = (1..loadCount).map {
                executor.submit<Boolean> {
                    try {
                        source.runnables().any()
                    } finally {
                        completed.countDown()
                    }
                }
            }
            check(completed.await(2, TimeUnit.SECONDS)) { "并发加载未在限定时间内完成" }
            check(futures.all { it.get() }) { "至少一次并发加载未发现 Runnable 服务" }
            "$loadCount 次并发加载完成" to "所有并发任务均加载到 Runnable 服务"
        } finally {
            executor.shutdownNow()
        }
    }

    private fun diagnostics(): DemoScenarioResult = scenario("服务诊断") {
        val report = source.runnableDiagnostics()
        val expectedAvailability = if (diagnosticsExpected) {
            ServiceDiagnosticAvailability.AVAILABLE
        } else {
            ServiceDiagnosticAvailability.UNAVAILABLE_IN_NON_DEBUG_BUILD
        }
        check(report.availability == expectedAvailability) {
            "诊断状态为 ${report.availability}，预期为 $expectedAvailability"
        }
        val summary = if (diagnosticsExpected) "服务诊断可用" else "发行构建诊断不可用符合预期"
        summary to "状态：${report.availability}\n$report"
    }

    private fun scenario(
        title: String,
        action: () -> Pair<String, String>
    ): DemoScenarioResult = try {
        val (summary, details) = action()
        DemoScenarioResult(title, true, summary, details)
    } catch (error: InterruptedException) {
        Thread.currentThread().interrupt()
        throw error
    } catch (error: Exception) {
        DemoScenarioResult(title, false, "执行失败", error.message ?: error.javaClass.name)
    }
}
