package com.anymore.auto.gradle

import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile

class AutoServiceRegisterPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        final AutoServiceExtension autoServiceExtension = project.getExtensions().create("autoService", AutoServiceExtension.class, true, new HashMap<String,Set<String>>())
        project.afterEvaluate {
            if (!project.plugins.hasPlugin(AppPlugin)) return
            final android = project.getExtensions().getByType(AppExtension.class)
            android.applicationVariants.forEach { variant ->
                //产物目录
                final workDir = project.file("${project.buildDir}${File.separator}intermediates${File.separator}auto_service${File.separator}${variant.dirName}${File.separator}")
                final classpath = project.files(android.bootClasspath, variant.javaCompileProvider.get().classpath, variant.javaCompileProvider.get().destinationDir)
                final registerTask = project.tasks.create("androidAutoServiceRegisterTask${variant.name.capitalize()}", AutoServiceRegisterTask.class) {
                    it.setClasspath(classpath)
                    it.setTargetDir(new File(workDir, "src"))
                    if (autoServiceExtension.checkImplementation) {
                        it.setRequiredServices(autoServiceExtension.requireServices)
                    }

                }
                final compileTask = project.tasks.create("compileAndroidAutoServiceRegistry${variant.name.capitalize()}", JavaCompile.class) {
                    it.setSource(new File(workDir, "src"))
                    it.include("**/*.java")
                    it.setClasspath(classpath)
                    it.setDestinationDir(variant.getJavaCompileProvider().get().getDestinationDir())
                    it.setSourceCompatibility("1.7")
                    it.setTargetCompatibility("1.7")
                }
                registerTask.mustRunAfter(variant.javaCompileProvider.get())
                compileTask.mustRunAfter(registerTask)
                variant.assembleProvider.get().dependsOn(registerTask, compileTask)
            }

        }
    }
}