# auto-service-android API 参考

本文列出 `0.0.13` 面向应用开发者的稳定 API、Gradle DSL 和生成契约。内部扫描器与生成器不属于兼容性承诺，参见[架构与类关系](architecture.md)。

## 兼容基线与依赖

| 项目 | 值 |
| --- | --- |
| Android Gradle Plugin | 8.13.0 |
| Gradle | 8.13 |
| 构建 JDK | 17 |
| Kotlin Gradle Plugin | 2.1.0 |
| 插件坐标 | `com.anymore:auto-service-register:0.0.13` |
| 运行时坐标 | `com.anymore:auto-service-loader:0.0.13` |
| Gradle 插件 ID | `auto-service` |

`auto-service-loader` 的发布 POM 会传递 `auto-service-annotation` 和 `auto-service-registry`。四个组件必须使用相同版本。

## API 分层

| 分层 | 类型或入口 | 是否建议业务代码直接使用 |
| --- | --- | --- |
| 声明 API | `@AutoService` | 是 |
| 加载 API | `ServiceLoader<T>` | 是 |
| 诊断 API | `ServiceDiagnosticReport`、`ServiceDiagnosticEntry`、两个诊断枚举 | 是，仅观测 |
| Gradle DSL | `autoService { ... }` | 是，仅 Application 模块 |
| 生成契约 | `ServiceRegistry`、`ServiceRegistryDiagnostics` | 否，由插件替换的保留类型 |
| 插件实现 | `com.anymore.auto.gradle.*` | 否，不承诺二进制兼容 |

## `@AutoService`

```kotlin
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class AutoService(
    vararg val value: KClass<*>,
    val priority: Int = 0,
    val alias: String = "",
    val singleton: Boolean = false
)
```

### 参数

| 参数 | 类型 | 默认值 | 语义 |
| --- | --- | --- | --- |
| `value` | `vararg KClass<*>` | 无 | 实现类注册到的一个或多个服务接口。空数组不会产生注册项。 |
| `priority` | `Int` | `0` | 越小越优先；相同值按实现类全限定名升序。 |
| `alias` | `String` | `""` | 精确、区分大小写的分组值。空查询返回所有 alias。 |
| `singleton` | `Boolean` | `false` | 是否让生成注册表为该实现维护跨加载调用共享的惰性单例。 |

### 实现类约束

生成代码直接执行 `new 实现类()`，所以实现类必须：

- 实现 `value` 中声明的每个服务类型；
- 不是抽象类；
- 对生成在 `com.anymore.auto` 包中的注册表可见；
- 提供可访问的无参构造函数。

违反这些约束会在生成源码的 Java 编译阶段失败。

### 多接口示例

```kotlin
@AutoService(
    Runnable::class,
    java.util.concurrent.Callable::class,
    priority = -10,
    alias = "startup",
    singleton = true
)
class SharedTask : Runnable, java.util.concurrent.Callable<Int> {
    override fun run() = Unit
    override fun call(): Int = 1
}
```

`singleton=true` 时，多接口绑定复用同一个供应器和同一个实例。

Java 注解形式：

```java
@AutoService(
    value = {Runnable.class},
    priority = 10,
    alias = "background",
    singleton = false
)
public final class BackgroundTask implements Runnable {
    @Override public void run() {}
}
```

## `ServiceLoader<T>`

`ServiceLoader<T>` 实现 `Iterable<T>`。构造函数是私有的，统一通过 `load()` 创建。

### 加载入口

```kotlin
companion object {
    @JvmStatic
    @JvmOverloads
    fun <T> load(clazz: Class<T>, alias: String = ""): ServiceLoader<T>

    inline fun <reified T> load(alias: String = ""): ServiceLoader<T>
}
```

Kotlin：

```kotlin
val all = ServiceLoader.load<Runnable>()
val startup = ServiceLoader.load<Runnable>("startup")
```

Java：

```java
ServiceLoader<Runnable> all = ServiceLoader.load(Runnable.class);
ServiceLoader<Runnable> startup = ServiceLoader.load(Runnable.class, "startup");
```

