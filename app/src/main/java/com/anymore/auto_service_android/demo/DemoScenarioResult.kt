package com.anymore.auto_service_android.demo

data class DemoScenarioResult(
    val title: String,
    val passed: Boolean,
    val summary: String,
    val details: String
) {
    companion object {
        fun summarize(results: List<DemoScenarioResult>): String =
            results.count { it.passed }.toString() + "/" + results.size + " 个场景通过"
    }
}
