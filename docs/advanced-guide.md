# auto-service-android 进阶指南

本文面向已经完成基础接入的使用者，覆盖排序、实例生命周期、编译期约束、全范围依赖发现和构建缓存。

## 确定服务顺序

同一接口先按 `priority` 从小到大排序，再按实现类全限定名排序。第二排序键让相同输入在不同机器上得到相同注册表。

```kotlin
@AutoService(StartupTask::class, priority = -100)
class DatabaseTask : StartupTask

@AutoService(StartupTask::class, priority = 0)
class AnalyticsTask : StartupTask
```

不要依赖模块声明顺序、依赖解析顺序或 JAR entry 顺序。需要显式先后关系时分配 priority 区间，例如框架任务使用负数，业务任务从 0 开始。

## 多接口共享单例

```kotlin
@AutoService(Runnable::class, java.util.concurrent.Callable::class, singleton = true)
class SharedWorker : Runnable, java.util.concurrent.Callable<Int> {
    override fun run() = Unit
    override fun call() = 1
}

val runnable = ServiceLoader.load<Runnable>().firstPriority
val callable = ServiceLoader.load<java.util.concurrent.Callable<Int>>().firstPriority
check(runnable === callable)
```

生成器按实现类复用 singleton 供应器，因此不同接口获得同一个实例。`singleton=false` 时每个供应器访问都会创建实例；不要用它承载跨调用共享状态。

## 用 `require()` 把缺失提前到构建期

```groovy
autoService {
    checkImplementation = true
    require('com.example.StartupTask')
    require('com.example.RegionProvider', 'china')
}
```

检查基于最终全范围 catalog 执行，项目模块和直接/传递 AAR/JAR 的实现都能满足约束。如果存在候选但全部被排除，错误会列出被排除实现和规则，便于区分“依赖缺失”和“配置过滤”。

`checkImplementation=false` 会跳过所有 `require()`，适合逐步迁移；稳定后应开启，让 `requireFirstPriority()` 的失败尽量前移。

## 组合正则排除规则

```groovy
autoService {
    excludeClassName('com\\.vendor\\.legacy\\..*')
    excludeAlias('experimental-.*')
    exclude('com\\.vendor\\..*', 'fallback')
}
```

- `excludeClassName`：类名匹配即排除；
- `excludeAlias`：alias 匹配即排除；
- `exclude`：类名和 alias 同时匹配才排除。

规则使用 Java 正则语义。优先写窄规则，并用 Debug `diagnose()` 确认 `exclusionRule`，避免 `.*` 误伤全部候选。

## 从所有模块和 AAR/JAR 发现实现

Application 插件通过 AGP `ScopedArtifacts.Scope.ALL` 消费当前变体的 classes，范围包括：

1. Application 自身 Java/Kotlin class；
2. Android/Java/Kotlin 项目模块；
3. 直接 AAR/JAR；
4. Maven POM 带入的传递 AAR/JAR。

推荐结构：

```groovy
// app/build.gradle
plugins {
    id 'com.android.application'
    id 'auto-service'
}
dependencies {
    implementation project(':feature-payments')
    implementation 'com.vendor:service-bridge:1.2.0'
}
```

```groovy
// feature-payments/build.gradle
plugins { id 'com.android.library' }
dependencies {
    compileOnly 'com.anymore:auto-service-annotation:0.0.13'
}
```

Library 模块不应用插件。聚合只在最终 Application 变体执行，避免每个库生成互相覆盖的注册表。

桥接 AAR 若要暴露生产者实现，必须在发布 POM 中声明 `api`/compile 级传递依赖；仅在构建桥接 AAR 时临时引用、但不写入 POM 的依赖无法被消费方发现。

## 重复类策略

全范围扫描先建立 class origin catalog。同名普通 class 来自两个输入时立即失败，错误同时包含两个容器名。这与 Java/Android 编译的重复类语义一致，也避免依赖遍历顺序决定最终实现。

插件只对需要替换的生成保留类采用特殊规则：输入存根被移除，最终 JAR 只写入一份生成实现。

## 构建缓存与可复现输出

注册任务使用 `@CacheableTask`，class 输入按 `@CompileClasspath` 建模。确定性 JAR writer 会：

- 按 entry 名排序；
- 使用固定时间戳和一致的 ZIP 元数据；
- 丢弃输入中的注册表存根并写入生成版本；
- 在相同输入与配置下产生相同 SHA-256。

验证本地缓存：

```bash
./gradlew :app:androidAutoServiceRegisterDebug --build-cache
./gradlew :app:androidAutoServiceRegisterDebug --build-cache
```

第二次应为 `UP-TO-DATE`。删除 app build 输出后再次运行，应可显示 `FROM-CACHE`。修改注解、依赖 class、排除/require 配置、sourceCompatibility 或诊断开关都会使缓存失效。

## Debug/Release 诊断隔离

诊断是否完整取决于 `variant.debuggable`，而不是变体名字。自定义可调试 build type 同样携带完整 entries；不可调试的 `debug` 名变体也不会携带。

Release 仍生成最小 `ServiceRegistryDiagnostics`，用于保持 loader API 可链接，但方法只返回 unavailable 报告。这样应用代码无需变体分支，同时不会把候选类名、alias 和排除规则打包进非调试产物。

## 发布前验证

```bash
./gradlew clean
./gradlew :auto-service-annotation:test :auto-service-registry:test \
  :auto-service-loader:test :auto-service-plugin:test
./gradlew :app:assembleDebug :app:assembleRelease
./gradlew :auto-service-loader:generatePomFileForMavenPublication \
  :auto-service-plugin:generatePomFileForMavenPublication
```

检查 loader POM 同时依赖 `auto-service-annotation:0.0.13` 和 `auto-service-registry:0.0.13`；插件 POM 不应重新引入 annotation 运行时依赖。

遇到异常时转到[故障排查](troubleshooting.md)。公共入口和最小接入见[README](../README.md)。
