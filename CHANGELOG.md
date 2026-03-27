# 版本变更日志

## v0.0.11 (2026-03-28)

### 解决 Gradle 废弃 API 警告

**主要变更**:
- ✅ 修复 Groovy DSL 属性赋值语法（Gradle 10.0 兼容）
  - `username "$X"` → `username = "$X"`
  - `password "$X"` → `password = "$X"`
  - `url 'X'` → `url = 'X'`
  - `namespace 'com.xxx'` → `namespace = 'com.xxx'`
  - `group 'com.xxx'` → `group = 'com.xxx'`
  - `version X` → `version = X`
- ✅ 升级 JavaCompile 废弃 API（Gradle 9.0 兼容）
  - `setDestinationDir(file)` → `destinationDirectory.set(file)`
- ✅ 升级 buildDir 到 layout.buildDirectory（Gradle 9.0 兼容）
  - 使用 `project.layout.buildDirectory.dir(...).get().asFile`
  - 替代字符串拼接和 `File.separator` 的路径构建方式
- ✅ 添加 buildsrc-switcher 和 buildsrc-restore skills

### 环境要求

**构建环境**:
- JDK 11 或更高版本
- Gradle 8.13 或更高版本
- Android Gradle Plugin 8.13.0 或更高版本

### 技术变更详情

#### 1. Groovy DSL 语法修复
修复的文件：
- `build.gradle` - 根目录构建配置（9 处修改）
- `app/build.gradle` - 应用模块配置（2 处修改）
- `auto-service-annotation/build.gradle` - 注解模块（2 处修改）
- `auto-service-loader/build.gradle` - 加载器模块（2 处修改）
- `auto-service-plugin/build.gradle` - 插件模块（4 处修改）

#### 2. 废弃 API 升级
文件：`auto-service-plugin/src/main/groovy/com/anymore/auto/gradle/AutoServiceRegisterPlugin.groovy`

**升级的 API**:
- 第 56 行：`setDestinationDir` → `destinationDirectory.set`
- 第 30-33 行：`workDir` 路径使用 `layout.buildDirectory`
- 第 37-40 行：`compileOutputDir` 路径使用 `layout.buildDirectory`

#### 3. Skills 增强
新增两个 OpenSpec skills 用于快速切换构建模式：
- `buildsrc-switcher` - 切换到 buildSrc 模式（快速插件开发）
- `buildsrc-restore` - 恢复到模块模式（正常构建流程）

### 迁移指南

**无破坏性升级**：
- ✅ 所有修改仅为语法升级，不改变功能逻辑
- ✅ 新语法在 Gradle 8.x 中完全兼容
- ✅ 构建成功，功能正常
- ✅ ServiceRegistry 正确生成

### 已知问题

- ⚠️ 仍有少量废弃警告来自第三方依赖
- ⚠️ 建议发布前执行完整的回归测试（debug/release 模块模式）

---

## v0.0.10 (2026-03-28)

### 适配 Android Gradle Plugin 8.13.0

### 升级 Android Gradle Plugin 到 7.4.0

**主要变更**:
- ✅ 升级 Gradle 从 6.7.1 到 7.5
- ✅ 升级 Android Gradle Plugin 从 4.2.2 到 7.4.0
- ✅ 升级 Kotlin 从 1.5.31 到 1.8.10
- ✅ 所有模块 Java 版本从 1.7 升级到 11（AGP 7.4.0 要求）
- ✅ 更新 DSL 语法（`compileSdkVersion` → `compileSdk` 等）
- ✅ 迁移 Maven 发布到 `maven-publish` 插件（`maven` 插件在 Gradle 7.x 已废弃）

### 环境要求

**构建环境**:
- JDK 11 或更高版本（AGP 7.x 强制要求）
- Gradle 7.5 或更高版本
- Android Gradle Plugin 7.4.0

**开发工具**:
- Android Studio 2022.1+ (Chipmunk) 或更高版本

### 技术变更详情

