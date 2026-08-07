package com.anymore.auto.gradle

import org.gradle.api.file.FileCollection

/** 编排扫描、校验与源码生成；Gradle 任务生命周期由外层 Task 管理。 */
final class AutoServiceRegisterAction {
    final FileCollection classpath
    final File targetDir
    private final Map<String, Set<String>> requiredServices
    private final Set<ExclusiveRule> exclusiveRules
    private final boolean diagnosticsEnabled
    private final AutoServiceLog log

    AutoServiceRegisterAction(
            FileCollection classpath,
            File targetDir,
            Map<String, Set<String>> requiredServices,
            Set<ExclusiveRule> exclusiveRules,
            boolean diagnosticsEnabled,
            AutoServiceLog log) {
        this.classpath = classpath.filter { it.exists() }
        this.targetDir = targetDir
        this.requiredServices = requiredServices ?: Collections.emptyMap()
        this.exclusiveRules = exclusiveRules ?: Collections.emptySet()
        this.diagnosticsEnabled = diagnosticsEnabled
        this.log = log
    }

    ServiceCatalog execute() {
        long startedAt = System.currentTimeMillis()
        ServiceCatalog catalog = new ClassMetadataScanner(exclusiveRules, log).scan(classpath.files)
        new RequiredServiceValidator().validate(requiredServices, catalog)
        File registry = new ServiceRegistryGenerator().write(catalog, targetDir)
        File diagnostics = new ServiceRegistryDiagnosticsGenerator().write(
                catalog, targetDir, diagnosticsEnabled)
        log.debug("生成注册表：${registry.absolutePath}")
        log.debug("生成诊断表：${diagnostics.absolutePath}")
        log.summary(catalog, System.currentTimeMillis() - startedAt)
        catalog
    }
}
