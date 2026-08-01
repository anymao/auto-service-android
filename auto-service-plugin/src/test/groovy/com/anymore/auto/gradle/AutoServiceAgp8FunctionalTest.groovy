package com.anymore.auto.gradle

import com.anymore.auto.AutoService
import org.gradle.testkit.runner.GradleRunner
import org.junit.After
import org.junit.Before
import org.junit.Test

import java.nio.file.Files
import java.nio.file.StandardCopyOption

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
    }

}
