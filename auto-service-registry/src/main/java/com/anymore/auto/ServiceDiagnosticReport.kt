package com.anymore.auto

data class ServiceDiagnosticReport(
    val serviceClassName: String,
    val requestedAlias: String,
    val availability: ServiceDiagnosticAvailability,
    val entries: List<ServiceDiagnosticEntry>
) {
    val registeredCount: Int
        get() = entries.count { it.status == ServiceDiagnosticStatus.REGISTERED }

    val matchingCount: Int
        get() = entries.count {
            it.status == ServiceDiagnosticStatus.REGISTERED &&
                (requestedAlias.isEmpty() || it.alias == requestedAlias)
        }

    val availableAliases: Set<String>
        get() = entries.asSequence()
            .filter { it.status == ServiceDiagnosticStatus.REGISTERED }
            .map { it.alias }
            .toCollection(linkedSetOf())

    override fun toString(): String = buildString {
        append(serviceClassName).append(": ")
        if (availability != ServiceDiagnosticAvailability.AVAILABLE) {
            append("diagnostics unavailable (").append(availability).append(')')
            return@buildString
        }
        append(entries.size)
            .append(" candidates, ")
            .append(registeredCount)
            .append(" registered\n")
        entries.forEach { entry ->
            append(if (entry.status == ServiceDiagnosticStatus.REGISTERED) "  ✓ " else "  × ")
            append(entry.implementationClassName)
                .append(" (priority=")
                .append(entry.priority)
                .append(", alias=\"")
                .append(entry.alias)
                .append('\"')
            if (entry.singleton) append(", singleton")
            append(')')
            entry.exclusionRule?.let { append(" — excluded by ").append(it) }
            append('\n')
        }
        append("Available aliases: ").append(availableAliases)
        if (requestedAlias.isNotEmpty()) {
            append("\nRequested alias \"")
                .append(requestedAlias)
                .append("\": ")
                .append(matchingCount)
                .append(" matches")
        }
    }
}
