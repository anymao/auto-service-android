package com.anymore.auto.gradle

/**
 * Created by anymore on 2022/4/8.
 */
class AutoServiceExtension {
    boolean checkImplementation = false
    private Set<String> requires = new HashSet<>()

    AutoServiceExtension(boolean checkImplementation, Set<String> requires) {
        this.checkImplementation = checkImplementation
        this.requires = requires
    }

    Set<String> getRequireServices() {
        return Collections.unmodifiableSet(requires)
    }

    def require(String service) {
        requires.add(service)
    }

//    def requires(String...services) {
//        requires.addAll(services)
//    }


    @Override
    String toString() {
        return "AutoServiceExtension{" +
                "checkImplementation=" + checkImplementation +
                ", requires=" + requires +
                '}';
    }
}