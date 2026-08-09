package com.anymore.auto.gradle

import org.gradle.testkit.runner.GradleRunner
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile

import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertTrue

class PublicationConfigurationTest {

    private static File testProjectDir
    private static File publicationRepository

    @BeforeClass
    static void setUpProject() {
        testProjectDir = Files.createTempDirectory('auto-service-publication-project').toFile()
        publicationRepository = Files.createTempDirectory('auto-service-publication-repository').toFile()
        publicationRepository.deleteDir()
        createMinimalFixture(findProjectRoot(), testProjectDir)
    }

    @AfterClass
    static void tearDownProject() {
        testProjectDir?.deleteDir()
        publicationRepository?.deleteDir()
    }

    @Test
    void projectsTaskSucceedsWithoutPublishingCredentials() {
        def result = runner('projects').build()

        assertFalse('普通配置任务不应输出 Maven 用户名', result.output.contains('Maven Username:'))
    }

    @Test
    void appDebugBuildSucceedsWithoutPublishingCredentials() {
        def result = runner(':app:assembleDebug').build()

        assertFalse('普通构建不应输出 Maven 用户名', result.output.contains('Maven Username:'))
    }

    @Test
    void publishTaskRejectsMissingCredentialsBeforeRepositoryWrite() {
        File initScript = new File(testProjectDir, 'redirect-publication.gradle')
        initScript.text = """
gradle.projectsEvaluated {
    gradle.rootProject.project(':auto-service-loader').publishing.repositories.configureEach { repository ->
        repository.url = uri('${publicationRepository.toURI()}')
    }
}
"""

        def result = runner(':auto-service-loader:publish', '--init-script', initScript.absolutePath)
                .buildAndFail()

        assertTrue(
                "无凭据发布应给出明确的本地错误：\n${result.output}",
                result.output.contains('发布到私有 Maven 仓库需要 ALIYUN_USERNAME 和 ALIYUN_PASSWORD'))
        assertFalse('凭据校验失败前不应写入发布仓库', publicationRepository.exists())
        assertFalse('发布失败日志不应输出 Maven 用户名', result.output.contains('Maven Username:'))
    }

    @Test
    void sourcesJarIncludesMainAndPluginEntrySources() {
        def result = runner(':auto-service-loader:sourcesJar').build()
        assertTrue(result.output.contains('BUILD SUCCESSFUL'))

        File sourcesJar = new File(testProjectDir, 'auto-service-loader/build/libs/auto-service-loader-0.0.13-sources.jar')
        assertTrue('sources JAR 应该生成', sourcesJar.isFile())
        new ZipFile(sourcesJar).withCloseable { archive ->
            assertTrue('sources JAR 应包含 main 源码', archive.getEntry('sample/Main.java') != null)
            assertTrue('sources JAR 应包含 pluginEntry 源码', archive.getEntry('sample/PluginEntry.java') != null)
        }
    }

    private static GradleRunner runner(String... arguments) {
        Map<String, String> environment = new LinkedHashMap<>(System.getenv())
        environment.remove('ALIYUN_USERNAME')
        environment.remove('ALIYUN_PASSWORD')
        List<String> completeArguments = arguments as List
        completeArguments.addAll([
                '-PALIYUN_USERNAME=',
                '-PALIYUN_PASSWORD=',
                '--stacktrace',
                '--console=plain'
        ])
        GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withEnvironment(environment)
                .withArguments(completeArguments)
    }

    private static File findProjectRoot() {
        File directory = new File(System.getProperty('user.dir')).canonicalFile
        while (directory != null && !new File(directory, 'settings.gradle').isFile()) {
            directory = directory.parentFile
        }
        assert directory != null: '找不到项目根目录'
        directory
    }

    private static void createMinimalFixture(File sourceRoot, File destinationRoot) {
        Files.copy(
                new File(sourceRoot, 'build.gradle').toPath(),
                new File(destinationRoot, 'build.gradle').toPath(),
                StandardCopyOption.REPLACE_EXISTING)
        Files.copy(
                new File(sourceRoot, 'maven_publish.gradle').toPath(),
                new File(destinationRoot, 'maven_publish.gradle').toPath(),
                StandardCopyOption.REPLACE_EXISTING)
        new File(destinationRoot, 'settings.gradle').text = '''
rootProject.name = 'publication-configuration-test'
include ':app', ':auto-service-loader'
'''
        new File(destinationRoot, 'gradle.properties').text = '''
org.gradle.jvmargs=-Xmx1024m -Dfile.encoding=UTF-8
VERSION=0.0.13
'''
        File appDirectory = new File(destinationRoot, 'app')
        appDirectory.mkdirs()
        new File(appDirectory, 'build.gradle').text = '''
plugins { id 'java' }
tasks.register('assembleDebug') { dependsOn classes }
'''
        File loaderDirectory = new File(destinationRoot, 'auto-service-loader')
        loaderDirectory.mkdirs()
        new File(loaderDirectory, 'build.gradle').text = '''
plugins { id 'java-library' }
apply from: '../maven_publish.gradle'
group = 'com.anymore'
version = VERSION

sourceSets {
    pluginEntry {
        java.srcDirs = ['src/pluginEntry/java']
    }
}
'''
        File mainSource = new File(loaderDirectory, 'src/main/java/sample/Main.java')
        mainSource.parentFile.mkdirs()
        mainSource.text = '''
package sample;
public final class Main {}
'''
        File pluginEntrySource = new File(loaderDirectory, 'src/pluginEntry/java/sample/PluginEntry.java')
        pluginEntrySource.parentFile.mkdirs()
        pluginEntrySource.text = '''
package sample;
public final class PluginEntry {}
'''
    }

}
