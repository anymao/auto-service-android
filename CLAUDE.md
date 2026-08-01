# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Development Requirements

- **JDK**: 11 或更高版本（annotation 模块使用 Java 8，loader 和 plugin 模块使用 Java 11）
- **Gradle**: 8.13
- **Android Gradle Plugin**: 8.13.0
- **Kotlin**: 2.1.0

## Project Overview

auto-service-android 是一个 Android 服务加载框架，类似于 Google AutoService，但在编译期生成代码注册实现类，避免运行时反射。它支持：
- 接口实现的优先级排序
- 单例模式（懒加载，双重检查锁定线程安全）
- 编译期预检查
- 别名机制
- 排除规则

当前版本：**0.0.11**（定义在 `gradle.properties` 的 `VERSION` 属性中）。

## Build Commands

```bash
# 清理构建
./gradlew clean

# 构建 app 模块
./gradlew :app:assembleDebug

# 发布库到 Maven 仓库（需要设置 ALIYUN_USERNAME 和 ALIYUN_PASSWORD 环境变量）
# 项目已迁移到 maven-publish 插件，uploadArchives 任务委托到 publish
./gradlew :auto-service-annotation:publish
./gradlew :auto-service-loader:publish
./gradlew :auto-service-plugin:publish

# 或使用兼容的 uploadArchives 别名
./gradlew :auto-service-annotation:uploadArchives
```

## 当前分支说明

当前分支 `feature/agp8.13.0` 将 AGP 从 7.4.0 升级到 8.13.0。主要变更：
- 插件中使用 `variant.javaCompileProvider` 替代已废弃的 `variant.javaCompile`
- 编译输出目录适配 AGP 8.x 的新路径结构
- Gradle 版本从 7.5 升级到 8.13

## Developing and Debugging auto-service-plugin

在开发和调试 auto-service-plugin 模块时，推荐使用 **buildSrc 模式**以实现快速迭代，无需每次都发布到 Maven 仓库。

> **注意**：`buildSrc/build.gradle.bak` 中的依赖版本（Kotlin 1.8.10、auto-service-annotation 0.0.9）可能需要与当前项目版本同步更新后才能正常使用。

### 启用 buildSrc 开发模式

1. **修改 buildSrc 目录下 build.gradle.bak 为 build.gradle**
   ```bash
   mv buildSrc/build.gradle.bak buildSrc/build.gradle
   ```

2. **注释掉根目录下 build.gradle 中的插件依赖**
   ```groovy
   // dependencies {
   //     classpath("com.anymore:auto-service-register:0.0.11")
   // }
   ```

3. **app/build.gradle 文件中，注释掉 `id 'auto-service'` 引用**
   ```groovy
   // plugins {
   //     id 'auto-service'
   // }
   ```

4. **app/build.gradle 文件中，直接应用插件类**
   ```groovy
   import com.anymore.auto.gradle.AutoServiceRegisterPlugin
   apply plugin: AutoServiceRegisterPlugin
   ```

### 开发迭代流程

```
1. 修改 auto-service-plugin/src/main/groovy 中的代码
2. ./gradlew clean
3. ./gradlew :app:assembleDebug  (插件会自动编译)
4. 验证 ServiceRegistry.java 是否正确生成
5. 循环以上步骤
```

### 恢复到模块模式（开发完成后）

1. 将 `buildSrc/build.gradle` 重命名回 `build.gradle.bak`
   ```bash
   mv buildSrc/build.gradle buildSrc/build.gradle.bak
   ```

2. 恢复根目录 build.gradle 中的插件依赖
   ```groovy
   dependencies {
       classpath("com.anymore:auto-service-register:0.0.11")
   }
   ```

3. 恢复 app/build.gradle 中的插件引用
   ```groovy
   plugins {
       id 'auto-service'
   }
   ```

### 调试技巧

**启用详细日志**：
```groovy
autoService {
    logLevel = "VERBOSE"  // 可选: DEBUG, INFO, WARN, ERROR
}
```

**查看 Task 执行情况**：
```bash
./gradlew :app:tasks --all
./gradlew :app:tasks --group=build
./gradlew :app:assembleDebug --dry-run
```

**验证插件是否加载**：
```bash
./gradlew projects
# 应该看到 autoService 扩展已注册
```

### buildSrc vs 模块模式对比

