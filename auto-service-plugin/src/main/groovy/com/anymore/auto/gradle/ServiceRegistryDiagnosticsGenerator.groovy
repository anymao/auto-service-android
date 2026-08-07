package com.anymore.auto.gradle

import com.squareup.javapoet.ClassName
import com.squareup.javapoet.CodeBlock
import com.squareup.javapoet.FieldSpec
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeSpec

import javax.lang.model.element.Modifier

/** 按变体生成结构化服务诊断实现。 */
final class ServiceRegistryDiagnosticsGenerator {
    private static final String PACKAGE_NAME = 'com.anymore.auto'
    private static final ClassName ENTRY = ClassName.get(PACKAGE_NAME, 'ServiceDiagnosticEntry')
    private static final ClassName REPORT = ClassName.get(PACKAGE_NAME, 'ServiceDiagnosticReport')
    private static final ClassName AVAILABILITY = ClassName.get(PACKAGE_NAME, 'ServiceDiagnosticAvailability')
    private static final ClassName STATUS = ClassName.get(PACKAGE_NAME, 'ServiceDiagnosticStatus')

    JavaFile generate(ServiceCatalog catalog, boolean diagnosticsEnabled) {
        TypeSpec.Builder type = TypeSpec.classBuilder('ServiceRegistryDiagnostics')
                .addJavadoc('Automatically generated file by auto-service. DO NOT MODIFY')
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addMethod(diagnosticsEnabled ? debugGetMethod() : releaseGetMethod())
        if (diagnosticsEnabled) {
            addDebugEntries(type, catalog)
        }
        JavaFile.builder(PACKAGE_NAME, type.build()).build()
    }

    File write(ServiceCatalog catalog, File targetDirectory, boolean diagnosticsEnabled) {
        targetDirectory.mkdirs()
        generate(catalog, diagnosticsEnabled).writeToFile(targetDirectory)
    }

    private static void addDebugEntries(TypeSpec.Builder type, ServiceCatalog catalog) {
        def listOfEntries = ParameterizedTypeName.get(ClassName.get(List.class), ENTRY)
        def mapType = ParameterizedTypeName.get(
                ClassName.get(Map.class), ClassName.get(String.class), listOfEntries)
        type.addField(FieldSpec.builder(
                mapType,
                'ENTRIES_BY_SERVICE',
                Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL).build())

        CodeBlock.Builder staticBlock = CodeBlock.builder()
                .addStatement('$T entries = new $T<>()', mapType, LinkedHashMap.class)
        catalog.candidatesByService().each { String serviceClassName, List<ServiceCandidate> candidates ->
            CodeBlock.Builder values = CodeBlock.builder()
            candidates.eachWithIndex { ServiceCandidate candidate, int index ->
                if (index > 0) values.add(', ')
                values.add('new $T($S, $L, $S, $L, $T.$L, $L)',
                        ENTRY,
                        candidate.implementationClassName,
                        candidate.priority,
                        candidate.alias,
                        candidate.singleton,
                        STATUS,
                        candidate.status.name(),
                        candidate.exclusionRule == null
                                ? CodeBlock.of('null')
                                : CodeBlock.of('$S', candidate.exclusionRule))
            }
            staticBlock.addStatement('entries.put($S, $T.unmodifiableList($T.asList($L)))',
                    serviceClassName, Collections.class, Arrays.class, values.build())
        }
        staticBlock.addStatement('ENTRIES_BY_SERVICE = $T.unmodifiableMap(entries)', Collections.class)
        type.addStaticBlock(staticBlock.build())
    }

    private static MethodSpec debugGetMethod() {
        def entryList = ParameterizedTypeName.get(ClassName.get(List.class), ENTRY)
        MethodSpec.methodBuilder('get')
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(REPORT)
                .addParameter(ParameterizedTypeName.get(
                        ClassName.get(Class.class),
                        com.squareup.javapoet.WildcardTypeName.subtypeOf(Object.class)), 'clazz')
                .addParameter(String.class, 'alias')
                .addStatement('$T normalizedAlias = alias == null ? $S : alias', String.class, '')
                .addStatement('$T entries = ENTRIES_BY_SERVICE.getOrDefault(clazz.getName(), $T.<$T>emptyList())',
                        entryList, Collections.class, ENTRY)
                .addStatement('return new $T(clazz.getName(), normalizedAlias, $T.AVAILABLE, entries)',
                        REPORT, AVAILABILITY)
                .build()
    }

    private static MethodSpec releaseGetMethod() {
        MethodSpec.methodBuilder('get')
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(REPORT)
                .addParameter(ParameterizedTypeName.get(
                        ClassName.get(Class.class),
                        com.squareup.javapoet.WildcardTypeName.subtypeOf(Object.class)), 'clazz')
                .addParameter(String.class, 'alias')
                .addStatement('return new $T(clazz.getName(), alias == null ? $S : alias, ' +
                        '$T.UNAVAILABLE_IN_NON_DEBUG_BUILD, $T.<$T>emptyList())',
                        REPORT, '', AVAILABILITY, Collections.class, ENTRY)
                .build()
    }
}
