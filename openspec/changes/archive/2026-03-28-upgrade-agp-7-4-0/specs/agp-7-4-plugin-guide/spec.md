# AGP 7.4.0 自定义插件开发指南

本文档记录 Android Gradle Plugin 7.4.0 的 API 变化和自定义插件开发流程。

## 官方文档

- [Android Gradle Plugin 7.4.0 Release Notes](https://developer.android.com/studio/releases/gradle-plugin)
- [Migrate to Android Gradle Plugin 7.x](https://developer.android.com/build/migrating-to-gradle-7)
- [Gradle Plugin API Documentation](https://developer.android.com/build/publish/gradgrad-plugin-api)

## 1. 环境要求

| 要求 | AGP 4.2.2 | AGP 7.4.0 |
|------|-----------|-----------|
| 最低 Gradle 版本 | 6.7.1 | 7.2 |
| 推荐 Gradle 版本 | 6.7.1 | 7.5 |
| 最低 JDK 版本 | JDK 8 | JDK 11 |
| Kotlin 版本 | 1.5.31+ | 1.8.10+ |

## 2. API 变化概览

### 2.1 Extension 类名变更

**AGP 4.x:**
```groovy
import com.android.build.gradle.AppExtension
import com.android.build.gradle.LibraryExtension
```

**AGP 7.x:**
```groovy
import com.android.build.gradle.AppExtension           // 仍然可用，兼容性别名
import com.android.build.gradle.ApplicationExtension    // 新推荐类名
import com.android.build.gradle.LibraryExtension       // 保持不变
```

**说明**: `AppExtension` 在 AGP 7.x 中是 `ApplicationExtension` 的别名/子类，两者都可用。推荐使用 `ApplicationExtension` 以获得更好的类型提示和未来兼容性。

### 2.2 DSL 语法变更

| 旧语法 (AGP 4.x) | 新语法 (AGP 7.x) | 说明 |
|------------------|------------------|------|
| `compileSdkVersion 33` | `compileSdk 33` | 方法名简化 |
| `targetSdkVersion 33` | `targetSdk 33` | 方法名简化 |
| `minSdkVersion 21` | `minSdk 21` | 方法名简化 |
| `buildToolsVersion "33.0.0"` | 可选，可删除 | AGP 7.x 自动管理 |

**示例：**
```groovy
// AGP 4.x 语法
android {
    compileSdkVersion 33
    buildToolsVersion "30.0.3"
    defaultConfig {
        targetSdkVersion 30
        minSdkVersion 17
    }
}

// AGP 7.x 语法
android {
    compileSdk 33
    // buildToolsVersion 不再需要
    defaultConfig {
        targetSdk 33
        minSdk 17
    }
}
```

### 2.3 核心插件 API 兼容性

以下 API 在 AGP 7.x 中**完全兼容**，无需修改代码：

| API | AGP 4.x | AGP 7.x | 兼容性 |
|-----|---------|---------|--------|
| `AppPlugin` | `com.android.build.gradle.AppPlugin` | `com.android.build.gradle.AppPlugin` | ✅ 完全兼容 |
| `applicationVariants` | `DomainObjectSet<ApplicationVariant>` | `DomainObjectSet<ApplicationVariant>` | ✅ 完全兼容 |
| `javaCompileProvider.classpath` | `FileCollection` | `FileCollection` | ✅ 完全兼容 |
| `javaCompileProvider.destinationDir` | `File` | `File` | ✅ 完全兼容 |
| `bootClasspath` | `List<File>` | `List<File>` | ✅ 完全兼容 |
| `assembleProvider` | `TaskProvider` | `TaskProvider` | ✅ 完全兼容 |

### 2.4 新的 Variant API（可选使用）

AGP 7.x 引入了新的 `AndroidComponentsExtension` API，提供更现代化的接口：

```groovy
import com.android.build.api.AndroidComponentsExtension
import com.android.build.api.variant.ApplicationVariant

// 新 API（推荐用于新项目）
def androidComponents = project.extensions.getByType(AndroidComponentsExtension.class)
androidComponents.onVariants { ApplicationVariant variant ->
    // 使用 Property-based API
    variant.outputs.forEach { output ->
        // 处理输出
    }
}
```

**注意**: 新 API 是可选的，传统 API 仍然完全可用。

## 3. 自定义插件开发流程

### 3.1 创建自定义 Android Gradle 插件

**项目结构：**
```
my-android-plugin/
├── build.gradle
├── src/
│   └── main/
│       ├── groovy/
│       │   └── com/
│       │       └── example/
│       │           └── plugin/
│       │               └── MyCustomPlugin.groovy
│       └── resources/
│           └── META-INF/
│               └── gradle-plugins/
│                   └── my-custom-plugin.properties
```

**插件声明文件：**
```properties
# META-INF/gradle-plugins/my-custom-plugin.properties
implementation-class=com.example.plugin.MyCustomPlugin
```

**插件 build.gradle：**
```groovy
apply plugin: 'groovy'
apply plugin: 'java-gradle-plugin'

repositories {
    google()
    mavenCentral()
}

dependencies {
    compileOnly gradleApi()
    compileOnly localGroovy()
    compileOnly 'com.android.tools.build:gradle:7.4.0'
}

gradlePlugin {
    plugins {
        myPlugin {
            id = 'my-custom-plugin'
            implementationClass = 'com.example.plugin.MyCustomPlugin'
        }
    }
}
```

### 3.2 插件主类实现

```groovy
package com.example.plugin

import com.android.build.gradle.ApplicationExtension
import com.android.build.gradle.AppPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile

class MyCustomPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        // 1. 创建插件扩展（可选）
        def extension = project.extensions.create("myPlugin", MyPluginExtension)

        project.afterEvaluate {
            // 2. 检查是否为 Android 应用模块
            if (!project.plugins.hasPlugin(AppPlugin)) {
                return
            }

            // 3. 获取 Android 扩展
            def android = project.extensions.getByType(ApplicationExtension.class)

            // 4. 遍历所有构建变体
            android.applicationVariants.forEach { variant ->
                configureVariant(project, variant, extension)
            }
        }
    }

    private void configureVariant(Project project, def variant, def extension) {
        // 5. 获取编译任务
        def javaCompile = variant.javaCompileProvider.get()

        // 6. 创建自定义任务
        def myTask = project.tasks.create(
            "myTask${variant.name.capitalize()}",
            MyCustomTask.class
        ) {
            it.variantName = variant.name
            it.outputDir = new File(project.buildDir, "myOutput/${variant.dirName}")
        }

        // 7. 设置任务依赖关系
        myTask.mustRunAfter(javaCompile)
        variant.assembleProvider.get().dependsOn(myTask)
    }
}
```

### 3.3 ApplicationExtension API 使用

```groovy
import com.android.build.gradle.ApplicationExtension

def android = project.extensions.getByType(ApplicationExtension.class)

// 访问 SDK 版本
android.compileSdk = 33

// 访问 buildTools 版本（可选）
android.buildToolsVersion = "33.0.0"

// 访问 defaultConfig
android.defaultConfig {
    applicationId "com.example.app"
    minSdk 21
    targetSdk 33
    versionCode 1
    versionName "1.0"
}

// 访问 buildTypes
android.buildTypes {
    release {
        minifyEnabled true
        proguardFiles getDefaultProguardFile('proguard-android-optimize.txt')
    }
}

// 访问 productFlavors
android.productFlavors {
    dev {
        dimension "environment"
    }
    prod {
        dimension "environment"
    }
}
```

### 3.4 ApplicationVariant API 使用

```groovy
import com.android.build.gradle.api.ApplicationVariant

android.applicationVariants.forEach { ApplicationVariant variant ->
    // Variant 基本信息
    println "Variant name: ${variant.name}"
    println "Variant dir name: ${variant.dirName}"

    // 获取编译任务
    def javaCompile = variant.javaCompileProvider.get()

    // 获取类路径
    def classpath = project.files(
        android.bootClasspath,
        javaCompile.classpath,
        javaCompile.destinationDir
    )

    // 获取输出目录
    def outputDir = javaCompile.destinationDir

    // 获取 assemble 任务
    def assembleTask = variant.assembleProvider.get()
}
```

### 3.5 创建自定义任务

```groovy
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

class MyCustomTask extends DefaultTask {

    String variantName
    File outputDir

    @TaskAction
    void execute() {
        println "Executing custom task for variant: ${variantName}"
        println "Output directory: ${outputDir}"

        // 任务逻辑
        outputDir.mkdirs()
        // ... 处理文件 ...
    }
}
```

### 3.6 任务依赖关系设置

```groovy
// 方式 1: dependsOn（必须在之后执行）
variant.assembleProvider.get().dependsOn(myTask)

// 方式 2: mustRunAfter（推荐，更灵活）
myTask.mustRunAfter(javaCompile)

// 方式 3: finalizedBy（任务完成后执行）
myTask.finalizedBy(cleanupTask)

// 方式 4: shouldRunAfter（软依赖）
myTask.shouldRunAfter(otherTask)
```

## 4. 常见场景

### 4.1 在编译后处理 class 文件

```groovy
class ProcessClassesPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.afterEvaluate {
            if (!project.plugins.hasPlugin(AppPlugin)) return

            def android = project.extensions.getByType(ApplicationExtension.class)

            android.applicationVariants.forEach { variant ->
                def javaCompile = variant.javaCompileProvider.get()

                def processTask = project.tasks.create(
                    "processClasses${variant.name.capitalize()}",
                    ProcessClassesTask.class
                ) {
                    it.inputDir = javaCompile.destinationDir
                    it.outputDir = new File(project.buildDir, "processed/${variant.dirName}")
                }

                processTask.mustRunAfter(javaCompile)
                variant.assembleProvider.get().dependsOn(processTask)
            }
        }
    }
}
```

### 4.2 生成代码并编译

```groovy
class CodeGenPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.afterEvaluate {
            if (!project.plugins.hasPlugin(AppPlugin)) return

            def android = project.extensions.getByType(ApplicationExtension.class)

            android.applicationVariants.forEach { variant ->
                def javaCompile = variant.javaCompileProvider.get()
                def workDir = new File(project.buildDir, "generated/${variant.dirName}")

                def genTask = project.tasks.create(
                    "generateCode${variant.name.capitalize()}",
                    CodeGenTask.class
                ) {
                    it.outputDir = new File(workDir, "src")
                }

                def compileGenTask = project.tasks.create(
                    "compileGeneratedCode${variant.name.capitalize()}",
                    JavaCompile.class
                ) {
                    it.setSource(new File(workDir, "src"))
                    it.setClasspath(javaCompile.classpath)
                    it.setDestinationDir(javaCompile.destinationDir)
                    it.setSourceCompatibility("1.8")
                    it.setTargetCompatibility("1.8")
                }

                genTask.mustRunAfter(javaCompile)
                compileGenTask.mustRunAfter(genTask)
                variant.assembleProvider.get().dependsOn(genTask, compileGenTask)
            }
        }
    }
}
```

### 4.3 使用新的 AndroidComponentsExtension API（AGP 7.x+）

```groovy
import com.android.build.api.AndroidComponentsExtension
import com.android.build.api.variant.ApplicationVariant
import com.android.build.api.variant.Variant

class ModernPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.afterEvaluate {
            def androidComponents = project.extensions.getByType(AndroidComponentsExtension.class)

            androidComponents.onVariants { ApplicationVariant variant ->
                println "Variant: ${variant.name}"

                variant.outputs.forEach { output ->
                    println "Output: ${output.outputType}"
                }
            }
        }
    }
}
```

## 5. 调试和最佳实践

### 5.1 日志输出

```groovy
class MyPlugin implements Plugin<Project> {
    private static final Logger LOG = Logging.getLogger(MyPlugin.class)

    @Override
    void apply(Project project) {
        LOG.lifecycle("Applying MyPlugin to ${project.name}")
        LOG.quiet("Quiet message")
        LOG.info("Info message")
        LOG.debug("Debug message")
    }
}
```

### 5.2 检查插件是否已应用

```groovy
project.afterEvaluate {
    if (!project.plugins.hasPlugin('com.android.application')) {
        return
    }

    if (!project.plugins.hasPlugin(AppPlugin)) {
        return
    }
}
```

### 5.3 使用 gradle.startParameter

```groovy
project.afterEvaluate {
    // 跳过 assemble 任务
    if (gradle.startParameter.taskNames.any { it.contains('assemble') }) {
        // 执行 assemble 相关逻辑
    }
}
```

### 5.4 使用 Provider 惰性计算

```groovy
// 使用 Provider 避免提前计算
def outputDir = project.provider {
    new File(project.buildDir, "output/${variant.dirName}")
}

// 在任务中使用 Provider
task.setProviderOutputDir(outputDir)
```

## 6. 迁移检查清单

### 6.1 环境检查

- [ ] JDK 版本 >= 11
- [ ] Gradle 版本 >= 7.2
- [ ] Kotlin 版本 >= 1.8.0（如果使用 Kotlin）

### 6.2 代码检查

- [ ] `AppExtension` → `ApplicationExtension`（推荐）
- [ ] DSL 语法更新（compileSdk、targetSdk、minSdk）
- [ ] 移除 `buildToolsVersion`（可选）
- [ ] 编译验证：`./gradlew build`

### 6.3 功能检查

- [ ] 插件正常编译
- [ ] 任务正确创建
- [ ] 任务依赖关系正确
- [ ] 生成/处理逻辑正确执行
- [ ] 最终构建成功

## 7. 参考资源

- [Android Gradle Plugin API Reference](https://developer.android.com/reference/tools/gradle-api/7.4)
- [Custom Gradle Plugins for Android](https://developer.android.com/studio/projects/custom-build-plugin)
- [Gradle Plugin Development Guide](https://docs.gradle.org/current/userguide/custom_plugins.html)
