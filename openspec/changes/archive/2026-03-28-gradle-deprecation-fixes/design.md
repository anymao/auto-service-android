# Gradle 废弃 API 修复设计

## 一、Groovy DSL 属性赋值语法修复

### 1.1 修复规则

所有 Maven `credentials` 块中的属性赋值需要从 `propName value` 改为 `propName = value`：

```groovy
// 旧语法
credentials {
    username "$ALIYUN_USERNAME"
    password "$ALIYUN_PASSWORD"
}

// 新语法
credentials {
    username = "$ALIYUN_USERNAME"
    password = "$ALIYUN_PASSWORD"
}
```

其他属性如 `url`、`namespace`、`group`、`version` 等也适用同样规则。

### 1.2 修改清单

| 文件 | 行号 | 属性 | 变更 |
|------|------|------|------|
| `build.gradle` | 11 | username | `username "$..."` → `username = "$..."` |
| `build.gradle` | 12 | password | `password "$..."` → `password = "$..."` |
| `build.gradle` | 14 | url | `url '...'` → `url = '...'` |
| `build.gradle` | 45 | username | 同上 |
| `build.gradle` | 46 | password | 同上 |
| `build.gradle` | 48 | url | 同上 |
| `build.gradle` | 52 | username | 同上 |
| `build.gradle` | 53 | password | 同上 |
| `build.gradle` | 55 | url | 同上 |
| `app/build.gradle` | 9 | namespace | `namespace '...'` → `namespace = '...'` |
| `app/build.gradle` | 16 | multiDexEnabled | `multiDexEnabled true` → `multiDexEnabled = true` |
| `auto-service-annotation/build.gradle` | 30 | group | `group 'com.anymore'` → `group = 'com.anymore'` |
| `auto-service-annotation/build.gradle` | 31 | version | `version VERSION` → `version = VERSION` |
| `auto-service-loader/build.gradle` | 34 | group | 同上 |
| `auto-service-loader/build.gradle` | 35 | version | 同上 |
| `auto-service-plugin/build.gradle` | 11 | username | 同上 |
| `auto-service-plugin/build.gradle` | 12 | password | 同上 |
| `auto-service-plugin/build.gradle` | 14 | url | 同上 |
| `auto-service-plugin/build.gradle` | 38 | group | 同上 |
| `auto-service-plugin/build.gradle` | 39 | version | 同上 |

## 二、废弃 API 升级

### 2.1 destinationDir → destinationDirectory

**文件**：`auto-service-plugin/src/main/groovy/com/anymore/auto/gradle/AutoServiceRegisterPlugin.groovy`

**位置**：第 56 行

**当前代码**：
```groovy
it.setDestinationDir(compileOutputDir)
```

**新代码**：
```groovy
it.destinationDirectory.set(project.layout.directory(compileOutputDir))
```

### 2.2 buildDir → layout.buildDirectory

**文件**：同上

**位置**：第 30 行和第 34 行

**当前代码（第 30 行）**：
```groovy
final workDir = project.file("${project.buildDir}${File.separator}intermediates${File.separator}auto_service${File.separator}${variant.dirName}${File.separator}")
```

**新代码**：
```groovy
final workDir = project.layout.buildDirectory
    .dir("intermediates/auto_service/${variant.dirName}")
    .get()
    .asFile
```

**当前代码（第 34 行）**：
```groovy
final compileOutputDir = project.file("${project.buildDir}${File.separator}intermediates${File.separator}javac${File.separator}${variant.name}${File.separator}compile${variant.name.capitalize()}JavaWithJavac${File.separator}classes")
```

**新代码**：
```groovy
final compileOutputDir = project.layout.buildDirectory
    .dir("intermediates/javac/${variant.name}/compile${variant.name.capitalize()}JavaWithJavac/classes")
    .get()
    .asFile
```

## 三、路径拼接优化

### 3.1 workDir 路径优化

**修改后**（第 30 行）：
```gro
final workDir = project.layout.buildDirectory
    .dir("intermediates/auto_service/${variant.dirName}")
    .get()
    .asFile
```

使用 `ProjectLayout` API 的优势：
- 类型安全
- 与 Gradle 配置缓存兼容
- 更清晰的路径表达

### 3.2 compileOutputDir 路径优化

**修改后**（第 34 行）：
```groovy
final compileOutputDir = project.layout.buildDirectory
    .dir("intermediates/javac/${variant.name}/compile${variant.name.capitalize()}JavaWithJavac/classes")
    .get()
    .asFile
```

## 四、完整性验证

### 4.1 文件清单

修改涉及以下文件：

1. `build.gradle` - 根目录构建配置
2. `app/build.gradle` - 应用模块配置
3. `auto-service-annotation/build.gradle` - 注解模块配置
4. `auto-service-loader/build.gradle` - 加载器模块配置
5. `auto-service-plugin/build.gradle` - 插件模块配置
6. `auto-service-plugin/src/main/groovy/com/anymore/auto/gradle/AutoServiceRegisterPlugin.groovy` - 插件主逻辑

### 4.2 验证步骤

1. 清理构建缓存
   ```bash
   ./gradlew clean
   ```

2. 执行构建（启用所有警告）
   ```bash
   ./gradlew :app:assembleDebug --warning-mode all
   ```

3. 验证结果：
   - 无废弃警告
   - 构建成功
   - ServiceRegistry 正确生成

4. 验证生成的文件路径
   ```bash
   ls -la app/build/intermediates/auto_service/debug/src/
   ```

## 五、向后兼容性

### 5.1 Gradle 版本兼容性

| API 变更 | 最低 Gradle 版本 | Gradle 8.x 兼容 | Gradle 9.x 兼容 |
|---------|-------------------|-------------------|-------------------|
| 属性赋值语法 | 7.0 | ✅ | ✅ |
| destinationDirectory | 5.0 | ✅ | ✅ |
| layout.buildDirectory | 6.5 | ✅ | ✅ |

### 5.2 风险评估

- 旧语法在 Gradle 10.0 中移除，但新语法在当前版本完全兼容
- `ProjectLayout` API 已稳定多年，无向后兼容问题
- 修改后不影响项目功能，仅修复废弃警告
