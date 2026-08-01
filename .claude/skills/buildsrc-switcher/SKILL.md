---
name: buildsrc-switcher
description: 切换项目到 buildSrc 构建开发模式。当用户提到"切换到 buildSrc 模式"、"启用 buildSrc 构建模式"、"使用 buildSrc 模式开发"或类似短语时使用此 skill。在开发 auto-service-plugin 时使用此模式可以快速迭代，无需每次都发布到 Maven 仓库。
---

# buildSrc 构建模式切换器

将 auto-service-android 项目切换到 buildSrc 构建开发模式，以便快速迭代开发插件而无需每次都发布到 Maven 仓库。

## 切换步骤

按以下顺序执行操作：

### 1. 重命名 buildSrc 构建文件

将 `buildSrc/build.gradle.bak` 重命名为 `buildSrc/build.gradle`

```bash
mv buildSrc/build.gradle.bak buildSrc/build.gradle
```

如果 `build.gradle.bak` 文件不存在，先检查 `build.gradle` 是否已经存在：
- 如果存在，说明可能已经是 buildSrc 模式了，提示用户
- 如果不存在，提示用户需要先创建备份文件

### 2. 注释根目录 build.gradle 中的插件依赖

读取根目录（项目根目录）的 `build.gradle` 文件，找到并注释掉插件依赖：

```groovy
dependencies {
    classpath("com.anymore:auto-service-register:x.x.x")
}
```

将其改为：

```groovy
dependencies {
    // classpath("com.anymore:auto-service-register:x.x.x")
}
```

注意：注释时要保留缩进格式，只注释 classpath 行，不注释 dependencies 块的其他内容。

### 3. 注释 app/build.gradle 中的插件引用

读取 `app/build.gradle` 文件，找到并注释掉 plugins 中`id 'auto-service'` 引用：

```groovy
plugins {
    id 'auto-service'
}
```

将其改为：

```groovy
plugins {
    // id 'auto-service'
}
```

注意：同样要保留缩进格式。

### 4. app/build.gradle 增加 buildSrc 构建插件引用

读取 `app/build.gradle` 文件，添加 `AutoServiceRegisterPlugin` 引用：

```groovy
import com.anymore.auto.gradle.AutoServiceRegisterPlugin
apply plugin: AutoServiceRegisterPlugin
```

注意事项：
- 插入位置：通常放在 `plugins` 块之后或文件顶部（在文件内容开始之前）
- 避免重复添加：如果文件中已存在这两行代码，则跳过此步骤
- 保留格式：不要改变其他内容的缩进格式

## 验证步骤

完成切换后，验证以下内容：

1. `buildSrc/build.gradle` 文件存在
2. 根目录 `build.gradle` 中的 classpath 依赖已被注释
3. `app/build.gradle` 中的 `id 'auto-service'` 已被注释
4. `app/build.gradle` 中已经声明 `apply plugin: AutoServiceRegisterPlugin`

## 开发迭代流程

完成切换后，向用户说明后续的开发流程：

```
1. 修改 auto-service-plugin/src/main/groovy 中的代码
2. ./gradlew clean
3. ./gradlew :app:assembleDebug  (插件会自动编译)
4. 验证 ServiceRegistry.java 是否正确生成
5. 循环以上步骤
```

## 恢复模块模式的说明

简要提醒用户，当开发完成后可以使用以下步骤恢复到模块模式：

1. 将 `buildSrc/build.gradle` 重命名回 `buildSrc/build.gradle.bak`
2. 恢复根目录 build.gradle 中的插件依赖（取消注释）
3. 恢复 app/build.gradle 中的插件引用（取消注释）
4. 注释掉 app/build.gradle 中的 `apply plugin: AutoServiceRegisterPlugin`
5. 先发布插件到 Maven 仓库，再构建 app 模块

## 错误处理

- 如果 `buildSrc` 目录不存在，提示用户此项目可能不是 auto-service-android 项目
- 如果必要的文件不存在，给出明确的错误提示
- 如果文件已经被注释或重命名，告知用户当前状态
