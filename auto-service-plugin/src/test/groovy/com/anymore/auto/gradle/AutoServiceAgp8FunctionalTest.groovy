package com.anymore.auto.gradle

import com.anymore.auto.AutoService
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.After
import org.junit.Before
import org.junit.Test

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import javax.tools.ToolProvider

class AutoServiceAgp8FunctionalTest {

    private File testProjectDir

    @Before
    void setUp() {
        testProjectDir = Files.createTempDirectory('auto-service-agp8').toFile()
    }

    @After
    void tearDown() {
        testProjectDir?.deleteDir()
    }

    @Test
    void releaseBuildTransformsClassesWithoutImplicitDependency() {
        copyFixture('agp8')

        def result = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withPluginClasspath()
                .withArguments(':app:assembleRelease', ':app:generateReleaseLintVitalReportModel', '--stacktrace', '--console=plain')
                .build()

        assert result.output.contains('androidAutoServiceRegisterRelease'): result.output
        assert new File(testProjectDir, 'app/build/outputs/apk/release/app-release-unsigned.apk').isFile()
    }

    @Test
    void disabledPrecheckDoesNotRejectMissingRequiredService() {
        copyFixture('agp8')
        new File(testProjectDir, 'app/build.gradle') << '''

autoService {
    checkImplementation = false
    sourceCompatibility = '1.8'
    require('missing.Service')
}
'''

        def result = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withPluginClasspath()
                .withArguments(':app:assembleRelease', '--stacktrace', '--console=plain')
                .build()

        assert result.output.contains('androidAutoServiceRegisterRelease'): result.output
        assert new File(testProjectDir, 'app/build/outputs/apk/release/app-release-unsigned.apk').isFile()
    }

    @Test
    void debugAndReleaseTransformsPreserveServiceClassesAndGenerateRegistry() {
        copyFixture('agp8')

        def result = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withPluginClasspath()
                .withArguments(':app:verifyAutoServiceOutputs', '--stacktrace', '--console=plain')
                .build()

        assert result.output.contains('androidAutoServiceRegisterDebug'): result.output
        assert result.output.contains('androidAutoServiceRegisterRelease'): result.output
    }

    @Test
    void generatedRegistryLoadsServicesThroughServiceLoaderAtRuntime() {
        copyFixture('agp8')

        def result = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withPluginClasspath()
                .withArguments(':app:verifyAutoServiceRuntime', '--stacktrace', '--console=plain')
                .build()

        assert result.output.contains('androidAutoServiceRegisterDebug'): result.output
    }

    @Test
    void excludedServiceIsNotReturnedByGeneratedRegistry() {
        copyFixture('agp8')
        new File(testProjectDir, 'app/build.gradle') << '''

autoService {
    excludeClassName('test\\\\.sample\\\\.ServiceImpl')
}
'''

        def result = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withPluginClasspath()
                .withArguments(':app:verifyExcludedAutoServiceRuntime', '--stacktrace', '--console=plain')
                .build()

        assert result.output.contains('androidAutoServiceRegisterDebug'): result.output
    }

    @Test
    void generatedRegistryFiltersMultipleServicesByAliasAtRuntime() {
        copyFixture('agp8')
        File source = new File(testProjectDir, 'app/src/main/java/test/sample/SecondaryServiceImpl.java')
        source.text = '''
package test.sample;

import com.anymore.auto.AutoService;

@AutoService(value = Runnable.class, alias = "secondary", priority = 10)
public final class SecondaryServiceImpl implements Runnable {
    @Override
    public void run() {
    }
}
'''

        def result = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withPluginClasspath()
                .withArguments(':app:verifyAliasedAutoServiceRuntime', '--stacktrace', '--console=plain')
                .build()

        assert result.output.contains('androidAutoServiceRegisterDebug'): result.output
    }

