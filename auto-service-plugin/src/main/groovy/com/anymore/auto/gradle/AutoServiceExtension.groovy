package com.anymore.auto.gradle

import java.util.function.Function

/**
 * Created by anymore on 2022/4/8.
 */
class AutoServiceExtension {
    boolean checkImplementation = false
    private HashMap<String, Set<String>> requires = new LinkedHashMap<>()

    AutoServiceExtension(boolean checkImplementation, HashMap<String, Set<String>> requires) {
        this.checkImplementation = checkImplementation
        this.requires = requires
    }

    Map<String, Set<String>> getRequireServices() {
        return Collections.unmodifiableMap(requires)
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


    @Override
    String toString() {
        return "AutoServiceExtension{" +
                "checkImplementation=" + checkImplementation +
                ", requires=" + requires +
                '}';
    }
}