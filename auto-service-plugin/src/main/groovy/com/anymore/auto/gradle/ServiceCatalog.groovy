package com.anymore.auto.gradle

import org.gradle.api.GradleException

final class ServiceCatalog {
    private final Map<String, ClassOrigin> classOrigins = new LinkedHashMap<>()
    private final Map<String, List<ServiceCandidate>> candidatesByService = new LinkedHashMap<>()

    void addClass(ClassOrigin origin) {
        ClassOrigin existing = classOrigins.get(origin.className)
        if (existing == null) {
            classOrigins.put(origin.className, origin)
            return
        }
        if (isGeneratedReservedClass(origin.className) || existing.sameInput(origin)) {
            return
        }
        throw new GradleException(
                "发现重复类 ${origin.className}：\n" +
                        "  - ${existing.displayName()}\n" +
                        "  - ${origin.displayName()}\n" +
                        '请解决依赖冲突，auto-service 不会选择其中一个定义。')
    }

    ClassOrigin classOrigin(String className) {
        classOrigins.get(className)
    }

    void addCandidate(ServiceCandidate candidate) {
        candidatesByService
                .computeIfAbsent(candidate.serviceClassName) { new ArrayList<ServiceCandidate>() }
                .add(candidate)
    }

    List<ServiceCandidate> candidatesFor(String serviceClassName) {
        immutableSorted(candidatesByService.getOrDefault(serviceClassName, Collections.emptyList()))
    }

    List<ServiceCandidate> registeredFor(String serviceClassName) {
        immutableSorted(candidatesByService
                .getOrDefault(serviceClassName, Collections.emptyList())
                .findAll { it.status == ServiceCandidateStatus.REGISTERED })
    }

    List<ServiceCandidate> registeredFor(String serviceClassName, String alias) {
        registeredFor(serviceClassName).findAll { alias == null || alias.empty || it.alias == alias }
                .asImmutable()
    }

    List<ServiceCandidate> excludedCandidates() {
        immutableSorted(candidatesByService.values()
                .flatten()
                .findAll { it.status == ServiceCandidateStatus.EXCLUDED } as List<ServiceCandidate>)
    }

    int candidateImplementationCount() {
        candidatesByService.values()
                .flatten()
                .collect { it.implementationClassName }
                .toSet()
                .size()
    }

    int uniqueClassCount() {
        classOrigins.size()
    }

    Map<String, List<ServiceCandidate>> registeredByService() {
        Map<String, List<ServiceCandidate>> result = new LinkedHashMap<>()
        candidatesByService.keySet().sort().each { String serviceClassName ->
            List<ServiceCandidate> candidates = registeredFor(serviceClassName)
            if (!candidates.empty) {
                result.put(serviceClassName, candidates)
            }
        }
        Collections.unmodifiableMap(result)
    }

    static boolean isGeneratedReservedClass(String className) {
        className == 'com.anymore.auto.ServiceRegistry' ||
                className == 'com.anymore.auto.ServiceRegistryDiagnostics' ||
                className.startsWith('com.anymore.auto.ServiceRegistryDiagnostics$')
    }

    private static List<ServiceCandidate> immutableSorted(Collection<ServiceCandidate> candidates) {
        List<ServiceCandidate> sorted = new ArrayList<>(candidates)
        Collections.sort(sorted)
        Collections.unmodifiableList(sorted)
    }
}
