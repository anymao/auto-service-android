package com.anymore.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
                ServiceDiagnosticEntry(
                    "sample.MainTask",
                    -10,
                    "",
                    true,
                    ServiceDiagnosticStatus.REGISTERED,
                    null
                ),
                ServiceDiagnosticEntry(
                    "sample.DebugTask",
                    0,
                    "debug",
                    false,
                    ServiceDiagnosticStatus.REGISTERED,
                    null
                ),
                ServiceDiagnosticEntry(
                    "sample.LegacyTask",
                    5,
                    "debug",
                    false,
                    ServiceDiagnosticStatus.EXCLUDED,
                    "className /.*Legacy.*/"
                )
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

        assertEquals(
            ServiceDiagnosticAvailability.UNAVAILABLE_PLUGIN_NOT_APPLIED,
            report.availability
        )
        assertTrue(report.entries.isEmpty())
    }

    @Test
    fun `未生成服务注册表时提示应用插件`() {
        val exception = assertThrows(IllegalStateException::class.java) {
            ServiceRegistry.get(Runnable::class.java, "")
        }

        assertTrue(exception.message.orEmpty().contains("Application 模块"))
        assertTrue(exception.message.orEmpty().contains("auto-service"))
    }
}