`alias` 应传非空引用。Java 调用方要查询全部实现时使用 `""`，不要传 `null`。

### 实例选择 API

| Kotlin API | Java 形式 | 返回或异常 |
| --- | --- | --- |
| `iterator()` | `iterator()` | 按 priority、类名顺序惰性取得实例。末尾继续 `next()` 抛 `NoSuchElementException`。 |
| `firstPriority` | `getFirstPriority()` | 第一个实例；没有实现时为 `null`。 |
| `requireFirstPriority()` | 同名 | 第一个实例；没有实现时抛 `IllegalArgumentException`。 |
| `lastPriority` | `getLastPriority()` | 最后一个实例；没有实现时为 `null`。 |
| `requireLastPriority()` | 同名 | 最后一个实例；没有实现时抛 `IllegalArgumentException`。 |

`requireFirstPriority()` 和 Gradle DSL 的 `require()` 不是同一件事：前者是运行时取值，后者在构建期验证实现是否存在。

### 实例生命周期

| 注解配置 | 生命周期 |
| --- | --- |
| `singleton=true` | 生成注册表保存线程安全的惰性供应器。不同 `load()` 调用、不同接口绑定都返回同一实例。 |
| `singleton=false` | 每次 `load()` 创建新的惰性包装。一个 loader 对象内重复遍历会复用实例；重新调用 `load()` 才会创建新实例。 |

不要缓存短生命周期依赖到 `singleton=true` 的服务中。需要每次业务调用都创建新对象时，应重新调用 `ServiceLoader.load()`，或让服务本身提供工厂方法。

## 诊断 API

### `ServiceLoader.diagnose()`

```kotlin
@JvmStatic
@JvmOverloads
fun diagnose(clazz: Class<*>, alias: String = ""): ServiceDiagnosticReport

inline fun <reified T> diagnose(alias: String = ""): ServiceDiagnosticReport
```

Kotlin：

```kotlin
val report = ServiceLoader.diagnose<Runnable>("startup")

when (report.availability) {
    ServiceDiagnosticAvailability.AVAILABLE -> println(report.entries)
    ServiceDiagnosticAvailability.UNAVAILABLE_IN_NON_DEBUG_BUILD -> Unit
    ServiceDiagnosticAvailability.UNAVAILABLE_PLUGIN_NOT_APPLIED ->
        error("请在 Application 模块应用 auto-service 插件")
}
```

Java：

```java
ServiceDiagnosticReport report = ServiceLoader.diagnose(Runnable.class, "startup");
```

诊断只描述编译期候选，不创建服务实例。

### `ServiceDiagnosticAvailability`

| 枚举值 | 含义 |
| --- | --- |
| `AVAILABLE` | 当前变体是 `debuggable=true`，完整候选元数据可用。 |
| `UNAVAILABLE_IN_NON_DEBUG_BUILD` | 当前变体不可调试；加载仍可用，但 entries 为空。 |
| `UNAVAILABLE_PLUGIN_NOT_APPLIED` | 仍在调用 registry 存根，Application 插件没有生成替换类。 |

### `ServiceDiagnosticStatus`

| 枚举值 | 含义 |
| --- | --- |
| `REGISTERED` | 候选进入生成注册表。 |
| `EXCLUDED` | 候选被某条 className/alias 正则排除。 |

### `ServiceDiagnosticEntry`

```kotlin
data class ServiceDiagnosticEntry(
    val implementationClassName: String,
    val priority: Int,
    val alias: String,
    val singleton: Boolean,
    val status: ServiceDiagnosticStatus,
    val exclusionRule: String?
)
```

`exclusionRule` 仅对 `EXCLUDED` 候选有值。Kotlin data class 还生成 `componentN()`、`copy()`、`equals()`、`hashCode()` 和属性 getter。

### `ServiceDiagnosticReport`

