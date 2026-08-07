package com.anymore.auto

import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceLoaderDiagnosticTest {

    @Test
    fun `泛型诊断入口委托给诊断注册表`() {
        val report = ServiceLoader.diagnose<Runnable>("debug")

        assertEquals(Runnable::class.java.name, report.serviceClassName)
        assertEquals("debug", report.requestedAlias)
        assertEquals(
            ServiceDiagnosticAvailability.UNAVAILABLE_PLUGIN_NOT_APPLIED,
            report.availability
        )
    }
}
