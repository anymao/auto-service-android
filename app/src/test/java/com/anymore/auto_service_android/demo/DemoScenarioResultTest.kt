package com.anymore.auto_service_android.demo

import org.junit.Assert.assertEquals
import org.junit.Test

class DemoScenarioResultTest {

    @Test
    fun summaryCountsPassingScenarios() {
        val summary = DemoScenarioResult.summarize(
            listOf(
                DemoScenarioResult("成功", true, "通过", ""),
                DemoScenarioResult("失败", false, "失败", "原因")
            )
        )

        assertEquals("1/2 个场景通过", summary)
    }
}
