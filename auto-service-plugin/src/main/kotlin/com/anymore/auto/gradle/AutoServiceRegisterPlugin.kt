package com.anymore.auto.gradle

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.ScopedArtifacts
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

class AutoServiceRegisterPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create(
            "autoService",
            AutoServiceExtension::class.java,
            false,
            LinkedHashMap<String, Set<String>>()
        )
        var applicationConfigured = false

        project.pluginManager.withPlugin("com.android.application") {
            applicationConfigured = true
            val androidComponents = project.extensions.getByType(
                ApplicationAndroidComponentsExtension::class.java
            )
            androidComponents.onVariants(androidComponents.selector().all()) { variant ->
                val task = project.tasks.register(
                    "androidAutoServiceRegister${variant.name.replaceFirstChar(Char::uppercase)}",
                    AutoServiceRegisterTask::class.java
                ) { configuredTask ->
                    configuredTask.compileClasspath.from(variant.compileClasspath)
                    configuredTask.sourceCompatibility.set(extension.sourceCompatibility)
                    configuredTask.diagnosticsEnabled.set(variant.debuggable)
                    configuredTask.logLevel.set(extension.logLevel)
                    configuredTask.variantName.set(variant.name)
                    configuredTask.serviceRequirements.set(
                        if (extension.checkImplementation) extension.requireServices else emptyMap()
                    )
                    configuredTask.excludedClassNamePatterns.set(
                        extension.exclusiveRules.map { it.className }
                    )
                    configuredTask.excludedAliasPatterns.set(
                        extension.exclusiveRules.map { it.alias }
                    )
                }

                variant.artifacts.forScope(ScopedArtifacts.Scope.ALL)
                    .use(task)
                    .toTransform(
                        ScopedArtifact.CLASSES,
                        { it.inputJars },
                        { it.inputDirectories },
                        { it.outputJar }
                    )
            }
        }

        project.pluginManager.withPlugin("com.android.library") {
            if (!applicationConfigured) {
                throw GradleException("auto-service 只能应用于 Android Application 模块")
            }
        }
        project.afterEvaluate {
            if (!applicationConfigured) {
                throw GradleException("auto-service 只能应用于 Android Application 模块")
            }
        }
    }
}
