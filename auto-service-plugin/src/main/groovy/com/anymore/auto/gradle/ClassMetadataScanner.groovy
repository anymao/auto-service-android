package com.anymore.auto.gradle

import javassist.bytecode.AnnotationsAttribute
import javassist.bytecode.ClassFile
import javassist.bytecode.annotation.ArrayMemberValue
import javassist.bytecode.annotation.BooleanMemberValue
import javassist.bytecode.annotation.ClassMemberValue
import javassist.bytecode.annotation.IntegerMemberValue
import javassist.bytecode.annotation.StringMemberValue
import org.gradle.api.GradleException

import java.security.MessageDigest
import java.util.jar.JarEntry
import java.util.jar.JarFile

/**
 * 直接读取 class 元数据，避免为了发现服务而加载用户类及其依赖。
 */
final class ClassMetadataScanner {
    private static final String AUTO_SERVICE_ANNOTATION = 'com.anymore.auto.AutoService'

    private final Set<ExclusiveRule> exclusiveRules

    ClassMetadataScanner(Set<ExclusiveRule> exclusiveRules) {
        this.exclusiveRules = exclusiveRules == null
                ? Collections.emptySet()
                : new LinkedHashSet<>(exclusiveRules)
    }

    ServiceCatalog scan(Collection<File> inputs) {
        ServiceCatalog catalog = new ServiceCatalog()
        inputs.findAll { it != null && it.exists() }
                .sort { File left, File right -> left.absolutePath <=> right.absolutePath }
                .each { File input ->
                    if (input.directory) {
                        scanDirectory(catalog, input)
                    } else if (input.name.endsWith('.jar')) {
                        scanJar(catalog, input)
                    }
                }
        catalog
    }

    private void scanDirectory(ServiceCatalog catalog, File root) {
        List<File> classFiles = []
        root.eachFileRecurse { File file ->
            if (file.file && file.name.endsWith('.class')) {
                classFiles.add(file)
            }
        }
        classFiles.sort { File left, File right ->
            relativeEntry(root, left) <=> relativeEntry(root, right)
        }.each { File classFile ->
            String entryName = relativeEntry(root, classFile)
            scanClass(catalog, root.name, entryName, classFile.bytes)
        }
    }

    private void scanJar(ServiceCatalog catalog, File input) {
        new JarFile(input).withCloseable { JarFile jar ->
            List<JarEntry> entries = jar.entries().findAll { JarEntry entry ->
                !entry.directory && entry.name.endsWith('.class')
            }.toList().sort { JarEntry left, JarEntry right -> left.name <=> right.name }
            entries.each { JarEntry entry ->
                jar.getInputStream(entry).withCloseable { InputStream stream ->
                    scanClass(catalog, input.name, entry.name, stream.bytes)
                }
            }
        }
    }

    private void scanClass(ServiceCatalog catalog, String containerName, String entryName, byte[] bytes) {
        if (entryName == 'module-info.class' || entryName.endsWith('/module-info.class')) {
            return
        }
        try {
            ClassFile classFile = new DataInputStream(new ByteArrayInputStream(bytes)).withCloseable {
                new ClassFile(it)
            }
            ClassOrigin origin = new ClassOrigin(
                    classFile.name,
                    containerName,
                    entryName,
                    sha256(bytes))
            catalog.addClass(origin)

            def annotation = findAutoServiceAnnotation(classFile)
            if (annotation == null) {
                return
            }

            String implementationClassName = classFile.name
            int priority = readPriority(annotation)
            String alias = readAlias(annotation)
            boolean singleton = readSingleton(annotation)
            ExclusiveRule rule = matchExcludeRule(implementationClassName, alias)
            ServiceCandidateStatus status = rule == null
                    ? ServiceCandidateStatus.REGISTERED
                    : ServiceCandidateStatus.EXCLUDED
            String exclusionRule = rule == null
                    ? null
                    : "className=${rule.className}, alias=${rule.alias}"

            readServiceClassNames(annotation).each { String serviceClassName ->
                catalog.addCandidate(new ServiceCandidate(
                        serviceClassName,
                        implementationClassName,
                        priority,
                        alias,
                        singleton,
                        status,
                        exclusionRule,
                        origin))
            }
        } catch (GradleException exception) {
            throw exception
        } catch (Exception exception) {
            throw new GradleException(
                    "无法解析 class 元数据：${containerName}!/${entryName}",
                    exception)
        }
    }

    private static def findAutoServiceAnnotation(ClassFile classFile) {
        AnnotationsAttribute visible = (AnnotationsAttribute) classFile.getAttribute(AnnotationsAttribute.visibleTag)
        def annotation = visible?.getAnnotation(AUTO_SERVICE_ANNOTATION)
        if (annotation != null) {
            return annotation
        }
        AnnotationsAttribute invisible = (AnnotationsAttribute) classFile.getAttribute(AnnotationsAttribute.invisibleTag)
        invisible?.getAnnotation(AUTO_SERVICE_ANNOTATION)
    }

    private static List<String> readServiceClassNames(def annotation) {
        def value = annotation.getMemberValue('value')
        if (!(value instanceof ArrayMemberValue)) {
            throw new GradleException("@AutoService.value 元数据格式无效")
        }
        ((ArrayMemberValue) value).value.collect { member ->
            if (!(member instanceof ClassMemberValue)) {
                throw new GradleException("@AutoService.value 只能包含类")
            }
            ((ClassMemberValue) member).value
        }
    }

    private static int readPriority(def annotation) {
        def value = annotation.getMemberValue('priority')
        value instanceof IntegerMemberValue ? ((IntegerMemberValue) value).value : 0
    }

    private static String readAlias(def annotation) {
        def value = annotation.getMemberValue('alias')
        value instanceof StringMemberValue ? ((StringMemberValue) value).value : ''
    }

    private static boolean readSingleton(def annotation) {
        def value = annotation.getMemberValue('singleton')
        value instanceof BooleanMemberValue && ((BooleanMemberValue) value).value
    }

    private ExclusiveRule matchExcludeRule(String implementationClassName, String alias) {
        exclusiveRules.find { ExclusiveRule rule ->
            implementationClassName.matches(rule.className) && alias.matches(rule.alias)
        }
    }

    private static String relativeEntry(File root, File file) {
        root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/' as char)
    }

    private static String sha256(byte[] bytes) {
        MessageDigest.getInstance('SHA-256')
                .digest(bytes)
                .collect { String.format('%02x', it & 0xff) }
                .join()
    }
}
