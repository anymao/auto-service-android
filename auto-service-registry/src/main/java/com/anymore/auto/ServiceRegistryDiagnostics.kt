package com.anymore.auto

object ServiceRegistryDiagnostics {
    @JvmStatic
    @JvmOverloads
    fun get(clazz: Class<*>, alias: String = "") = ServiceDiagnosticReport(
        clazz.name,
        alias,
        ServiceDiagnosticAvailability.UNAVAILABLE_PLUGIN_NOT_APPLIED,
        emptyList()
    )
}
