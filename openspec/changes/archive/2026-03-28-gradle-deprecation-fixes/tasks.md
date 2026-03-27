# Gradle 废弃 API 修复任务

## 任务列表

### 阶段 1：修复 Groovy DSL 属性赋值语法

- [x] **修复 build.gradle 中的 credentials 属性**
  - 文件：`build.gradle`
  - 修改行：11, 12, 14（buildscript.repositories）
  - 修改行：45, 46, 48（allprojects.repositories）
  - 修改行：52, 53, 55（allprojects.repositories snapshot）
  - 变更：`username "$X"` → `username = "$X"`
  - 变更：`password "$X"` → `password = "$X"`
  - 变更：`url 'X'` → `url = 'X'`

- [x] **修复 app/build.gradle 中的属性**
  - 文件：`app/build.gradle`
  - 修改行：9（namespace）
  - 修改行：16（multiDexEnabled）
  - 变更：`namespace 'com.xxx'` → `namespace = 'com.xxx'`
  - 变更：`multiDexEnabled true` → `multiDexEnabled = true`

- [x] **修复 auto-service-annotation/build.gradle**
  - 文件：`auto-service-annotation/build.gradle`
  - 修改行：30, 31
  - 变更：`group 'com.anymore'` → `group = 'com.anymore'`
  - 变更：`version VERSION` → `version = VERSION`

- [x] **修复 auto-service-loader/build.gradle**
  - 文件：`auto-service-loader/build.gradle`
  - 修改行：34, 35
  - 变更：同上

- [x] **修复 auto-service-plugin/build.gradle 中的 credentials 属性**
  - 文件：`auto-service-plugin/build.gradle`
  - 修改行：11, 12, 14（credentials）
  - 修改行：38, 39（group, version）
  - 变更：同上

### 阶段 2：升级废弃 API

- [x] **升级 destinationDir 到 destinationDirectory**
  - 文件：`auto-service-plugin/src/main/groovy/com/anymore/auto/gradle/AutoServiceRegisterPlugin.groovy`
  - 修改行：56
  - 旧：`it.setDestinationDir(compileOutputDir)`
  - 新：`it.destinationDirectory.set(project.layout.directory(compileOutputDir))`

- [x] **升级 buildDir 到 layout.buildDirectory（workDir）**
  - 文件：同上
  - 修改行：30
  - 旧：`project.file("${project.buildDir}${File.separator}intermediates${File.separator}auto_service${File.separator}${variant.dirName}${File.separator}")`
  - 新：
    ```groovy
    project.layout.buildDirectory
        .dir("intermediates/auto_service/${variant.dirName}")
        .get()
        .asFile
    ```

- [x] **升级 buildDir 到 layout.buildDirectory（compileOutputDir）**
  - 文件：同上
  - 修改行：34
  - 旧：`project.file("${project.buildDir}${File.separator}intermediates${File.separator}javac${File.separator}${variant.name}${File.separator}compile${variant.name.capitalize()}JavaWithJavac${File.separator}classes")`
  - 新：
    ```groovy
    project.layout.buildDirectory
        .dir("intermediates/javac/${variant.name}/compile${variant.name.capitalize()}JavaWithJavac/classes")
        .get()
        .asFile
    ```

### 阶段 3：验证测试

- [x] **清理构建缓存**
  ```bash
  ./gradlew clean
  ```

- [x] **执行构建并检查警告**
  ```bash
  ./gradlew :app:assembleDebug --warning-mode all 2>&1 | grep -i "deprecated\|warning"
  ```
  - 预期：无废弃警告输出

- [x] **验证构建成功**
  ```bash
  ./gradlew :app:assembleDebug
  ```
  - 预期：BUILD SUCCESSFUL

- [x] **验证 ServiceRegistry 生成**
  ```bash
  ls -la app/build/intermediates/auto_service/debug/src/com/anymore/auto/ServiceRegistry.java
  ```
  - 预期：文件存在且内容正确

- [x] **检查生成的日志**
  ```bash
  ./gradlew :app:assembleDebug 2>&1 | grep -A2 "AutoService"
  ```
  - 预期：看到正常的插件执行日志

### 阶段 4：回归测试

- [x] **测试 debug 构建**
  ```bash
  ./gradlew :app:assembleDebug
  ```

- [ ] **测试 release 构建**
  ```bash
  ./gradlew :app:assembleRelease
  ```

- [ ] **测试模块模式构建（非 buildSrc）**
  - 确保当前处于模块模式
  - 验证插件正常加载

## 执行顺序

```
阶段 1 → 阶段 2 → 阶段 3 → 阶段 4
```

## 预估时间

| 阶段 | 任务数 | 预估时间 |
|------|--------|----------|
| 阶段 1 | 5 个任务 | 2 小时 |
| 阶段 2 | 3 个任务 | 1.5 小时 |
| 阶段 3 | 5 个任务 | 1 小时 |
| 阶段 4 | 3 个任务 | 0.5 小时 |
| **总计** | **16 个任务** | **5 小时** |

## 备注

- 所有修改仅为语法升级，不改变功能逻辑
- 修改后应确保向后兼容性
- 如遇到任何构建失败，检查路径拼接是否正确
