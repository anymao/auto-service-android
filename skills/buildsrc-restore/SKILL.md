---
name: buildsrc-restore
description: 切换项目从 buildSrc 构建开发模式恢复到模块模式。当用户提到"切回模块模式"、"恢复模块模式"、"退出 buildSrc 模式"、"恢复正常构建模式"或类似短语时使用此 skill。在完成 auto-service-plugin 开发后使用此模式恢复到正常的构建流程。
---

# buildSrc 模式恢复器

将 auto-service-android 项目从 buildSrc 构建开发模式恢复到模块模式，以便使用正常的 Maven 仓库构建流程。

## 恢复步骤

按以下顺序执行操作：

### 1. 重命名 buildSrc 构建文件

将 `buildSrc/build.gradle` 重命名回 `buildSrc/build.gradle.bak`

```bash
mv buildSrc/build.gradle buildSrc/build.gradle.bak
```

如果 `buildSrc/build.gradle` 文件不存在，检查：
- 如果 `buildSrc/build.gradle.bak` 已存在，说明可能已经是模块模式了，提示用户
- 如果两个文件都不存在，提示用户需要先创建 build.gradle 文件

### 2. 恢复根目录 build.gradle 中的插件依赖

读取根目录（项目根目录）的 `build.gradle` 文件，找到并取消注释插件依赖：

```groovy
// classpath("com.anymore:auto-service-register:x.x.x")
```

将其改为：

```groovy
classpath("com.anymore:auto-service-register:x.x.x")
```

注意：要保留缩进格式，只取消注释 classpath 行，确保版本号正确（当前应为 8.13.0 适配版本）

### 3. 恢复 app/build.gradle 中的插件引用

读取 `app/build.gradle` 文件，找到并取消注释 plugins 中的 `id 'auto-service'` 引用：

```groovy
plugins {
    // id 'auto-service'
}
```

将其改为：

```groovy
plugins {
    id 'auto-service'
}
```

注意：同样要保留缩进格式。

### 4. 注释 app/build.gradle 中的 buildSrc 构建插件引用

读取 `app/build.gradle` 文件，注释掉或删除 `AutoServiceRegisterPlugin` 引用：

```groovy
import com.anymore.auto.gradle.AutoServiceRegisterPlugin
apply plugin: AutoServiceRegisterPlugin
```

将其改为：

```groovy
// import com.anymore.auto.gradle.AutoServiceRegisterPlugin
// apply plugin: AutoServiceRegisterPlugin
```

或直接删除这两行代码。

## 验证步骤

完成恢复后，验证以下内容：

1. `buildSrc/build.gradle.bak` 文件存在，`buildSrc/build.gradle` 不存在
2. 根目录 `build.gradle` 中的 classpath 依赖已取消注释
3. `app/build.gradle` 中的 `id 'auto-service'` 已取消注释
4. `app/build.gradle` 中不存在或已注释 `apply plugin: AutoServiceRegisterPlugin`

## 后续发布流程

完成恢复后，向用户说明后续的发布和构建流程：

```
1. 先发布插件到 Maven 仓库：
   ./gradlew :auto-service-plugin:uploadArchives

2. 然后构建 app 模块：
   ./gradlew :app:assembleDebug
```

## 切换回 buildSrc 模式的说明

简要提醒用户，如果需要继续开发调试，可以使用 buildsrc-switcher skill 切换回 buildSrc 模式。

## 错误处理

- 如果 `buildSrc` 目录不存在，提示用户此项目可能不是 auto-service-android 项目
- 如果必要的文件不存在，给出明确的错误提示
- 如果文件已经被取消注释或重命名，告知用户当前状态
- 如果 Maven 仓库配置不正确（如 ALIYUN_USERNAME 和 ALIYUN_PASSWORD 环境变量未设置），提示用户需要先配置
