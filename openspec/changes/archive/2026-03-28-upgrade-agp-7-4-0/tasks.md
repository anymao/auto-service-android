## 1. Gradle 环境升级

- [x] 1.1 更新 gradle-wrapper.properties：将 Gradle 版本从 6.7.1 升级到 7.5
  - 文件：`gradle/wrapper/gradle-wrapper.properties`
  - 变更：`distributionUrl=https\://services.gradle.org/distributions/gradle-7.5-bin.zip`

- [x] 1.2 更新根目录 build.gradle：将 AGP 版本从 4.2.2 升级到 7.4.0
  - 文件：`build.gradle`
  - 变更：`classpath "com.android.tools.build:gradle:7.4.0"`

- [x] 1.3 更新根目录 build.gradle：将 Kotlin 版本从 1.5.31 升级到 1.8.10
  - 文件：`build.gradle`
  - 变更：`ext.kotlin_version = "1.8.10"`

## 2. App 模块 DSL 语法适配

- [x] 2.1 更新 app/build.gradle：将 `compileSdkVersion 30` 改为 `compileSdk 33`
  - 文件：`app/build.gradle`
  - 行：`compileSdkVersion 30` → `compileSdk 33`

- [x] 2.2 更新 app/build.gradle：将 `targetSdkVersion 30` 改为 `targetSdk 33`
  - 文件：`app/build.gradle`
  - 行：`targetSdkVersion 30` → `targetSdk 33`

- [x] 2.3 更新 app/build.gradle：将 `minSdkVersion 17` 改为 `minSdk 17`
  - 文件：`app/build.gradle`
  - 行：`minSdkVersion 17` → `minSdk 17`

- [x] 2.4 更新 app/build.gradle：移除 `buildToolsVersion "30.0.3"` 配置行
  - 文件：`app/build.gradle`
  - 删除：`buildToolsVersion "30.0.3"`

## 3. 插件模块依赖升级

- [x] 3.1 更新 buildSrc/build.gradle：将 AGP compileOnly 依赖从 1.3.1 升级到 7.4.0
  - 文件：`buildSrc/build.gradle`
  - 行：`compileOnly("com.android.tools.build:gradle:1.3.1")` → `compileOnly("com.android.tools.build:gradle:7.4.0")`

- [x] 3.2 更新 buildSrc/build.gradle：将 AGP implementation 依赖从 3.5.0 升级到 7.4.0
  - 文件：`buildSrc/build.gradle`
  - 行：`implementation("com.android.tools.build:gradle:3.5.0")` → `implementation("com.android.tools.build:gradle:7.4.0")`

## 4. 插件 API 验证

以下 API 在 AGP 7.x 中保持兼容，需要编译验证：

- [x] 4.1 验证 AppPlugin 类路径：`com.android.build.gradle.AppPlugin`
  - 预期：路径不变，编译成功

- [x] 4.2 验证 AppExtension 类路径：`com.android.build.gradle.AppExtension`
  - 预期：路径不变，编译成功

- [x] 4.3 验证 applicationVariants API 遍历逻辑
  - 预期：API 兼容，无需修改代码

- [x] 4.4 验证 javaCompileProvider.classpath API
  - 预期：API 兼容，返回 FileCollection

- [x] 4.5 验证 javaCompileProvider.destinationDir API
  - 预期：API 兼容，返回 File

- [x] 4.6 验证 bootClasspath API
  - 预期：API 兼容，返回 List<File>

- [x] 4.7 验证 assembleProvider API
  - 预期：API 兼容

- [x] 4.8 编译 buildSrc 模块：验证插件代码无编译错误
  - 命令：`./gradlew :buildSrc:compileGroovy` 或直接触发 Sync

## 5. 验证和测试

- [x] 5.1 执行清理构建：清除旧版本缓存
  - 命令：`./gradlew clean`

- [x] 5.2 执行 app 模块编译：验证整体编译流程
  - 命令：`./gradlew :app:assembleDebug`

- [x] 5.3 验证插件功能：检查生成的 ServiceRegistry.java 文件
  - 位置：`app/build/intermediates/auto_service/debug/src/com/anymore/auto/ServiceRegistry.java`
  - 验证内容：包含正确的服务注册代码

- [x] 5.4 验证 ServiceLoader 加载：运行 app 确认服务加载正常工作
  - 安装 app，检查日志输出

- [x] 5.5 检查 Javassist 字节码处理：确认能正确解析 JDK 11+ 编译的 class 文件
  - 当前版本 Javassist 3.28.0-GA 支持 JDK 11+

## 6. 文档更新

- [x] 6.1 更新 CLAUDE.md：添加 JDK 11+ 要求说明
  - 添加：开发环境要求 JDK 11 或更高版本

- [x] 6.2 更新 CLAUDE.md：更新构建命令说明（如有必要）

## 任务依赖关系

```
Phase 1 ──┬── Phase 2 ──┬── Phase 4 ── Phase 5 ── Phase 6
          │             │
          └── Phase 3 ──┘
```

- Phase 1 必须最先完成（环境升级）
- Phase 2 和 Phase 3 可以并行（DSL 适配和插件依赖升级）
- Phase 4 依赖 Phase 3 完成（需要新 AGP API 才能验证）
- Phase 5 依赖 Phase 4 完成（编译验证后才能测试）
- Phase 6 最后完成（文档更新）