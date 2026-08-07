package com.anymore.auto.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

import javax.tools.ToolProvider
@CacheableTask
abstract class AutoServiceRegisterTask extends DefaultTask {

    @InputFiles @Classpath
    abstract ListProperty<RegularFile> getInputJars()

    @InputFiles @PathSensitive(PathSensitivity.RELATIVE)
    abstract ListProperty<Directory> getInputDirectories()

    @CompileClasspath
    abstract ConfigurableFileCollection getCompileClasspath()

    @Input
    abstract Property<String> getSourceCompatibility()

    @Input
    abstract MapProperty<String, Set<String>> getServiceRequirements()

    @Input
    abstract ListProperty<String> getExcludedClassNamePatterns()

    @Input
    abstract ListProperty<String> getExcludedAliasPatterns()

    @Input
    abstract Property<Boolean> getDiagnosticsEnabled()

    @Input
    abstract Property<Integer> getLogLevel()

    @Input
    abstract Property<String> getVariantName()

    @OutputFile
    abstract org.gradle.api.file.RegularFileProperty getOutputJar()

    AutoServiceRegisterTask() {
        diagnosticsEnabled.convention(false)
        logLevel.convention(Logger.INFO)
        variantName.convention('unknown')
    }

    @TaskAction
    void run() {
        File sourceDirectory = new File(temporaryDir, 'src')
        File classesDirectory = new File(temporaryDir, 'classes')
        project.delete(sourceDirectory, classesDirectory)
        sourceDirectory.mkdirs()
        classesDirectory.mkdirs()

        def inputFiles = project.files(inputJars.get().collect { it.asFile }, inputDirectories.get().collect { it.asFile })
        def rules = toExclusiveRules(excludedClassNamePatterns.get(), excludedAliasPatterns.get())
        AutoServiceLog log = new AutoServiceLog(logLevel.get(), variantName.get())
        new AutoServiceRegisterAction(
                inputFiles,
                sourceDirectory,
                serviceRequirements.get(),
                rules,
                diagnosticsEnabled.get(),
                log).execute()
        List<File> sources = []
        sourceDirectory.eachFileRecurse { File source ->
            if (source.file && source.name.endsWith('.java')) sources.add(source)
        }
        sources.sort { File left, File right -> left.absolutePath <=> right.absolutePath }
        if (!sources.empty) {
            def compiler = ToolProvider.systemJavaCompiler
            if (compiler == null) throw new GradleException('当前 JDK 不包含 Java 编译器')
            def classpath = project.files(inputFiles, compileClasspath).asPath
            List<String> arguments = [
                    '-classpath', classpath,
                    '-source', sourceCompatibility.get(),
                    '-target', sourceCompatibility.get(),
                    '-d', classesDirectory.absolutePath
            ]
            arguments.addAll(sources*.absolutePath)
            def result = compiler.run(null, null, null, arguments as String[])
            if (result != 0) throw new GradleException("编译 auto-service 生成源码失败，退出码：${result}")
        }
        File output = outputJar.get().asFile
        new DeterministicJarWriter().write(
                inputJars.get().collect { it.asFile },
                inputDirectories.get().collect { it.asFile },
                classesDirectory,
                output)
    }

    static Set<ExclusiveRule> toExclusiveRules(List<String> classNamePatterns, List<String> aliasPatterns) {
        if (classNamePatterns.size() != aliasPatterns.size()) {
            throw new GradleException('排除规则的类名与别名数量不一致')
        }
        def rules = new LinkedHashSet<ExclusiveRule>()
        classNamePatterns.eachWithIndex { String className, int index ->
            rules.add(new ExclusiveRule(className, aliasPatterns[index]))
        }
        return rules
    }
}