| 特性 | buildSrc 模式 | 模块模式 |
|------|----------------|----------|
| 构建速度 | 快（修改立即生效） | 较慢（需先构建插件） |
| 发布流程 | 需要额外配置步骤 | 标准流程 |
| 调试便利性 | 高 | 中 |
| 适用场景 | 日常开发调试 | 发布准备、CI/CD |

## Module Architecture

项目由四个主要模块组成：

### 1. auto-service-annotation
注解模块，定义 `@AutoService` 注解。使用 Java 8 编译目标。包含属性：
- `value`: 服务接口类（vararg，支持一个实现对应多个接口）
- `priority`: 优先级（值越小越靠前，默认 0）
- `alias`: 别名（默认空字符串）
- `singleton`: 是否单例（默认 false）

### 2. auto-service-loader
运行时 API 模块，提供 `ServiceLoader.load<T>()` 方法加载服务实现。使用 Java 11 编译目标。包含：
- `ServiceLoader`: 核心加载器，实现 `Iterable<T>`，支持 `load(clazz, alias)` 按优先级和别名加载，提供 `firstPriority`/`lastPriority`/`requireFirstPriority()`/`requireLastPriority()` 便捷方法
- `SingletonServiceSupplier`: 单例服务提供者（DCL 线程安全懒加载），抽象类，子类需实现 `newInstance()`
- `ServiceSupplier`: 服务提供者包装类，持有别名和 `Supplier` 委托
- `ServiceLazy`: 继承 `SingletonServiceSupplier`，用于包装非单例实现以统一迭代器接口

### 3. auto-service-plugin
Gradle 插件模块，在编译期扫描所有 `@AutoService` 注解并生成 `ServiceRegistry.java`。使用 Java 11 编译目标。核心类：
- `AutoServiceRegisterPlugin`: 插件入口，注册到 application 模块，监听 `applicationVariants`
- `AutoServiceRegisterTask`: Gradle Task，持有 classpath、targetDir、排除规则等配置，委托给 Action 执行
- `AutoServiceRegisterAction`: 核心扫描逻辑 — 遍历 classpath 中的 .class 和 .jar 文件，用 Javassist 解析注解，按 PriorityQueue 排序，用 JavaPoet 生成注册代码，执行编译预检查
- `AutoServiceExtension`: 配置扩展，支持 `checkImplementation`、`sourceCompatibility`、`logLevel`、`require()`、`excludeClassName()`、`excludeAlias()`、`exclude()` 等配置
- `ExclusiveRule`: 排除规则数据类，支持 className 和 alias 的正则匹配
- `Logger`: 插件日志工具，支持 VERBOSE/DEBUG/INFO/WARN/ERROR 五个级别

### 4. auto-service-registry
运行时的 `ServiceRegistry` 存根模块。`settings.gradle` 中已注释掉此模块的源码编译，改为通过 Maven 发布 `0.0.7-SNAPSHOT`。其 `ServiceRegistry` 类仅包含抛异常的存根方法，实际实现由插件生成的代码在编译期替换。

## Code Generation Flow

1. 用户在实现类上添加 `@AutoService(Interface::class)` 注解
2. Java 编译完成后，`AutoServiceRegisterTask` 扫描 classpath 中的所有 .class 和 .jar 文件
3. 使用 Javassist 的 `ClassPool` 加载类字节码，解析 `@AutoService` 注解的 `value`、`priority`、`alias`、`singleton` 属性
4. 按接口分组，每组内用 `PriorityQueue` 按 priority 排序（值越小越靠前，同 priority 按类名字典序）
5. 使用 JavaPoet 生成 `ServiceRegistry.java`，包含：
   - 静态 `serviceSuppliers` Map（Class → List<ServiceSupplier>）
   - 静态初始化块中的 `register()` 调用
   - `get(clazz, alias)` 方法：支持按别名过滤，包装为 `SingletonServiceSupplier` 或 `ServiceLazy`
6. 将生成的 `ServiceRegistry.java` 编译并打包到 APK 的 dex 中

生成的 `ServiceRegistry.java` 位置：`build/intermediates/auto_service/{variant}/src/com/anymore/auto/ServiceRegistry.java`

## Plugin Configuration

在 application 模块的 build.gradle 中配置：

