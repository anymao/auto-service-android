package com.anymore.auto.gradle

import org.gradle.api.GradleException

import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

/** 将 AGP 的 class 输入合并为字节级稳定的输出 JAR。 */
final class DeterministicJarWriter {

    void write(
            Collection<File> inputJars,
            Collection<File> inputDirectories,
            File generatedClassesDirectory,
            File outputFile) {
        SortedMap<String, EntryData> entries = new TreeMap<>()
        inputJars.findAll { it?.isFile() }
                .sort { File left, File right -> left.absolutePath <=> right.absolutePath }
                .each { File input -> collectJar(input, entries) }
        inputDirectories.findAll { it?.isDirectory() }
                .sort { File left, File right -> left.absolutePath <=> right.absolutePath }
                .each { File input -> collectDirectory(input, entries, false) }
        if (generatedClassesDirectory?.isDirectory()) {
            collectDirectory(generatedClassesDirectory, entries, true)
        }

        outputFile.parentFile.mkdirs()
        new JarOutputStream(new BufferedOutputStream(new FileOutputStream(outputFile))).withCloseable { output ->
            entries.each { String name, EntryData data ->
                JarEntry entry = new JarEntry(name)
                entry.method = ZipEntry.DEFLATED
                entry.time = 0L
                output.putNextEntry(entry)
                output.write(data.bytes)
                output.closeEntry()
            }
        }
    }

    private static void collectJar(File input, SortedMap<String, EntryData> entries) {
        new JarFile(input).withCloseable { JarFile archive ->
            archive.entries().findAll { JarEntry entry ->
                !entry.directory && entry.name.endsWith('.class')
            }.toList().sort { JarEntry left, JarEntry right -> left.name <=> right.name }
                    .each { JarEntry entry ->
                        if (!isReservedEntry(entry.name)) {
                            archive.getInputStream(entry).withCloseable { stream ->
                                addOrdinary(entries, entry.name, stream.bytes, input.name)
                            }
                        }
                    }
        }
    }

    private static void collectDirectory(
            File root,
            SortedMap<String, EntryData> entries,
            boolean generated) {
        List<File> files = []
        root.eachFileRecurse { File file ->
            if (file.file && file.name.endsWith('.class')) files.add(file)
        }
        files.sort { File left, File right ->
            relativeName(root, left) <=> relativeName(root, right)
        }.each { File file ->
            String name = relativeName(root, file)
            if (generated) {
                if (!isReservedEntry(name)) {
                    throw new GradleException("生成目录包含非框架保留类：${name}")
                }
                entries.put(name, new EntryData(file.bytes, "generated!/${name}"))
            } else if (!isReservedEntry(name)) {
                addOrdinary(entries, name, file.bytes, root.name)
            }
        }
    }

    private static void addOrdinary(
            SortedMap<String, EntryData> entries,
            String name,
            byte[] bytes,
            String containerName) {
        EntryData existing = entries.get(name)
        if (existing != null) {
            throw new GradleException(
                    "发现重复类 ${entryToClassName(name)}：\n" +
                            "  - ${existing.origin}\n" +
                            "  - ${containerName}!/${name}\n" +
                            '请解决依赖冲突，auto-service 不会选择其中一个定义。')
        }
        entries.put(name, new EntryData(bytes, "${containerName}!/${name}"))
    }

    static boolean isReservedEntry(String name) {
        name == 'com/anymore/auto/ServiceRegistry.class' ||
                name.startsWith('com/anymore/auto/ServiceRegistry$') ||
                name == 'com/anymore/auto/ServiceRegistryDiagnostics.class' ||
                name.startsWith('com/anymore/auto/ServiceRegistryDiagnostics$')
    }

    private static String relativeName(File root, File file) {
        root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/' as char)
    }

    private static String entryToClassName(String name) {
        name.substring(0, name.length() - '.class'.length()).replace('/', '.')
    }

    private static final class EntryData {
        final byte[] bytes
        final String origin

        EntryData(byte[] bytes, String origin) {
            this.bytes = bytes
            this.origin = origin
        }
    }
}
