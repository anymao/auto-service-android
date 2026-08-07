package com.anymore.auto.gradle

import org.gradle.api.GradleException

/** 对扩展声明的必选服务执行稳定、可行动的构建期校验。 */
final class RequiredServiceValidator {

    void validate(Map<String, Set<String>> requirements, ServiceCatalog catalog) {
        List<String> failures = []
        requirements.keySet().sort().each { String serviceClassName ->
            List<String> aliases = new ArrayList<>(requirements[serviceClassName] ?: [''])
            Collections.sort(aliases)
            aliases.each { String alias ->
                String failure = failureFor(serviceClassName, alias ?: '', catalog)
                if (failure != null) failures.add(failure)
            }
        }
        if (!failures.empty) {
            throw new GradleException(failures.join('\n\n'))
        }
    }

    private static String failureFor(String serviceClassName, String alias, ServiceCatalog catalog) {
        List<ServiceCandidate> allCandidates = catalog.candidatesFor(serviceClassName)
        List<ServiceCandidate> registered = catalog.registeredFor(serviceClassName)
        List<ServiceCandidate> matching = catalog.registeredFor(serviceClassName, alias)
        if (!matching.empty) return null

        String requirement = alias.empty
                ? "Missing required service: ${serviceClassName}"
                : "Missing required service: ${serviceClassName} (alias=\"${alias}\")"
        if (allCandidates.empty) {
            return requirement + '\n' +
                    "No @AutoService implementation was found in this variant's full class scope.\n" +
                    'Check the annotation, dependency inclusion, and whether the plugin is applied to the application module.'
        }
        if (registered.empty) {
            StringBuilder message = new StringBuilder(requirement)
                    .append('\n')
                    .append(allCandidates.size())
                    .append(' implementations were found, but all were excluded:')
            allCandidates.each { ServiceCandidate candidate ->
                message.append('\n  - ')
                        .append(candidate.implementationClassName)
                        .append(', excluded by ')
                        .append(candidate.exclusionRule ?: 'an exclusion rule')
            }
            return message.toString()
        }

        StringBuilder message = new StringBuilder(requirement)
                .append('\n')
                .append(registered.size())
                .append(' registered implementations were found, but none match the requested alias:')
        registered.each { ServiceCandidate candidate ->
            message.append('\n  - ')
                    .append(candidate.implementationClassName)
                    .append(' (alias="')
                    .append(candidate.alias)
                    .append('", priority=')
                    .append(candidate.priority)
                    .append(')')
        }
        List<String> aliases = registered.collect { it.alias }.toSet().toList().sort()
        message.append('\nAvailable aliases: [')
                .append(aliases.collect { "\"${it}\"" }.join(', '))
                .append(']')
        message.toString()
    }
}
