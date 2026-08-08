package com.anymore.auto_service_android.demo

import com.anymore.auto.ServiceDiagnosticReport
import com.anymore.auto.ServiceLoader
import java.util.concurrent.Callable

interface DemoServiceSource {
    fun runnables(alias: String = ""): Iterable<Runnable>

    fun callables(): Iterable<Callable<*>>

    fun firstRunnable(): Runnable?

    fun lastRunnable(): Runnable?

    fun runnableDiagnostics(): ServiceDiagnosticReport
}

class ServiceLoaderDemoServiceSource : DemoServiceSource {
    override fun runnables(alias: String): Iterable<Runnable> = ServiceLoader.load(alias)

    override fun callables(): Iterable<Callable<*>> = ServiceLoader.load<Callable<*>>()

    override fun firstRunnable(): Runnable? = ServiceLoader.load<Runnable>().firstPriority

    override fun lastRunnable(): Runnable? = ServiceLoader.load<Runnable>().lastPriority

    override fun runnableDiagnostics(): ServiceDiagnosticReport = ServiceLoader.diagnose<Runnable>()
}
