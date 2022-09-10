package com.anymore.auto.gradle

import java.util.function.Function

/**
 * Created by anymore on 2022/4/8.
 */
class AutoServiceExtension {
    boolean checkImplementation = false
    private HashMap<String, Set<String>> requires = new LinkedHashMap<>()
    private HashSet<ExclusiveRule> exclusives = new HashSet<>()

    AutoServiceExtension(boolean checkImplementation, HashMap<String, Set<String>> requires) {
        this.checkImplementation = checkImplementation
        this.requires = requires
    }

    Map<String, Set<String>> getRequireServices() {
        return Collections.unmodifiableMap(requires)
    }

    Set<ExclusiveRule> getExclusiveRules() {
        return Collections.unmodifiableSet(exclusives)
    }

    def require(String service) {
        require(service, "")
    }

    def require(String service, String alias) {
        requires.computeIfAbsent(service, new Function<String, Set<String>>() {
            @Override
            Set<String> apply(String s) {
                return new HashSet()
            }
        }).add(alias)
    }

    def excludeClassName(String className) {
        exclude(className, ".*")
    }

    def excludeAlias(String alias) {
        exclude(".*", alias)
    }

    def exclude(String className, String alias) {
        exclusives.add(new ExclusiveRule(className, alias))
    }


    @Override
    String toString() {
        return "AutoServiceExtension{" +
                "checkImplementation=" + checkImplementation +
                ", requires=" + requires +
                ", exclusives=" + exclusives +
                '}';
    }
}