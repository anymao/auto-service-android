package com.anymore.auto.gradle


import com.anymore.auto.AutoService
import com.squareup.javapoet.*
import javassist.ClassPool
import javassist.CtClass
import javassist.Loader
import javassist.bytecode.AnnotationsAttribute
import javassist.bytecode.ClassFile
import javassist.bytecode.annotation.*
import org.gradle.api.GradleException
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.TaskAction
import org.jetbrains.annotations.NotNull

import javax.lang.model.element.Modifier
import java.util.function.Function
import java.util.jar.JarFile

/**
 * Created by anymore on 2022/4/3.
 */
class AutoServiceRegisterAction {
    final FileCollection classpath
    final File targetDir
    private final ClassPool classPool
    private final Set<String> requiredServices

    AutoServiceRegisterAction(FileCollection classpath, File targetDir, Set<String> requiredServices) {
        this.classpath = classpath
        this.targetDir = targetDir
        this.requiredServices = requiredServices
        classPool = new ClassPool(true) {
            @Override
            ClassLoader getClassLoader() {
                return new Loader(this)
            }
        }
    }

    private List<CtClass> load() {
        final result = new LinkedList<CtClass>()
        classpath.each {
            classPool.appendClassPath(it.getAbsolutePath())
        }
        classpath.each {
            load(result, classPool, it)
        }
        return result
    }

    private void load(List<CtClass> result, ClassPool pool, File file) {
        if (file.isDirectory()) {
            file.listFiles().each {
                load(result, pool, it)
            }
        } else {
            if (file.name.endsWith(".class")) {
                loadClass(result, pool, file)
            } else if (file.name.endsWith(".jar")) {
                loadJar(result, pool, file)
            }
        }
    }

    private void loadClass(List<CtClass> result, ClassPool pool, File file) {
        new FileInputStream(file).withCloseable {
            result.add(pool.makeClass(it))
        }
    }

    private void loadJar(List<CtClass> result, ClassPool pool, File file) {
        final jarFile = new JarFile(file)
        jarFile.entries().asIterator().each {
            if (it.name.endsWith(".class")) {
                jarFile.getInputStream(it).withCloseable {
                    result.add(pool.makeClass(it))
                }
            }
        }
    }

    private Map<String, Collection<Element>> loadAutoServices(List<CtClass> classes) {
        final result = new HashMap<String, Queue<Element>>()
        classes.each { ctClass ->
            if (ctClass.hasAnnotation(AutoService.class)) {
                final autoServiceAnnotation = getAnnotation(ctClass.classFile, AutoService.class)
                if (autoServiceAnnotation == null) {
                    return
                }
                final serviceClasses = (ArrayMemberValue) autoServiceAnnotation.getMemberValue("value")
                final pm = ((IntegerMemberValue) autoServiceAnnotation.getMemberValue("priority"))
                int p
                if (pm != null) {
                    p = pm.value
                } else {
                    p = 0
                }
                final priority = p
                final am = (StringMemberValue) autoServiceAnnotation.getMemberValue("alias")
                String a
                if (am != null) {
                    a = am.value
                } else {
                    a = ""
                }
                final alias = a
                serviceClasses.value.each { mv ->
                    final cm = (ClassMemberValue) mv
                    result.computeIfAbsent(cm.value, new Function<String, Queue<Element>>() {
                        @Override
                        Queue<Element> apply(String s) {
                            return new PriorityQueue<Element>()
                        }
                    }).offer(Element.create(ctClass.name, priority, alias))
                }
            }
        }
        return result
    }

    private Annotation getAnnotation(ClassFile classFile, Class<?> clazz) {
        final visibleAttr = (AnnotationsAttribute) classFile.getAttribute(AnnotationsAttribute.visibleTag)
        if (visibleAttr != null) {
            final autoService = visibleAttr.getAnnotation(clazz.name)
            if (autoService != null) return autoService
        }
        final invisibleAttr = (AnnotationsAttribute) classFile.getAttribute(AnnotationsAttribute.invisibleTag)
        if (invisibleAttr != null) {
            return invisibleAttr.getAnnotation(clazz.name)
        }
        return null
    }