    @Test
    void pluginRejectsAndroidLibraryModule() {
        copyFixture('agp8')
        File buildFile = new File(testProjectDir, 'app/build.gradle')
        buildFile.text = buildFile.text
                .replace("id 'com.android.application'", "id 'com.android.library'")
                .replace("        applicationId 'test.sample'\n", '')

        def result = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withPluginClasspath()
                .withArguments(':app:tasks', '--stacktrace', '--console=plain')
                .buildAndFail()

        assert result.output.contains('auto-service 只能应用于 Android Application 模块'): result.output
    }

    @Test
    void pluginRejectsProjectWithoutAndroidApplicationPlugin() {
        new File(testProjectDir, 'settings.gradle').text = "rootProject.name = 'plain-project'\n"
        new File(testProjectDir, 'build.gradle').text = "plugins { id 'auto-service' }\n"

        def result = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withPluginClasspath()
                .withArguments('tasks', '--stacktrace', '--console=plain')
                .buildAndFail()

        assert result.output.contains('auto-service 只能应用于 Android Application 模块'): result.output
    }

    @Test
    void transformIsUpToDateAndRestoredFromCacheWithStableHash() {
        copyFixture('agp8')
        new File(testProjectDir, 'settings.gradle') << '''

buildCache {
    local {
        directory = file('local-build-cache')
    }
}
'''
        List<String> arguments = [
                ':app:androidAutoServiceRegisterDebug',
                '--build-cache',
                '--stacktrace',
                '--console=plain'
        ]
        def first = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withPluginClasspath()
                .withArguments(arguments)
                .build()
        assert first.task(':app:androidAutoServiceRegisterDebug').outcome == TaskOutcome.SUCCESS
        File output = new File(
                testProjectDir,
                'app/build/intermediates/classes/debug/ALL/androidAutoServiceRegisterDebug/classes.jar')
        assert output.isFile(): "转换输出不存在：${output}"
        String firstHash = sha256(output)

        def second = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withPluginClasspath()
                .withArguments(arguments)
                .build()
        assert second.task(':app:androidAutoServiceRegisterDebug').outcome == TaskOutcome.UP_TO_DATE

        new File(testProjectDir, 'app/build').deleteDir()
        def cached = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withPluginClasspath()
                .withArguments(arguments)
                .build()
        assert cached.task(':app:androidAutoServiceRegisterDebug').outcome == TaskOutcome.FROM_CACHE
        assert sha256(output) == firstHash
    }

    @Test
    void discoversServicesFromProjectsAndDirectAndTransitiveAars() {
        copyFixture('agp8')

        def publishResult = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withPluginClasspath()
                .withArguments(
                        ':external-producer:publish',
                        ':external-bridge:publish',
                        '--stacktrace',
                        '--console=plain')
                .build()
        File bridgePom = new File(
                testProjectDir,
                'test-repo/test/external/external-bridge/1.0/external-bridge-1.0.pom')
        assert bridgePom.isFile(): publishResult.output
        assert bridgePom.text.contains('external-producer'): '桥接 AAR 的 POM 未保留传递依赖'

        new File(testProjectDir, 'app/build.gradle') << '''

dependencies {
    implementation project(':java-services')
    implementation 'test.external:external-bridge:1.0'
}

autoService {
    exclude('test\\\\.external\\\\.ExcludedExternalTask', 'excluded')
}
'''

        def result = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withPluginClasspath()
                .withArguments(':app:verifyAllScopeRuntime', '--stacktrace', '--console=plain')
                .build()

        assert result.output.contains('androidAutoServiceRegisterDebug'): result.output
        assert result.output.contains('androidAutoServiceRegisterRelease'): result.output
    }

    @Test
    void rejectsOrdinaryDuplicateClassesWithBothOrigins() {
        copyFixture('agp8')
        File firstJar = new File(testProjectDir, 'libs/duplicate-first.jar')
        File secondJar = new File(testProjectDir, 'libs/duplicate-second.jar')
        createDuplicateClassJar(firstJar, 'first')
        createDuplicateClassJar(secondJar, 'second')
        new File(testProjectDir, 'app/build.gradle') << '''

dependencies {
    implementation files('../libs/duplicate-first.jar', '../libs/duplicate-second.jar')
}
'''

        def result = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withPluginClasspath()
                .withArguments(':app:assembleDebug', '--stacktrace', '--console=plain')
                .buildAndFail()

        assert result.output.contains('test.duplicate.Duplicate'): result.output
        assert result.output.contains('duplicate-first.jar'): result.output
        assert result.output.contains('duplicate-second.jar'): result.output
    }