#### 1. 构建配置升级
- `gradle/wrapper/gradle-wrapper.properties`: Gradle 6.7.1 → 7.5
- `build.gradle`: AGP 4.2.2 → 7.4.0, Kotlin 1.5.31 → 1.8.10
- 环境变量名修正: `ALIYUN` → `ALIYUN`（阿里云）

#### 2. App 模块 DSL 语法适配
- `compileSdkVersion 30` → `compileSdk 33`
- `targetSdkVersion 30` → `targetSdk 33`
- `minSdkVersion 17` → `minSdk 17`
- 移除 `buildToolsVersion "30.0.3"`（AGP 7.x 自动管理）
- 添加 `namespace` 配置（推荐做法）
- 修复 `android:exported` 属性（Android 12+ 要求）

#### 3. 插件模块升级
- `buildSrc/build.gradle`:
  - AGP 依赖: 1.3.1/3.5.0 → 7.4.0
  - Kotlin gradle plugin: 1.5.31 → 1.8.10
  - Java 版本: 1.7 → 11
- `auto-service-plugin/build.gradle`: Java 版本 1.7 → 11

#### 4. 插件 API 验证
核心插件 API 在 AGP 7.x 中保持完全兼容：
- `AppPlugin`: 包路径不变
- `AppExtension`: 包路径不变（仍可用）
- `applicationVariants`: 完全兼容
- `javaCompileProvider.classpath`: 完全兼容
- `javaCompileProvider.destinationDir`: 完全兼容
- `bootClasspath`: 完全兼容
- `assembleProvider`: 完全兼容

#### 5. Maven 发布迁移
- `maven_publish.gradle`:
  - 使用 `maven-publish` 插件替代已废弃的 `maven` 插件
  - 配置 `publishing { publications { maven(MavenPublication) } }`
  - 支持自定义 `artifactId`（通过项目属性或使用项目名称）
  - 配置完整的 POM 信息（许可证、开发者、SCM）
  - 保留 `uploadArchives` 任务作为向后兼容

#### 6. 文档更新
- `CLAUDE.md`: 添加 JDK 11+ 开发环境要求
- 新增 AGP 7.4.0 自定义插件开发指南（`openspec/changes/archive/2026-03-28-upgrade-agp-7-4-0/specs/agp-7-4-plugin-guide/`）

### 迁移指南

**对于现有项目**:
1. 确保 JDK 版本 >= 11
2. 更新 Gradle wrapper: `./gradlew wrapper --gradle-version 7.5`
3. 更新项目依赖版本
4. 更新 DSL 语法
5. 执行 `./gradlew clean` 清理旧缓存
6. 重新构建项目

**对于新项目**:
1. 使用 AGP 7.4.0 模板创建项目
2. 使用推荐的 DSL 语法
3. 确保 JDK 版本 >= 11

### 兼容性

✅ **向后兼容**: 插件核心 API 完全兼容，无需修改插件代码
✅ **编译通过**: buildSrc 编译成功，app 模块编译成功
✅ **功能验证**: AutoService 插件正常工作，ServiceRegistry 正确生成

### 已知问题

1. **Maven 插件废弃**: 在 Gradle 7.x 中 `maven` 插件已废弃，已迁移到 `maven-publish`
2. **构建缓存**: 升级后首次构建建议执行 `./gradlew clean`

---

## v0.0.8 (2023-03-29)

ServiceLoader 变更为懒加载

## v0.0.7 (2023-03-03)

修复别名获取时候的 bug

## v0.0.6 (2022-09-10)

增加排除的规则以避免响应的实现的注入

## v0.0.5 (2022-05-20)

修复多接口单例下，通过不同接口拉起的单例不唯一问题

## v0.0.4 (2022-05-19)

@AutoService 增加 singleton，提供单例；编译预检查支持接口+别名

## v0.0.3 (2022-04-17)

@AutoService 增加 alias，可通过别名过滤

## v0.0.2 (2022-04-09)

## v0.0.1 (2022-04-09)

add AutoServiceExtension

## v0.0.0 (2022-04-05)

init-project