    private void makeServiceRegistryFile(Map<String, Queue<Element>> elements) {
        if (targetDir.exists()) {
            targetDir.mkdirs()
        }
        createServiceRegistry(elements)
                .writeTo(targetDir)
    }

    private JavaFile createServiceRegistry(Map<String, Queue<Element>> elements) {
        final pkg = "com.anymore.auto"
        //Class<?>
        final WildcardTypeName anyType = WildcardTypeName.subtypeOf(Object.class)
        final TypeVariableName typeOfS = TypeVariableName.get("S")
        //ServiceFactory
        final ClassName serviceSupplierClassName = ClassName.get(pkg, "ServiceSupplier")

        final serviceCreatorsField = FieldSpec.builder(
                ParameterizedTypeName.get(ClassName.get(Map.class),
                        ParameterizedTypeName.get(ClassName.get(Class.class), anyType),
                        ParameterizedTypeName.get(ClassName.get(List.class), ParameterizedTypeName.get(
                                serviceSupplierClassName, anyType
                        ))
                ),
                "serviceSuppliers",
                Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL
        ).initializer("new \$T<>()", LinkedHashMap.class)
                .build()
        final function = TypeSpec.anonymousClassBuilder("")
                .addSuperinterface(
                        ParameterizedTypeName.get(
                                ClassName.get(Function.class),
                                ParameterizedTypeName.get(ClassName.get(Class.class), anyType),
                                ParameterizedTypeName.get(ClassName.get(List.class), ParameterizedTypeName.get(serviceSupplierClassName, anyType))
                        )
                )
                .addMethod(
                        MethodSpec.methodBuilder("apply")
                                .addModifiers(Modifier.PUBLIC)
                                .addAnnotation(Override.class)
                                .addParameter(ParameterizedTypeName.get(ClassName.get(Class.class), anyType), "c")
                                .addStatement("return new \$T<>()", LinkedList.class)
                                .returns(ParameterizedTypeName.get(ClassName.get(List.class), ParameterizedTypeName.get(serviceSupplierClassName, anyType)))
                                .build()
                ).build()
        final registerMethod = MethodSpec.methodBuilder("register")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.SYNCHRONIZED)
                .addTypeVariable(typeOfS)
                .addParameter(
                        ParameterSpec.builder(
                                ParameterizedTypeName.get(ClassName.get(Class.class), typeOfS),
                                "clazz"
                        ).build()
                )
                .addParameter(
                        ParameterSpec.builder(
                                ParameterizedTypeName.get(serviceSupplierClassName, typeOfS),
                                "supplier"
                        ).build()
                )
                .addStatement("serviceSuppliers.computeIfAbsent(\$N,\$L).add(supplier)", "clazz", function)
                .returns(TypeName.VOID)
                .build()

        final getMethod = MethodSpec.methodBuilder("get")
                .addModifiers(Modifier.STATIC, Modifier.SYNCHRONIZED)
                .addTypeVariable(typeOfS)
                .addParameter(ParameterSpec.builder(
                        ParameterizedTypeName.get(ClassName.get(Class.class), typeOfS),
                        "clazz"
                ).build())
                .addParameter(ParameterSpec.builder(String.class, "alias").build())
                .addCode(CodeBlock.builder()
                        .addStatement("final \$T<\$T<\$T>> allSuppliers = serviceSuppliers.getOrDefault(clazz, new \$T<\$T<\$T>>())",
                                List.class, serviceSupplierClassName, anyType, ArrayList.class, serviceSupplierClassName, anyType
                        )
                        .addStatement("final \$T<\$T<\$T>> suppliers = new \$T<>()", List.class, serviceSupplierClassName, anyType, LinkedList.class)
                        .add(CodeBlock.builder()
                                .beginControlFlow("if (alias != null && alias.length() > 0)")
                                .beginControlFlow("for (\$T<\$T> supplier:allSuppliers)", serviceSupplierClassName, anyType)
                                .beginControlFlow("if (supplier.getAlias() == alias)")
                                .addStatement("suppliers.add(supplier)")
                                .endControlFlow()
                                .endControlFlow()
                                .nextControlFlow("else")
                                .addStatement("suppliers.addAll(allSuppliers)")
                                .endControlFlow()
                                .build())
                        .addStatement("final \$T<\$T> services = new \$T<>(suppliers.size())",
                                List.class, typeOfS, ArrayList.class
                        )
                        .beginControlFlow("if (!suppliers.isEmpty())")
                        .beginControlFlow("for (\$T<\$T> supplier : suppliers)", serviceSupplierClassName, anyType)
                        .beginControlFlow("try")
                        .addStatement("services.add((\$T) supplier.get())", typeOfS)
                        .nextControlFlow("catch (\$T e)", Exception.class)
                        .addStatement("throw new \$T(\$T.format(\"create class %s error!\", clazz.getCanonicalName()), e)", ServiceConfigurationError.class, String.class)
                        .endControlFlow()
                        .endControlFlow()
                        .endControlFlow()
                        .addStatement("return \$T.unmodifiableList(services)", Collections.class)
                        .build()
                )
                .returns(ParameterizedTypeName.get(ClassName.get(List.class), typeOfS))
                .build()

