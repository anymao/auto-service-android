# auto-service-android 故障排查

本文按“现象 → 判断 → 修复 → 验证”组织。命令均在 AGP 8.13.0、Gradle 8.13、JDK 17 下执行。

## `load()` 返回空结果

1. 在 Application 模块确认已应用 `id 'auto-service'`。Library 模块不要应用插件。
2. 确认实现类使用 `@AutoService(TargetService::class)`，并真正实现该接口。
3. 确认实现所在项目模块或 AAR/JAR 是当前变体的依赖，不是只属于另一 build type。
4. 在 debuggable 变体执行：

   ```kotlin
   println(ServiceLoader.diagnose<TargetService>())
   ```

5. `availability=UNAVAILABLE_PLUGIN_NOT_APPLIED` 表示注册表仍是存根；检查插件应用与 `androidAutoServiceRegisterDebug` 是否执行。
6. `entries` 有候选但状态均为 `EXCLUDED`，转到“实现被排除”。完全没有候选时，检查依赖图和注解的接口参数。

验证：运行 `./gradlew :app:assembleDebug --info`，应出现 `androidAutoServiceRegisterDebug`，随后 `registeredCount` 大于 0。

## 指定 alias 没有匹配

```kotlin
val report = ServiceLoader.diagnose<TargetService>("production")
println(report.availableAliases)
println(report.matchingCount)
```

- `registeredCount > 0` 且 `matchingCount == 0`：调用 alias 与注解值不完全相同。alias 匹配区分大小写，不按正则匹配。
- `availableAliases` 包含空字符串：这些实现只能由空 alias 查询全部结果，不能匹配 `"production"`。
- 对应候选为 `EXCLUDED`：检查 alias 排除正则。

修复注解或调用字符串后重新 assemble；注册表是构建产物，仅修改运行时代码不会更新它。

## 实现被排除

Debug 报告会保留排除候选和命中规则：

```kotlin
ServiceLoader.diagnose<TargetService>().entries
    .filter { it.status == ServiceDiagnosticStatus.EXCLUDED }
    .forEach { println("${it.implementationClassName}: ${it.exclusionRule}") }
```

逐项检查 `excludeClassName`、`excludeAlias` 和 `exclude(classPattern, aliasPattern)`。前两者会把另一个字段视为 `.*`；`exclude()` 只有两个正则同时匹配才排除。Groovy 字符串中的点需要写成 `\\.`。

## 构建报告重复 class

错误会列出 class 全限定名和两个输入来源。这意味着 Application、项目模块、直接或传递 AAR/JAR 中出现了同名普通类。

1. 运行依赖报告定位重复坐标：

   ```bash
   ./gradlew :app:dependencies --configuration debugRuntimeClasspath
   ```

2. 对错误中两个 JAR/AAR 分别确认由哪条依赖路径引入。
3. 升级到不重复的版本，或在依赖声明上排除其中一条传递依赖。
4. 不要用 packagingOptions 掩盖 class 冲突；该选项主要处理资源，不能定义可靠的 class 覆盖顺序。

`ServiceRegistry` 和 `ServiceRegistryDiagnostics` 是插件替换存根的保留类，不按普通重复类处理。

## 插件与 loader 版本不一致

`0.0.13` 要求 `auto-service-register`、`auto-service-loader`、`auto-service-annotation`、`auto-service-registry` 对齐。混用版本可能造成缺少诊断类型、存根签名不一致或生成类无法链接。

```groovy
classpath 'com.anymore:auto-service-register:0.0.13'
implementation 'com.anymore:auto-service-loader:0.0.13'
```

运行 `./gradlew :app:dependencies`，确认没有旧版本被约束或传递依赖选中。修复后使用 `--refresh-dependencies` 只在确实怀疑缓存坐标错误时重试。

## instrumentation 在 manifest merger 阶段失败

如果看到 `InstrumentationActivityInvoker` 的 Bootstrap/Empty Activity 缺少 `android:exported`，先检查 AndroidX Test 依赖是否仍解析到旧的 `androidx.test:core:1.3.0`。当前工程使用：

```groovy
androidTestImplementation 'androidx.test.ext:junit:1.1.5'
androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
```

用下面的命令确认实际解析版本：

```bash
./gradlew :app:dependencies --configuration debugAndroidTestRuntimeClasspath
```

应看到 `androidx.test:core:1.5.0`，然后再执行：

```bash
./gradlew :app:connectedDebugAndroidTest
```

不要用临时覆盖测试 Activity 的 manifest 属性掩盖旧依赖链；如果仍失败，记录完整依赖图、AGP、targetSdk 和设备系统版本。

## sources JAR 缺少插件入口源码

发布的 `auto-service-plugin-0.0.13-sources.jar` 应同时包含 main Groovy 源码和 `pluginEntry` 中的 `AutoServiceRegisterPlugin.kt`。本版本的发布脚本会在项目配置完成后绑定自定义源集。可用以下命令检查：

```bash
./gradlew :auto-service-plugin:sourcesJar
unzip -l auto-service-plugin/build/libs/auto-service-plugin-0.0.13-sources.jar \
  | rg 'AutoServiceRegisterPlugin.kt|ClassMetadataScanner.groovy'
```

如果二次封装自己的发布脚本，应保留 `pluginEntry` 源码，不能只取 `sourceSets.main.allSource`。

## Release 中诊断不可用

这是预期行为，不是加载失败：

- `load()` 在 Debug 和 Release 都使用完整注册表；
- `diagnose()` 仅在 `variant.debuggable == true` 时返回候选；
- 非 debuggable 变体返回 `UNAVAILABLE_IN_NON_DEBUG_BUILD` 和空 entries；
- Release 诊断类不携带实现名、alias 或排除正则。

若需要线上观测，记录业务层选中的实现或设计不含候选清单的指标，不要把完整编译期诊断元数据重新塞进 Release。

## 发布提示缺少凭据

错误：`发布到私有 Maven 仓库需要 ALIYUN_USERNAME 和 ALIYUN_PASSWORD`。

在发布进程外提供两个值之一：

```bash
export ALIYUN_USERNAME='由密钥系统注入'
export ALIYUN_PASSWORD='由密钥系统注入'
./gradlew :auto-service-loader:publish
```

也可写入用户级 `~/.gradle/gradle.properties`，但不能写入仓库的 `gradle.properties` 或 `buildSrc/gradle.properties`。普通 `projects`、测试和 assemble 不需要这两个值。曾提交到 Git 历史的旧凭据必须在服务端吊销并轮换；从当前文件删除不能使历史凭据失效。

## 仍无法定位

收集以下信息后再提交问题：

- AGP、Gradle、JDK 和四个组件版本；
- 失败变体及 `variant.debuggable`；
- `ServiceDiagnosticReport.toString()` 的 Debug 输出；
- 重复类错误中的两个来源；
- `androidAutoServiceRegister<Variant>` 的任务日志。

不要附带 Maven 用户名、密码或私有仓库访问令牌。