    private void copyFixture(String fixtureName) {
        URL fixture = getClass().getResource("/fixtures/${fixtureName}")
        assert fixture != null: "找不到测试夹具：${fixtureName}"
        File fixtureDirectory = new File(fixture.toURI())
        fixtureDirectory.eachFileRecurse { File source ->
            File destination = new File(testProjectDir, fixtureDirectory.toPath().relativize(source.toPath()).toString())
            if (source.isDirectory()) {
                destination.mkdirs()
            } else {
                destination.parentFile.mkdirs()
                destination.bytes = source.bytes
            }
        }

        File currentDirectory = new File(System.getProperty('user.dir'))
        File localProperties
        while (currentDirectory != null && localProperties == null) {
            File candidate = new File(currentDirectory, 'local.properties')
            if (candidate.isFile()) {
                localProperties = candidate
            }
            currentDirectory = currentDirectory.parentFile
        }
        if (localProperties?.isFile()) {
            Files.copy(localProperties.toPath(), new File(testProjectDir, 'local.properties').toPath(), StandardCopyOption.REPLACE_EXISTING)
        }

        File annotationJar = new File(AutoService.protectionDomain.codeSource.location.toURI())
        File fixtureAnnotationJar = new File(testProjectDir, 'libs/auto-service-annotation.jar')
        fixtureAnnotationJar.parentFile.mkdirs()
        Files.copy(annotationJar.toPath(), fixtureAnnotationJar.toPath(), StandardCopyOption.REPLACE_EXISTING)

        File loaderJar = new File(Class.forName('com.anymore.auto.ServiceSupplier').protectionDomain.codeSource.location.toURI())
        File fixtureLoaderJar = new File(testProjectDir, 'libs/auto-service-loader.jar')
        Files.copy(loaderJar.toPath(), fixtureLoaderJar.toPath(), StandardCopyOption.REPLACE_EXISTING)

        File registryJar = new File(Class.forName('com.anymore.auto.ServiceDiagnosticReport').protectionDomain.codeSource.location.toURI())
        File fixtureRegistryJar = new File(testProjectDir, 'libs/auto-service-registry.jar')
        Files.copy(registryJar.toPath(), fixtureRegistryJar.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private static String sha256(File file) {
        java.security.MessageDigest.getInstance('SHA-256')
                .digest(file.bytes)
                .encodeHex()
                .toString()
    }

    private static void createDuplicateClassJar(File jarFile, String marker) {
        File workDirectory = new File(jarFile.parentFile, "${jarFile.name}.work")
        File source = new File(workDirectory, 'src/test/duplicate/Duplicate.java')
        File classes = new File(workDirectory, 'classes')
        source.parentFile.mkdirs()
        classes.mkdirs()
        source.text = """
package test.duplicate;

public final class Duplicate {
    public static final String MARKER = "${marker}";
}
"""
        def compiler = ToolProvider.systemJavaCompiler
        assert compiler != null: '运行功能测试需要完整 JDK'
        int exitCode = compiler.run(null, null, null, '-d', classes.absolutePath, source.absolutePath)
        assert exitCode == 0: "编译重复类测试输入失败：${jarFile.name}"

        File classFile = new File(classes, 'test/duplicate/Duplicate.class')
        jarFile.parentFile.mkdirs()
        jarFile.withOutputStream { output ->
            new JarOutputStream(output).withCloseable { archive ->
                archive.putNextEntry(new JarEntry('test/duplicate/Duplicate.class'))
                archive.write(classFile.bytes)
                archive.closeEntry()
            }
        }
    }

}
