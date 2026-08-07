package com.anymore.auto.gradle

final class ServiceCandidate implements Comparable<ServiceCandidate> {
    final String serviceClassName
    final String implementationClassName
    final int priority
    final String alias
    final boolean singleton
    final ServiceCandidateStatus status
    final String exclusionRule
    final ClassOrigin origin

    ServiceCandidate(
            String serviceClassName,
            String implementationClassName,
            int priority,
            String alias,
            boolean singleton,
            ServiceCandidateStatus status,
            String exclusionRule,
            ClassOrigin origin) {
        this.serviceClassName = serviceClassName
        this.implementationClassName = implementationClassName
        this.priority = priority
        this.alias = alias
        this.singleton = singleton
        this.status = status
        this.exclusionRule = exclusionRule
        this.origin = origin
    }

    @Override
    int compareTo(ServiceCandidate other) {
        int priorityOrder = priority <=> other.priority
        priorityOrder != 0 ? priorityOrder : implementationClassName <=> other.implementationClassName
    }
}
