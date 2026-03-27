# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Development Requirements

- **JDK**: 11 或更高版本（AGP 7.4.0 要求）
- **Gradle**: 7.5
- **Android Gradle Plugin**: 7.4.0
- **Kotlin**: 1.8.10

## Project Overview

auto-service-android 是一个 Android 服务加载框架，类似于 Google AutoService，但在编译期生成代码注册实现类，避免运行时反射。它支持：
- 接口实现的优先级排序
- 单例模式（懒加载）
- 编译期预检查
- 别名机制
- 排除规则

## Build Commands

```bash
# 清理构建
./gradlew clean

# 构建 app 模块
./gradlew :app:assembleDebug

# 发布库到 Maven 仓库（需要设置 ALIYUN_USERNAME 和 ALIYUN_PASSWORD 环境变量）
./gradlew :auto-service-annotation:uploadArchives
./gradlew :auto-service-loader:uploadArchives
./gradlew :auto-service-plugin:uploadArchives
```

## Module Architecture

项目由四个主要模块组成：

### 1. auto-service-annotation
注解模块，定义 `@AutoService` 注解。包含属性：
- `value`: 服务接口类
- `priority`: 优先级（值越小越靠前）
- `alias`: 别名
- `singleton`: 是否单例

### 2. auto-service-loader
运行时 API 模块，提供 `ServiceLoader.load<T>()` 方法加载服务实现。包含：
- `ServiceLoader`: 核心加载器，支持按优先级和别名加载
- `SingletonServiceSupplier`: 单例服务提供者（线程安全懒加载）
- `ServiceSupplier`: 服务提供者包装类

### 3. auto-service-plugin
Gradle 插件模块，在编译期扫描所有 `@AutoService` 注解并生成 `ServiceRegistry.java`。核心类：
- `AutoServiceRegisterPlugin`: 插件入口，注册到 application 模块
- `AutoServiceRegisterAction`: 扫描 class/jar 文件，解析注解，生成注册代码
- `AutoServiceExtension`: 配置扩展，支持预检查、排除规则等

### 4. auto-service-registry
运行时的 `ServiceRegistry` 存根模块，实际实现由插件生成的代码提供。

## Code Generation Flow

1. 用户在实现类上添加 `@AutoService(Interface::class)` 注解
2. 编译完成后，`AutoServiceRegisterTask` 扫描 classpath 中的所有 .class 和 .jar 文件
3. 使用 Javassist 解析 `@AutoService` 注解信息
4. 使用 JavaPoet 生成 `ServiceRegistry.java`，包含静态注册代码
5. 编译生成的 `ServiceRegistry.java` 并打包到 APK

## Plugin Configuration

在 application 模块的 build.gradle 中配置：

```groovy
autoService {
    checkImplementation = false       // 开启编译预检查
    sourceCompatibility = "1.8"       // Java 编译版本
    logLevel = "VERBOSE"              // 日志级别
    require(Runnable.class.name)      // 要求必须实现的接口
    excludeAlias("lym23")             // 排除特定别名的实现
    excludeClassName("com\\.anymore\\..*")  // 排除特定类名模式
}
```

## Key Implementation Details

- **优先级排序**: 通过 `PriorityQueue` 在生成代码时按 priority 排序
- **单例实现**: `SingletonServiceSupplier` 使用双重检查锁定（DCL）实现线程安全
- **别名机制**: 支持同一接口的不同实现通过 alias 区分，加载时可通过 `ServiceLoader.load(Interface::class, "alias")` 获取特定实现
- **排除规则**: `excludeClassName` 和 `excludeAlias` 支持正则表达式匹配