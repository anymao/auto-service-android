package com.anymore.auto.gradle

import java.util.function.Function

/**
 * Created by anymore on 2022/4/8.
 */
class AutoServiceExtension {
    boolean checkImplementation = false
    String sourceCompatibility = "1.7"
    private HashMap<String, Set<String>> requires = new LinkedHashMap<>()
    private HashSet<ExclusiveRule> exclusives = new HashSet<>()
    private int logLevel = Logger.INFO

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

    def setLogLevel(String level) {
        switch (level) {
            case "VERBOSE":
                logLevel = Logger.VERBOSE
                break
            case "DEBUG":
                logLevel = Logger.DEBUG
                break
            case "INFO":
                logLevel = Logger.INFO
                break
            case "WARN":
                logLevel = Logger.WARN
                break
            case "ERROR":
                logLevel = Logger.ERROR
                break
            default:
                logLevel = Logger.INFO
                break
        }
    }

    def getLogLevel() {
        return logLevel
    }

    @Override
    String toString() {
        return "AutoServiceExtension{" +
                "checkImplementation=" + checkImplementation +
                ", sourceCompatibility=" + sourceCompatibility +
                ", requires=" + requires +
                ", exclusives=" + exclusives +
                ", _logLevel=" + logLevel +
                '}'
    }
}