# Gradle 废弃 API 修复提案

## 背景

AGP 8.13.0 适配已完成，但构建过程中仍存在多个 Gradle 废弃 API 警告。这些警告将在 Gradle 9.0 和 10.0 中导致构建失败。

## 问题

当前构建产生以下废弃警告：

### 高优先级（Gradle 10.0 将移除）

**Groovy DSL 属性赋值语法**：约 20 处
- 旧语法：`username "$VALUE"`
- 新语法：`username = "$VALUE"`
- 影响文件：
  - `build.gradle`
  - `app/build.gradle`
  - `auto-service-annotation/build.gradle`
  - `auto-service-loader/build.gradle`
  - `auto-service-plugin/build.gradle`

### 中优先级（Gradle 9.0将移除）

**JavaCompile destinationDir 属性**：1 处
- 旧 API：`setDestinationDir(file)`
- 新 API：`destinationDirectory.set(project.layout.directory(file))`
- 位置：`AutoServiceRegisterPlugin.groovy:56`

**project.buildDir 使用**：2 处
- 旧 API：`project.buildDir`
- 新 API：`project.layout.buildDirectory`
- 位置：`AutoServiceRegisterPlugin.groovy:30, 34`

### 低优先级（代码优化）

**路径拼接方式**：2 处
- 当前使用字符串拼接和 `File.separator`
- 建议使用更简洁的方式

## 目标

修复所有 Gradle 废弃 API 警告，确保与 Gradle 9.0 和 10.0 兼容。

## 范围

### 包含
- 修复所有 Groovy 属性赋值语法
- 升级 `destinationDir` 到 `destinationDirectory`
- 升级 `buildDir` 到 `layout.buildDirectory`
- 优化路径拼接代码

### 不包含
- 添加增量构建注解（可选优化）
- 迁移到新的 Variant API（单独提案）

## 成功标准

- [ ] `./gradlew :app:assembleDebug --warning-warning all` 无废弃警告
- [ ] 构建成功，功能正常
- [ ] ServiceRegistry 正确生成
- [ ] 无新增编译错误

## 风险

| 风险项 | 影响 | 缓解措施 |
|---------|------|-----------|
| 路径 API 变更导致构建失败 | 高 | 充分测试，验证文件路径正确性 |
| DSL 语法变更破坏兼容性 | 中 | 保持向后兼容，仅升级到新语法 |

## 估算工作量

- Groovy DSL 语法修复：2 小时
- 废弃 API 升级：1.5 小时
- 路径优化：0.5 小时
- 测试验证：1 小时
- **总计：5 小时**