| 属性 | 类型 | 计算规则 |
| --- | --- | --- |
| `serviceClassName` | `String` | 被查询服务类型的全限定名。 |
| `requestedAlias` | `String` | 调用方传入的 alias。 |
| `availability` | `ServiceDiagnosticAvailability` | 当前诊断可用状态。 |
| `entries` | `List<ServiceDiagnosticEntry>` | Debuggable 变体中的全部注册与排除候选；其他状态为空。 |
| `registeredCount` | `Int` | `status == REGISTERED` 的数量。 |
| `matchingCount` | `Int` | 空 alias 时等于全部注册数；非空时统计精确 alias 匹配数。 |
| `availableAliases` | `Set<String>` | 已注册候选的 alias，按候选稳定顺序去重。 |

作为 Kotlin data class，它还生成 `component1()` 到 `component4()`、`copy()`、`equals()` 和 `hashCode()`。`toString()` 由项目实现，适合本地调试日志。它可能包含实现类名和排除规则，不要把 Debug 报告原样上传到不受信任的日志系统。

## Gradle 插件与 DSL

插件只能应用到 Android Application 模块：

```groovy
plugins {
    id 'com.android.application'
    id 'auto-service'
}
```

应用到 Android Library 或普通 Gradle 项目会抛出：

```text
auto-service 只能应用于 Android Application 模块
```

### 完整 DSL

```groovy
autoService {
    checkImplementation = true
    sourceCompatibility = '1.8'
    logLevel = 'INFO'

    require('com.example.StartupTask')
    require('com.example.RegionProvider', 'china')

    excludeClassName('com\\.example\\.legacy\\..*')
    excludeAlias('experimental-.*')
    exclude('com\\.vendor\\..*', 'fallback')
}
```

| 配置 | 类型 | 默认值 | 行为 |
| --- | --- | --- | --- |
| `checkImplementation` | `boolean` | `false` | 为 `true` 时执行全部 `require()`；为 `false` 时忽略必选声明。 |
| `sourceCompatibility` | `String` | `"1.8"` | 传给生成源码编译器的 `-source` 和 `-target`。 |
| `logLevel` | `String` | `"INFO"` | `VERBOSE`、`DEBUG`、`INFO`、`WARN`、`ERROR`；其他字符串回退为 `INFO`。 |
| `require(service)` | 方法 | 无 | 要求该服务至少有一个已注册实现。 |
| `require(service, alias)` | 方法 | 无 | 要求该服务至少有一个精确 alias 匹配实现。 |
| `excludeClassName(pattern)` | 方法 | 无 | 类名匹配 Java 正则时排除，alias 使用 `.*`。 |
| `excludeAlias(pattern)` | 方法 | 无 | alias 匹配 Java 正则时排除，类名使用 `.*`。 |
| `exclude(classPattern, aliasPattern)` | 方法 | 无 | 两个正则同时匹配时排除。 |

正则通过 Java `String.matches()` 判断，即规则需要匹配整个字符串。Groovy 单引号字符串中的点通常写成 `\\.`。

### 变体任务

每个 Application 变体注册：

```text
androidAutoServiceRegister<Variant>
```

任务消费 `ScopedArtifacts.Scope.ALL` 下的 `ScopedArtifact.CLASSES`，覆盖 Application、项目模块、直接依赖和传递 AAR/JAR。任务标记为 `@CacheableTask`。

诊断开关取自 `variant.debuggable`，不按 `debug`/`release` 名称猜测。

## 生成保留类型

`ServiceRegistry` 和 `ServiceRegistryDiagnostics` 在 registry 模块中提供可链接存根。插件会从最终 class JAR 中移除存根，再写入同名生成类。

业务代码不要直接调用它们，原因是：

- 方法签名服务于 loader 与生成器之间的内部契约；
- 未应用插件时 `ServiceRegistry.get()` 会直接失败；
- 后续版本可以在不改变 `ServiceLoader` 的情况下调整生成实现。

## 相关文档

- [架构与类关系](architecture.md)
- [测试覆盖与缺口](testing.md)
- [进阶指南](advanced-guide.md)
- [故障排查](troubleshooting.md)