```groovy
autoService {
    checkImplementation = false       // 开启编译预检查（默认 false）
    sourceCompatibility = "1.8"       // 生成代码的 Java 编译版本（默认 "1.7"）
    logLevel = "VERBOSE"              // 日志级别（默认 INFO）
    require(Runnable.class.name)      // 要求必须实现的接口
    require(Runnable.class.name, "alias")  // 要求接口的特定别名实现
    excludeAlias("lym23")             // 排除特定别名的实现（支持正则）
    excludeClassName("com\\.anymore\\..*")  // 排除特定类名模式（支持正则）
    exclude("com\\.anymore\\..*", "lym.*")  // 同时按类名和别名排除
}
```

## Key Implementation Details

- **优先级排序**: 通过 `PriorityQueue` 在生成代码时按 priority 排序，值越小越靠前
- **单例实现**: `SingletonServiceSupplier` 使用双重检查锁定（DCL）+ `@Volatile` 实现线程安全懒加载
- **非单例包装**: 非单例实现通过 `ServiceLazy` 包装为 `SingletonServiceSupplier` 子类，统一迭代器接口
- **别名机制**: 支持同一接口的不同实现通过 alias 区分，加载时可通过 `ServiceLoader.load(Interface::class, "alias")` 获取特定实现。`ServiceLoader.get()` 方法中 alias 为空时返回全部实现
- **排除规则**: `excludeClassName` 和 `excludeAlias` 支持正则表达式匹配，在扫描阶段过滤掉匹配的实现类
- **发布配置**: `maven_publish.gradle` 使用 `maven-publish` 插件，自动根据版本号是否含 `SNAPSHOT` 选择 release/snapshot 仓库

<!-- gitnexus:start -->
# GitNexus — Code Intelligence

This project is indexed by GitNexus as **auto-service-android** (685 symbols, 688 relationships, 1 execution flows). Use the GitNexus MCP tools to understand code, assess impact, and navigate safely.

> Index stale? Run `node .gitnexus/run.cjs analyze` from the project root — it auto-selects an available runner. No `.gitnexus/run.cjs` yet? `npx gitnexus analyze` (npm 11 crash → `npm i -g gitnexus`; #1939).

## Always Do

- **MUST run impact analysis before editing any symbol.** Before modifying a function, class, or method, run `impact({target: "symbolName", direction: "upstream"})` and report the blast radius (direct callers, affected processes, risk level) to the user.
- **MUST run `detect_changes()` before committing** to verify your changes only affect expected symbols and execution flows. For regression review, compare against the default branch: `detect_changes({scope: "compare", base_ref: "master"})`.
- **MUST warn the user** if impact analysis returns HIGH or CRITICAL risk before proceeding with edits.
- When exploring unfamiliar code, use `query({search_query: "concept"})` to find execution flows instead of grepping. It returns process-grouped results ranked by relevance.
- When you need full context on a specific symbol — callers, callees, which execution flows it participates in — use `context({name: "symbolName"})`.
- For security review, `explain({target: "fileOrSymbol"})` lists taint findings (source→sink flows; needs `analyze --pdg`).

## Never Do

- NEVER edit a function, class, or method without first running `impact` on it.
- NEVER ignore HIGH or CRITICAL risk warnings from impact analysis.
- NEVER rename symbols with find-and-replace — use `rename` which understands the call graph.
- NEVER commit changes without running `detect_changes()` to check affected scope.

## Resources

| Resource | Use for |
|----------|---------|
| `gitnexus://repo/auto-service-android/context` | Codebase overview, check index freshness |
| `gitnexus://repo/auto-service-android/clusters` | All functional areas |
| `gitnexus://repo/auto-service-android/processes` | All execution flows |
| `gitnexus://repo/auto-service-android/process/{name}` | Step-by-step execution trace |

## CLI

| Task | Read this skill file |
|------|---------------------|
| Understand architecture / "How does X work?" | `.claude/skills/gitnexus/gitnexus-exploring/SKILL.md` |
| Blast radius / "What breaks if I change X?" | `.claude/skills/gitnexus/gitnexus-impact-analysis/SKILL.md` |
| Trace bugs / "Why is X failing?" | `.claude/skills/gitnexus/gitnexus-debugging/SKILL.md` |
| Rename / extract / split / refactor | `.claude/skills/gitnexus/gitnexus-refactoring/SKILL.md` |
| Tools, resources, schema reference | `.claude/skills/gitnexus/gitnexus-guide/SKILL.md` |
| Index, status, clean, wiki CLI commands | `.claude/skills/gitnexus/gitnexus-cli/SKILL.md` |

<!-- gitnexus:end -->
