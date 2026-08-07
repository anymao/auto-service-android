package com.anymore.auto.gradle

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.ScopedArtifacts
import org.gradle.api.Plugin
import org.gradle.api.Project

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class AutoServiceRegisterPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        final AutoServiceExtension extension = project.extensions.create(
                "autoService", AutoServiceExtension.class, true, new HashMap<String, Set<String>>())

        project.pluginManager.withPlugin('com.android.application') {
            def androidComponents = project.extensions.getByType(ApplicationAndroidComponentsExtension)
            androidComponents.onVariants(androidComponents.selector().all()) { variant ->
                def task = project.tasks.register(
                        "androidAutoServiceRegister${variant.name.capitalize()}",
                        AutoServiceRegisterTask.class) { configuredTask ->
                    configuredTask.compileClasspath.from(variant.compileClasspath)
                    configuredTask.sourceCompatibility.set(extension.sourceCompatibility)
                    configuredTask.serviceRequirements.set(
                            extension.checkImplementation ? extension.requireServices : Collections.emptyMap())
                    configuredTask.excludedClassNamePatterns.set(
                            extension.exclusiveRules.collect { it.className })
                    configuredTask.excludedAliasPatterns.set(
                            extension.exclusiveRules.collect { it.alias })
                    configuredTask.diagnosticsEnabled.set(variant.debuggable)
                    configuredTask.logLevel.set(extension.logLevel)
                    configuredTask.variantName.set(variant.name)
                }

                def scopedOperation = variant.artifacts.forScope(ScopedArtifacts.Scope.PROJECT).use(task)
                configureClassesTransform(scopedOperation)
            }
        }
    }

    private static void configureClassesTransform(Object scopedOperation) {
        Method toTransform = scopedOperation.class.methods.find {
            it.name == 'toTransform' && it.parameterCount == 4
        }
        if (toTransform == null) {
            throw new IllegalStateException('当前 AGP 不支持 ScopedArtifact.CLASSES 转换')
        }

        Class scopedArtifactType = toTransform.parameterTypes[0]
        Class classesArtifactType = scopedArtifactType.classLoader.loadClass(
                'com.android.build.api.artifact.ScopedArtifact$CLASSES')
        Object classesArtifact = classesArtifactType.getField('INSTANCE').get(null)
        Class functionType = toTransform.parameterTypes[1]
        toTransform.invoke(
                scopedOperation,
                classesArtifact,
                taskProperty(functionType, 'inputJars'),
                taskProperty(functionType, 'inputDirectories'),
                taskProperty(functionType, 'outputJar'))
    }

    private static Object taskProperty(Class functionType, String propertyName) {
        return Proxy.newProxyInstance(
                functionType.classLoader,
                [functionType] as Class[],
                { Object proxy, Method method, Object[] arguments ->
                    if (method.name == 'invoke') {
                        return arguments[0]."$propertyName"
                    }
                    if (method.name == 'toString') {
                        return "AutoService task property: $propertyName"
                    }
                    if (method.name == 'hashCode') {
                        return System.identityHashCode(proxy)
                    }
                    if (method.name == 'equals') {
                        return proxy.is(arguments[0])
                    }
                    throw new UnsupportedOperationException(method.name)
                } as InvocationHandler)
    }
}
