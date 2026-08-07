package com.anymore.auto

data class ServiceDiagnosticEntry(
    val implementationClassName: String,
    val priority: Int,
    val alias: String,
    val singleton: Boolean,
    val status: ServiceDiagnosticStatus,
    val exclusionRule: String?
)
