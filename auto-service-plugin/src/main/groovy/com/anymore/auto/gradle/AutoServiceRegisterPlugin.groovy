package com.anymore.auto.gradle

import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile

class AutoServiceRegisterPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        final AutoServiceExtension autoServiceExtension = project.getExtensions().create("autoService", AutoServiceExtension.class, true, new HashMap<String,Set<String>>())

        // 设置日志级别
        Logger.level = autoServiceExtension.getLogLevel()

        project.afterEvaluate {
            if (!project.plugins.hasPlugin(AppPlugin)) return

            def android = project.extensions.findByType(AppExtension)
            if (android == null) {
                Logger.w("Android Extension not found, skip auto-service plugin")
                return
            }

            android.applicationVariants.forEach { variant ->
                // 产物目录
                final workDir = project.layout.buildDirectory
                    .dir("intermediates/auto_service/${variant.dirName}")
                    .get()
                    .asFile

                // AGP 8.x 使用编译后的 class 目录
                final javaCompileTask = variant.javaCompileProvider.get()
                final compileOutputDir = project.layout.buildDirectory
                    .dir("intermediates/javac/${variant.name}/compile${variant.name.capitalize()}JavaWithJavac/classes")
                    .get()
                    .asFile

                final classpath = project.files(android.bootClasspath, variant.javaCompileProvider.get().classpath, compileOutputDir)

                final registerTask = project.tasks.register("androidAutoServiceRegisterTask${variant.name.capitalize()}", AutoServiceRegisterTask.class)
                registerTask.configure {
                    it.setClasspath(classpath)
                    it.setTargetDir(new File(workDir, "src"))
                    if (autoServiceExtension.checkImplementation) {
                        it.setRequiredServices(autoServiceExtension.requireServices)
                    }
                    it.setExclusiveRules(autoServiceExtension.exclusiveRules)
                }

                Logger.v("autoService:$autoServiceExtension")
                Logger.d("SourceCompatibility:${autoServiceExtension.sourceCompatibility}")

                final compileTask = project.tasks.register("compileAndroidAutoServiceRegistry${variant.name.capitalize()}", JavaCompile.class)
                compileTask.configure {
                    it.setSource(new File(workDir, "src"))
                    it.include("**/*.java")
                    it.setClasspath(classpath)
                    it.destinationDirectory.set(compileOutputDir)
                    it.setSourceCompatibility(autoServiceExtension.sourceCompatibility)
                    it.setTargetCompatibility(autoServiceExtension.sourceCompatibility)
                }

                // 确保任务依赖顺序：Java编译完成后，扫描生成代码，然后编译生成的代码
                registerTask.configure {
                    it.mustRunAfter(variant.javaCompileProvider.get())
                }
                compileTask.configure {
                    it.dependsOn(variant.javaCompileProvider.get())
                    it.mustRunAfter(registerTask.get())
                }

                // 找到 dex 任务并添加依赖，确保使用生成的 class
                def dexTaskName = "dexBuilder${variant.name.capitalize()}"
                def dexTask = project.tasks.findByName(dexTaskName)
                if (dexTask != null) {
                    dexTask.dependsOn(compileTask)
                }

                variant.assembleProvider.get().dependsOn(registerTask, compileTask)
            }
        }
    }
}