        final staticRegisterCode = CodeBlock.builder().with { builder ->
            elements.each { entry ->
                final serviceType = ClassName.bestGuess(entry.key)
                final queue = entry.value
                while (!queue.isEmpty()) {
                    final element = queue.poll()
                    final implType = ClassName.bestGuess(element.name)
                    final callable = TypeSpec.anonymousClassBuilder("\$S", element.alias)
                            .addSuperinterface(ParameterizedTypeName.get(serviceSupplierClassName, serviceType))
                            .addMethod(
                                    MethodSpec.methodBuilder("get")
                                            .addAnnotation(Override.class)
                                            .addModifiers(Modifier.PUBLIC)
                                            .addStatement("return new \$T()", implType)
                                            .returns(serviceType)
                                            .build()
                            ).build()
                    builder.addStatement("register(\$T.class,\$L)", serviceType, callable)
                }
            }
            builder.build()
        }

        final serviceRegistry = TypeSpec.classBuilder("ServiceRegistry")
                .addJavadoc("Automatically generated file by auto-service. DO NOT MODIFY")
                .addModifiers(Modifier.FINAL)
                .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).addMember("value", "\$S", "unchecked").build())
                .addField(serviceCreatorsField)
                .addStaticBlock(staticRegisterCode)
                .addMethod(registerMethod)
                .addMethod(getMethod)
                .build()
        return JavaFile.builder(pkg, serviceRegistry)
                .build()
    }

    private Collection<String> preCheckRequiredServices(Set<String> services, Set<String> implementedServices) {
        final result = new LinkedList<String>()
        services.each {
            if (!implementedServices.contains(it)) {
                result.add(it)
            }
        }
        return result
    }


    @TaskAction
    boolean execute() throws Exception {
        final startTime = System.currentTimeMillis()
        final result = loadAutoServices(load())
        if (!requiredServices.isEmpty()) {
            final checkResult = preCheckRequiredServices(requiredServices, result.keySet())
            if (!checkResult.isEmpty()) {
                checkResult.each {
                    Logger.e("require service $it but has no implementation,please check!")
                }
                throw new GradleException("please check autoService required service!")
            }
        }
        makeServiceRegistryFile(result)
        Logger.tell("used time:${(System.currentTimeMillis() - startTime) / 1000}s")
        return true
    }

    static class Element implements Comparable<Element> {
        final String name
        final int priority
        final String alias

        Element(String name, int priority, String alias) {
            this.name = name
            this.priority = priority
            this.alias = alias
        }

        static Element create(String name, int priority, String alias) {
            return new Element(name, priority, alias)
        }

        @Override
        int compareTo(@NotNull Element o) {
            if (priority == o.priority) {
                return name <=> o.name
            } else {
                return priority <=> o.priority
            }
        }


        @Override
        String toString() {
            return "Element{" +
                    "name='" + name + '\'' +
                    ", priority=" + priority +
                    ", alias='" + alias + '\'' +
                    '}';
        }
    }
}
