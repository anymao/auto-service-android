package com.anymore.auto.gradle

import org.gradle.api.GradleException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import javax.tools.ToolProvider
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertThrows
import static org.junit.Assert.assertTrue

class ClassMetadataScannerTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder()

    @Test
    void '目录和jar中的运行时注解生成相同候选项'() {
        File classes = compileSources([
                'sample/DebugTask.java': '''
package sample;
import com.anymore.auto.AutoService;
@AutoService(value = { Runnable.class }, priority = -5, alias = "debug", singleton = true)
public final class DebugTask implements Runnable {
    public void run() {}
}
'''
        ])
        File jar = jarClasses(classes, 'services.jar')

        [classes, jar].each { File input ->
            ServiceCatalog catalog = new ClassMetadataScanner(Collections.emptySet()).scan([input])
            assertEquals(1, catalog.registeredFor('java.lang.Runnable').size())
            ServiceCandidate candidate = catalog.registeredFor('java.lang.Runnable')[0]

            assertEquals('sample.DebugTask', candidate.implementationClassName)
            assertEquals(-5, candidate.priority)
            assertEquals('debug', candidate.alias)
            assertTrue(candidate.singleton)
        }
    }

    @Test
    void '类保留注解也能被扫描并使用默认值'() {
        File classes = compileSources([
                'com/anymore/auto/AutoService.java': '''
package com.anymore.auto;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface AutoService {
    Class<?>[] value();
    int priority() default 0;
    String alias() default "";
    boolean singleton() default false;
}
''',
                'sample/BinaryTask.java': '''
package sample;
import com.anymore.auto.AutoService;
@AutoService(Runnable.class)
public final class BinaryTask implements Runnable {
    public void run() {}
}
'''
        ])

        List<ServiceCandidate> candidates = new ClassMetadataScanner(Collections.emptySet())
                .scan([classes])
                .registeredFor('java.lang.Runnable')
        assertEquals(1, candidates.size())
        ServiceCandidate candidate = candidates[0]

        assertEquals('sample.BinaryTask', candidate.implementationClassName)
        assertEquals(0, candidate.priority)
        assertEquals('', candidate.alias)
        assertEquals(false, candidate.singleton)
    }

    @Test
    void '排除规则保留候选项和命中原因'() {
        File classes = compileSources([
                'sample/LegacyTask.java': '''
package sample;
import com.anymore.auto.AutoService;
@AutoService(value = Runnable.class, alias = "legacy")
public final class LegacyTask implements Runnable {
    public void run() {}
}
'''
        ])
        def rules = [new ExclusiveRule('sample\\.LegacyTask', 'legacy')] as Set

        ServiceCatalog catalog = new ClassMetadataScanner(rules).scan([classes])

        assertTrue(catalog.registeredFor('java.lang.Runnable').empty)
        assertEquals(1, catalog.excludedCandidates().size())
        ServiceCandidate excluded = catalog.excludedCandidates()[0]
        assertEquals(ServiceCandidateStatus.EXCLUDED, excluded.status)
        assertTrue(excluded.exclusionRule.contains('sample\\.LegacyTask'))
    }

    @Test
    void '损坏class报告来源而不是原始解析异常'() {
        File classes = temporaryFolder.newFolder('broken-classes')
        File broken = new File(classes, 'sample/Broken.class')
        broken.parentFile.mkdirs()
        broken.bytes = [0x01, 0x02, 0x03] as byte[]

        GradleException exception = assertThrows(GradleException) {
            new ClassMetadataScanner(Collections.emptySet()).scan([classes])
        }

        assertTrue(exception.message.contains('broken-classes'))
        assertTrue(exception.message.contains('sample/Broken.class'))
    }

    private File compileSources(Map<String, String> sources) {
        File sourceRoot = temporaryFolder.newFolder('src-' + UUID.randomUUID())
        File classes = temporaryFolder.newFolder('classes-' + UUID.randomUUID())
        List<File> sourceFiles = sources.collect { String path, String content ->
            File source = new File(sourceRoot, path)
            source.parentFile.mkdirs()
            source.text = content
            source
        }
        List<String> arguments = [
                '-classpath', System.getProperty('java.class.path'),
                '-d', classes.absolutePath
        ]
        arguments.addAll(sourceFiles*.absolutePath)
        int result = ToolProvider.systemJavaCompiler.run(
                null,
                null,
                null,
                arguments as String[])
        assertEquals('测试夹具编译失败', 0, result)
        classes
    }

    private File jarClasses(File classes, String name) {
        File jar = new File(temporaryFolder.root, name)
        new JarOutputStream(new FileOutputStream(jar)).withCloseable { output ->
            classes.eachFileRecurse { File file ->
                if (file.file) {
                    String entryName = classes.toPath().relativize(file.toPath())
                            .toString()
                            .replace(File.separatorChar, '/' as char)
                    JarEntry entry = new JarEntry(entryName)
                    entry.time = 0L
                    output.putNextEntry(entry)
                    output.write(file.bytes)
                    output.closeEntry()
                }
            }
        }
        jar
    }
}
