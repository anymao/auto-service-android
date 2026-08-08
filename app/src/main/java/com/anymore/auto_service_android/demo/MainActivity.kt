package com.anymore.auto_service_android.demo

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.graphics.Color
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var overallStatus: TextView
    private lateinit var runAgain: Button
    private lateinit var resultsContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        overallStatus = findViewById(R.id.demo_overall_status)
        runAgain = findViewById(R.id.demo_run_again)
        resultsContainer = findViewById(R.id.demo_results_container)

        runAgain.setOnClickListener { runScenarios() }
        runScenarios()
    }

    private fun runScenarios() {
        runAgain.isEnabled = false
        overallStatus.text = getString(R.string.demo_running)
        overallStatus.setTextColor(Color.DKGRAY)
        resultsContainer.removeAllViews()

        thread {
            val diagnosticsExpected =
                applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
            val results = DemoScenarioRunner(
                ServiceLoaderDemoServiceSource(),
                diagnosticsExpected
            ).runAll()

            runOnUiThread {
                renderResults(results)
                runAgain.isEnabled = true
            }
        }
    }

    private fun renderResults(results: List<DemoScenarioResult>) {
        overallStatus.text = DemoScenarioResult.summarize(results)
        overallStatus.setTextColor(
            colorFor(if (results.all { it.passed }) R.color.demo_status_success else R.color.demo_status_failure)
        )
        resultsContainer.removeAllViews()
        results.forEach { result ->
            resultsContainer.addView(
                TextView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    text = getString(
                        if (result.passed) R.string.demo_result_passed else R.string.demo_result_failed,
                        result.title,
                        result.summary,
                        result.details
                    )
                    setTextColor(
                        colorFor(
                            if (result.passed) R.color.demo_status_success else R.color.demo_status_failure
                        )
                    )
                    setPadding(0, 20, 0, 20)
                }
            )
        }
    }

    private fun colorFor(colorRes: Int): Int = ContextCompat.getColor(this, colorRes)
}
