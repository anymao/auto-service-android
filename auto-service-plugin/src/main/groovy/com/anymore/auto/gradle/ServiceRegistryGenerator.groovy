package com.anymore.auto.gradle

import com.squareup.javapoet.AnnotationSpec
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.CodeBlock
import com.squareup.javapoet.FieldSpec
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterSpec
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeSpec
import com.squareup.javapoet.TypeVariableName
import com.squareup.javapoet.WildcardTypeName

import javax.lang.model.element.Modifier
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Function
import java.util.function.Supplier

/** 生成服务注册表，不读取构建环境。 */
final class ServiceRegistryGenerator {

    JavaFile generate(ServiceCatalog catalog) {
        final String pkg = 'com.anymore.auto'
        final WildcardTypeName anyType = WildcardTypeName.subtypeOf(Object.class)
        final TypeVariableName typeOfS = TypeVariableName.get('S')
        final ClassName serviceSupplierClassName = ClassName.get(pkg, 'ServiceSupplier')
        final ClassName singletonServiceSupplierClassName = ClassName.get(pkg, 'SingletonServiceSupplier')
        final ClassName serviceLazyClassName = ClassName.get(pkg, 'ServiceLazy')

        FieldSpec serviceSuppliers = FieldSpec.builder(
                ParameterizedTypeName.get(
                        ClassName.get(Map.class),
                        ParameterizedTypeName.get(ClassName.get(Class.class), anyType),
                        ParameterizedTypeName.get(
                                ClassName.get(List.class),
                                ParameterizedTypeName.get(serviceSupplierClassName, anyType))),
                'serviceSuppliers',
                Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer('new $T<>()', LinkedHashMap.class)
                .build()

        TypeSpec newListFunction = TypeSpec.anonymousClassBuilder('')
                .addSuperinterface(ParameterizedTypeName.get(
                        ClassName.get(Function.class),
                        ParameterizedTypeName.get(ClassName.get(Class.class), anyType),
                        ParameterizedTypeName.get(
                                ClassName.get(List.class),
                                ParameterizedTypeName.get(serviceSupplierClassName, anyType))))
                .addMethod(MethodSpec.methodBuilder('apply')
                        .addModifiers(Modifier.PUBLIC)
                        .addAnnotation(Override.class)
                        .addParameter(ParameterizedTypeName.get(ClassName.get(Class.class), anyType), 'clazz')
                        .addStatement('return new $T<>()', LinkedList.class)
                        .returns(ParameterizedTypeName.get(
                                ClassName.get(List.class),
                                ParameterizedTypeName.get(serviceSupplierClassName, anyType)))
                        .build())
                .build()

        MethodSpec register = MethodSpec.methodBuilder('register')
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.SYNCHRONIZED)
                .addTypeVariable(typeOfS)
                .addParameter(ParameterizedTypeName.get(ClassName.get(Class.class), typeOfS), 'clazz')
                .addParameter(ParameterizedTypeName.get(serviceSupplierClassName, typeOfS), 'supplier')
                .addStatement('serviceSuppliers.computeIfAbsent(clazz, $L).add(supplier)', newListFunction)
                .returns(TypeName.VOID)
                .build()

        MethodSpec get = MethodSpec.methodBuilder('get')
                .addModifiers(Modifier.STATIC, Modifier.SYNCHRONIZED)
                .addTypeVariable(typeOfS)
                .addParameter(ParameterSpec.builder(
                        ParameterizedTypeName.get(ClassName.get(Class.class), typeOfS), 'clazz').build())
                .addParameter(String.class, 'alias')
                .addCode(CodeBlock.builder()
                        .addStatement('final $T<$T<$T>> allSuppliers = serviceSuppliers.getOrDefault(clazz, new $T<$T<$T>>())',
                                List.class, serviceSupplierClassName, anyType,
                                ArrayList.class, serviceSupplierClassName, anyType)
                        .addStatement('final $T<$T<$T>> suppliers = new $T<>()',
                                List.class, serviceSupplierClassName, anyType, LinkedList.class)
                        .beginControlFlow('if (alias != null && alias.length() > 0)')
                        .beginControlFlow('for ($T<$T> supplier : allSuppliers)', serviceSupplierClassName, anyType)
                        .beginControlFlow('if ($T.equals(supplier.getAlias(), alias))', Objects.class)
                        .addStatement('suppliers.add(supplier)')
                        .endControlFlow()
                        .endControlFlow()
                        .nextControlFlow('else')
                        .addStatement('suppliers.addAll(allSuppliers)')
                        .endControlFlow()
                        .addStatement('final $T<$T> services = new $T<>(suppliers.size())',
                                List.class,
                                ParameterizedTypeName.get(singletonServiceSupplierClassName, typeOfS),
                                ArrayList.class)
                        .beginControlFlow('for ($T<$T> supplier : suppliers)', serviceSupplierClassName, anyType)
                        .addStatement('final $T<$T> realSupplier = ($T<$T>) supplier.getSupplier()',
                                Supplier.class, typeOfS, Supplier.class, typeOfS)
                        .beginControlFlow('if (realSupplier instanceof $T)', singletonServiceSupplierClassName)
                        .addStatement('services.add(($T) realSupplier)', singletonServiceSupplierClassName)
                        .nextControlFlow('else')
                        .addStatement('services.add(new $T(realSupplier))', serviceLazyClassName)
                        .endControlFlow()
                        .endControlFlow()
                        .addStatement('return $T.unmodifiableList(services)', Collections.class)
                        .build())
                .returns(ParameterizedTypeName.get(
                        ClassName.get(List.class),
                        ParameterizedTypeName.get(singletonServiceSupplierClassName, typeOfS)))
                .build()

        CodeBlock staticRegistration = registrationCode(catalog, serviceSupplierClassName, singletonServiceSupplierClassName)
        TypeSpec registry = TypeSpec.classBuilder('ServiceRegistry')
                .addJavadoc('Automatically generated file by auto-service. DO NOT MODIFY')
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class)
                        .addMember('value', '$S', 'unchecked')
                        .build())
                .addField(serviceSuppliers)
                .addStaticBlock(staticRegistration)
                .addMethod(register)
                .addMethod(get)
                .build()
        JavaFile.builder(pkg, registry).build()
    }

    File write(ServiceCatalog catalog, File targetDirectory) {
        targetDirectory.mkdirs()
        generate(catalog).writeToFile(targetDirectory)
    }

    private static CodeBlock registrationCode(
            ServiceCatalog catalog,
            ClassName serviceSupplierClassName,
            ClassName singletonServiceSupplierClassName) {
        CodeBlock.Builder builder = CodeBlock.builder()
        Map<ClassName, String> singletonSuppliers = new LinkedHashMap<>()
        AtomicLong supplierCounter = new AtomicLong()
        catalog.registeredByService().each { String serviceClassName, List<ServiceCandidate> candidates ->
            ClassName serviceType = ClassName.bestGuess(serviceClassName)
            candidates.each { ServiceCandidate candidate ->
                ClassName implementationType = ClassName.bestGuess(candidate.implementationClassName)
                if (!candidate.singleton) {
                    TypeSpec supplier = TypeSpec.anonymousClassBuilder('')
                            .addSuperinterface(ParameterizedTypeName.get(ClassName.get(Supplier.class), implementationType))
                            .addMethod(MethodSpec.methodBuilder('get')
                                    .addAnnotation(Override.class)
                                    .addModifiers(Modifier.PUBLIC)
                                    .addStatement('return new $T()', implementationType)
                                    .returns(implementationType)
                                    .build())
                            .build()
                    builder.addStatement('register($T.class, new $T<$T>($S, $L))',
                            serviceType, serviceSupplierClassName, serviceType, candidate.alias, supplier)
                    return
                }

                String supplierName = singletonSuppliers.get(implementationType)
                if (supplierName == null) {
                    TypeSpec singletonSupplier = TypeSpec.anonymousClassBuilder('')
                            .addSuperinterface(ParameterizedTypeName.get(singletonServiceSupplierClassName, implementationType))
                            .addMethod(MethodSpec.methodBuilder('newInstance')
                                    .addAnnotation(Override.class)
                                    .addModifiers(Modifier.PUBLIC)
                                    .addStatement('return new $T()', implementationType)
                                    .returns(implementationType)
                                    .build())
                            .build()
                    supplierName = "supplier${supplierCounter.getAndIncrement()}"
                    builder.addStatement('final $T<$T> $N = $L',
                            Supplier.class, implementationType, supplierName, singletonSupplier)
                    singletonSuppliers.put(implementationType, supplierName)
                }
                builder.addStatement('register($T.class, new $T<$T>($S, $N))',
                        serviceType, serviceSupplierClassName, serviceType, candidate.alias, supplierName)
            }
        }
        builder.build()
    }
}
