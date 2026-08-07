package com.anymore.auto.gradle

/** 变体实例级日志器，避免并行任务共享可变日志级别。 */
final class AutoServiceLog {
    final int level
    final String variantName
    private final PrintStream output

    AutoServiceLog(int level, String variantName = '', PrintStream output = System.out) {
        this.level = level
        this.variantName = variantName ?: 'unknown'
        this.output = output
    }

    void debug(String message) {
        if (level <= Logger.DEBUG) output.println("[auto-service][DEBUG] ${message}")
    }

    void info(String message) {
        if (level <= Logger.INFO) output.println("[auto-service] ${message}")
    }

    void summary(ServiceCatalog catalog, long elapsedMillis) {
        info("${variantName}: scanned ${catalog.uniqueClassCount()} unique classes, " +
                "found ${catalog.candidateImplementationCount()} candidates, " +
                "registered ${catalog.registeredBindingCount()} bindings across " +
                "${catalog.registeredInterfaceCount()} interfaces, " +
                "excluded ${catalog.excludedImplementationCount()}, ${elapsedMillis} ms.")
        if (catalog.registeredBindingCount() == 0) {
            info('未发现已注册服务；请检查 @AutoService 注解以及 auto-service 插件是否应用于 Application 模块。')
        }
    }
}
