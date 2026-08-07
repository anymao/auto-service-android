package com.anymore.auto.gradle

import org.gradle.api.GradleException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

import static org.junit.Assert.assertArrayEquals
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertThrows
import static org.junit.Assert.assertTrue

class DeterministicJarWriterTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder()

    @Test
    void '不同输入迭代顺序生成完全相同的jar和固定时间戳'() {
        File firstJar = jar('first.jar', [
                'sample/Zebra.class': [3] as byte[],
                'sample/Alpha.class': [1] as byte[]
        ])
        File secondJar = jar('second.jar', ['sample/Middle.class': [2] as byte[]])
        File directory = temporaryFolder.newFolder('classes')
        write(directory, 'sample/Directory.class', [4] as byte[])
        File generated = temporaryFolder.newFolder('generated')
        write(generated, 'com/anymore/auto/ServiceRegistry.class', [9] as byte[])

        File firstOutput = new File(temporaryFolder.root, 'first-output.jar')
        File secondOutput = new File(temporaryFolder.root, 'second-output.jar')
        DeterministicJarWriter writer = new DeterministicJarWriter()
        writer.write([firstJar, secondJar], [directory], generated, firstOutput)
        writer.write([secondJar, firstJar], [directory], generated, secondOutput)

        assertArrayEquals(firstOutput.bytes, secondOutput.bytes)
        new JarFile(firstOutput).withCloseable { JarFile archive ->
            assertEquals(0L, archive.getJarEntry('sample/Directory.class').time)
        }
    }

    @Test
    void '输入中的保留占位类由生成类唯一替换'() {
        File input = temporaryFolder.newFolder('input')
        write(input, 'com/anymore/auto/ServiceRegistry.class', [1] as byte[])
        write(input, 'sample/Task.class', [2] as byte[])
        File generated = temporaryFolder.newFolder('generated-reserved')
        byte[] replacement = [8, 9] as byte[]
        write(generated, 'com/anymore/auto/ServiceRegistry.class', replacement)
        File output = new File(temporaryFolder.root, 'replacement.jar')

        new DeterministicJarWriter().write([], [input], generated, output)

        new JarFile(output).withCloseable { JarFile archive ->
            assertArrayEquals(replacement, archive.getInputStream(
                    archive.getJarEntry('com/anymore/auto/ServiceRegistry.class')).bytes)
            assertEquals(2, archive.entries().findAll { !it.directory }.size())
        }
    }

    @Test
    void '不同容器中的普通重复类明确失败'() {
        File first = jar('duplicate-first.jar', ['sample/Duplicate.class': [1] as byte[]])
        File second = jar('duplicate-second.jar', ['sample/Duplicate.class': [2] as byte[]])
        File generated = temporaryFolder.newFolder('generated-empty')

        GradleException exception = assertThrows(GradleException) {
            new DeterministicJarWriter().write(
                    [first, second], [], generated,
                    new File(temporaryFolder.root, 'duplicate-output.jar'))
        }

        assertTrue(exception.message.contains('duplicate-first.jar'))
        assertTrue(exception.message.contains('duplicate-second.jar'))
        assertTrue(exception.message.contains('sample/Duplicate.class'))
    }

    private File jar(String name, Map<String, byte[]> entries) {
        File jar = new File(temporaryFolder.root, name)
        new JarOutputStream(new FileOutputStream(jar)).withCloseable { output ->
            entries.each { String entryName, byte[] bytes ->
                output.putNextEntry(new JarEntry(entryName))
                output.write(bytes)
                output.closeEntry()
            }
        }
        jar
    }

    private static void write(File root, String relativePath, byte[] bytes) {
        File file = new File(root, relativePath)
        file.parentFile.mkdirs()
        file.bytes = bytes
    }
}
