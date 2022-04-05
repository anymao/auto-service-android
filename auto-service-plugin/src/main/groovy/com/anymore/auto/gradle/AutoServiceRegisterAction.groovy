package com.anymore.auto.gradle

import com.anymore.auto.AutoService
import com.squareup.javapoet.*
import javassist.ClassPool
import javassist.CtClass
import javassist.Loader
import javassist.bytecode.AnnotationsAttribute
import javassist.bytecode.ClassFile
import javassist.bytecode.annotation.Annotation
import javassist.bytecode.annotation.ArrayMemberValue
import javassist.bytecode.annotation.ClassMemberValue
import javassist.bytecode.annotation.IntegerMemberValue
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.TaskAction
import org.jetbrains.annotations.NotNull

import javax.lang.model.element.Modifier
import java.util.concurrent.Callable
import java.util.function.Function
import java.util.jar.JarFile

/**
 * Created by anymore on 2022/4/3.
 */
class AutoServiceRegisterAction {
    final FileCollection classpath
    final File targetDir
    private final ClassPool classPool

    AutoServiceRegisterAction(FileCollection classpath, File targetDir) {
        this.classpath = classpath
        this.targetDir = targetDir
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
                serviceClasses.value.each { mv ->
                    final cm = (ClassMemberValue) mv
                    result.computeIfAbsent(cm.value, new Function<String, Queue<Element>>() {
                        @Override
                        Queue<Element> apply(String s) {
                            return new PriorityQueue<Element>()
                        }
                    }).offer(Element.create(ctClass.name, priority))
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

        final serviceCreatorsField = FieldSpec.builder(
                ParameterizedTypeName.get(ClassName.get(Map.class),
                        ParameterizedTypeName.get(ClassName.get(Class.class), anyType),
                        ParameterizedTypeName.get(ClassName.get(List.class), ParameterizedTypeName.get(
                                ClassName.get(Callable.class), anyType
                        ))
                ),
                "serviceCreators",
                Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL
        ).initializer("new \$T<>()", LinkedHashMap.class)
                .build()
        final function = TypeSpec.anonymousClassBuilder("")
                .addSuperinterface(
                        ParameterizedTypeName.get(
                                ClassName.get(Function.class),
                                ParameterizedTypeName.get(ClassName.get(Class.class), anyType),
                                ParameterizedTypeName.get(ClassName.get(List.class), ParameterizedTypeName.get(ClassName.get(Callable.class), anyType))
                        )
                )
                .addMethod(
                        MethodSpec.methodBuilder("apply")
                                .addModifiers(Modifier.PUBLIC)
                                .addAnnotation(Override.class)
                                .addParameter(ParameterizedTypeName.get(ClassName.get(Class.class), anyType), "c")
                                .addStatement("return new \$T<>()", LinkedList.class)
                                .returns(ParameterizedTypeName.get(ClassName.get(List.class), ParameterizedTypeName.get(ClassName.get(Callable.class), anyType)))
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
                                ParameterizedTypeName.get(ClassName.get(Callable.class), typeOfS),
                                "creator"
                        ).build()
                )
                .addStatement("serviceCreators.computeIfAbsent(\$N,\$L).add(creator)", "clazz", function)
                .returns(TypeName.VOID)
                .build()

        final getMethod = MethodSpec.methodBuilder("get")
                .addModifiers(Modifier.STATIC, Modifier.SYNCHRONIZED)
                .addTypeVariable(typeOfS)
                .addParameter(ParameterSpec.builder(
                        ParameterizedTypeName.get(ClassName.get(Class.class), typeOfS),
                        "clazz"
                ).build())
                .addCode(CodeBlock.builder()
                        .addStatement("final \$T<\$T<?>> creators = serviceCreators.getOrDefault(clazz, new \$T<\$T<?>>())",
                                List.class, Callable.class, ArrayList.class, Callable.class
                        )
                        .addStatement("final \$T<\$T> services = new \$T<>(creators.size())",
                                List.class, typeOfS, ArrayList.class
                        )
                        .beginControlFlow("if (!creators.isEmpty())")
                        .beginControlFlow("for (\$T<?> creator : creators)", Callable.class)
                        .beginControlFlow("try")
                        .addStatement("services.add((\$T) creator.call())", typeOfS)
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
                Logger.tell(String.format("Service:%s", serviceType.toString()))
                while (!queue.isEmpty()) {
                    final element = queue.poll()
                    final implType = ClassName.bestGuess(element.name)
                    Logger.tell(String.format("Impls:%s", implType.toString()))
                    final callable = TypeSpec.anonymousClassBuilder("")
                            .addSuperinterface(ParameterizedTypeName.get(ClassName.get(Callable), serviceType))
                            .addMethod(
                                    MethodSpec.methodBuilder("call")
                                            .addAnnotation(Override.class)
                                            .addModifiers(Modifier.PUBLIC)
                                            .addException(Exception.class)
                                            .addStatement("return new \$T()", implType)
                                            .returns(serviceType)
                                            .build()
                            ).build()
                    builder.addStatement("register(\$T.class,\$L)", serviceType, callable)
                }
                Logger.tell("------------------------------------------------")
            }
            builder.build()
        }

        final serviceRegistry = TypeSpec.classBuilder("ServiceRegistry")
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


    @TaskAction
    boolean execute() throws Exception {
        final startTime = System.currentTimeMillis()
        final result = loadAutoServices(load())
        makeServiceRegistryFile(result)
        Logger.tell("use time:${(System.currentTimeMillis() - startTime) / 1000}s")
        return true
    }

    static class Element implements Comparable<Element> {
        final String name
        final int priority

        Element(String name, int priority) {
            this.name = name
            this.priority = priority
        }

        static Element create(String name, int priority) {
            return new Element(name, priority)
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
                    '}';
        }
    }
}
