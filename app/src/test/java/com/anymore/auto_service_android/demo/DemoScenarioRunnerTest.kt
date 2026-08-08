package com.anymore.auto_service_android.demo

import com.anymore.auto.ServiceDiagnosticAvailability
import com.anymore.auto.ServiceDiagnosticEntry
import com.anymore.auto.ServiceDiagnosticReport
import com.anymore.auto.ServiceDiagnosticStatus
import java.util.concurrent.Callable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoScenarioRunnerTest {

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

    private class FakeDemoServiceSource(
        private val failRunnableLoad: Boolean = false,
        private val nonDebugDiagnostic: Boolean = false
    ) : DemoServiceSource {

        private val runnable = Runnable {}

        override fun runnables(alias: String): Iterable<Runnable> {
            if (failRunnableLoad && alias.isEmpty()) {
                error("Runnable 加载失败")
            }
            return if (alias == "lym23") listOf(Runnable {}) else listOf(runnable)
        }

        override fun callables(): Iterable<Callable<*>> = listOf(Callable { 1 })

        override fun firstRunnable(): Runnable? = runnable

        override fun lastRunnable(): Runnable? = runnable

        override fun runnableDiagnostics(): ServiceDiagnosticReport = ServiceDiagnosticReport(
            serviceClassName = Runnable::class.java.name,
            requestedAlias = "",
            availability = if (nonDebugDiagnostic) {
                ServiceDiagnosticAvailability.UNAVAILABLE_IN_NON_DEBUG_BUILD
            } else {
                ServiceDiagnosticAvailability.AVAILABLE
            },
            entries = listOf(
                ServiceDiagnosticEntry(
                    implementationClassName = "demo.FakeRunnable",
                    priority = 0,
                    alias = "",
                    singleton = true,
                    status = ServiceDiagnosticStatus.REGISTERED,
                    exclusionRule = null
                )
            )
        )
    }
}
