## 0. 开发环境配置（可选，用于快速迭代）

- [ ] 0.1 启用 buildSrc 开发模式
  - [ ] 0.1.1 将 `buildSrc/build.gradle.bak` 重命名为 `build.gradle`
  - [ ] 0.1.2 验证 buildSrc 配置正确
- [ ] 0.2 配置根目录 build.gradle
  - [ ] 0.2.1 注释掉 `classpath("com.anymore:auto-service-register:x.x.x")` 依赖
  - [ ] 0.2.2 记录注释的版本号，以便恢复
- [ ] 0.3 配置 app/build.gradle
  - [ ] 0.3.1 注释掉 `id 'auto-service'` 插件引用
  - [ ] 0.3.2 添加 `import com.anymore.auto.gradle.AutoServiceRegisterPlugin`
  - [ ] 0.3.3 添加 `apply plugin: AutoServiceRegisterPlugin`
- [ ] 0.4 验证 buildSrc 模式工作正常
  - [ ] 0.4.1 清理构建缓存 `./gradlew clean`
  - [ ] 0.4.2 测试构建 `./gradlew :app:assembleDebug`
  - [ ] 0.4.3 验证插件 Task 已创建
- [ ] 0.5 恢复模块模式（开发完成后）
  - [ ] 0.5.1 将 `buildSrc/build.gradle` 重命名为 `build.gradle.bak`
  - [ ] 0.5.2 恢复根目录 build.gradle 中的插件依赖
  - [ ] 0.5.3 恢复 app/build.gradle 中的插件引用
  - [ ] 0.5.4 验证模块模式工作正常

## 1. 升级构建工具版本

- [x] 1.1 更新 gradle-wrapper.properties 中的 Gradle 版本到 8.13
- [x] 1.2 更新 build.gradle 中的 AGP 依赖到 8.13.0
- [x] 1.3 更新 Kotlin 版本到 2.1.0
- [x] 1.4 更新 Java 编译版本配置（如果需要）

## 2. 更新插件入口 AutoServiceRegisterPlugin

- [x] 2.1 替换导入语句，使用 AGP 8.13.0 的新 API
  - [ ] 2.1.1 移除 `AppExtension` 和 `AppPlugin` 导入
  - [ ] 2.1.2 添加 `AndroidComponentsExtension` 导入
  - [ ] 2.1.3 添加必要的 AGP 8.x 类型导入
- [x] 2.2 替换 `AppExtension` 为 `AndroidComponentsExtension`
  - [ ] 2.2.1 使用 `project.extensions.getByType()` 获取扩展
  -[ ] 2.2.2 验证扩展获取成功
- [ ] 2.3 替换 `applicationVariants` 为 `onVariants` API 调用
  - [ ] 2.3.1 移除 `afterEvaluate` 代码块
  - [ ] 2.3.2 使用 `androidComponents.onVariants()` 注册回调
  - [ ] 2.3.3 确保 variant 遍历逻辑正确
- [ ] 2.4 替换 `buildDir` 为 `layout.buildDirectory`
  - [ ] 2.4.1 更新所有 `project.buildDir` 引用
  - [ ] 2.4.2 使用 `project.layout.buildDirectory` API
  - [ ] 2.4.3 正确使用 DirectoryProvider 和 `.get().asFile`
- [ ] 2.5 移除或重构 `afterEvaluate` 代码块
  - [ ] 2.5.1 移除 `afterEvaluate` 包装
  - [ ] 2.5.2 确保插件扩展注册在 apply() 顶层
  - [ ] 2.5.3 验证配置时机正确

## 3. 更新 Variant API 使用方式

- [ ] 3.1 使用 `onVariants()` 配置 variant 回调
  - [ ] 3.1.1 在 onVariants 闭包内访问 variant 属性
  - [ ] 3.1.2 验证 variant.name、variant.buildType 等属性可访问
  - [ ] 3.1.3 测试 debug/release 变体都能正确处理
- [ ] 3.2 更新 classpath 获取方式（使用 `variant.artifacts` API）
  - [ ] 3.2.1 尝试使用 `variant.compileConfiguration`
  - [ ] 3.2.2 尝试使用 `variant.artifacts.get()` 获取编译产物
  - [ ] 3.2.3 验证 classpath 包含 bootClasspath
  - [ ] 3.2.4 验证 classpath 包含依赖库
- [ ] 3.3 更新工作目录路径计算方式
  - [ ] 3.3.1 使用 `project.layout.buildDirectory.dir()`
  - [ ] 3.3.2 包含 variant.name 到路径中
  - [ ] 3.3.3 验证生成的目录路径与 AGP 7.x 兼容（或更新期望路径）
- [ ] 3.4 更新 Java 编译任务引用获取方式
  - [ ] 3.4.1 尝试 `variant.getTaskProvider("compileJava")`
  - [ ] 3.4.2 验证 Task Provider 可用
  - [ ] 3.4.3 测试 Kotlin 编译任务（如果存在）
- [ ] 3.5 更新 assemble 任务的依赖配置
  - [ ] 3.5.1 使用 `variant.tasks.assemble.configure { dependsOn(...) }`
  - [ ] 3.5.2 验证 assemble 任务正确依赖我们的 Task
  - [ ] 3.5.3 测试多 variant 的依赖关系

## 4. 更新 Task 创建和配置

- [ ] 4.1 更新 `AutoServiceRegisterTask` 的创建方式
  - [ ] 4.1.1 使用 `project.tasks.register()` 替代 `create()`
  - [ ] 4.1.2 配置 Task 的 classpath
  - [ ] 4.1.3 配置 Task 的 targetDir
  - [ ] 4.1.4 传递扩展配置（checkImplementation、requiredServices、exclusiveRules）
- [ ] 4.2 更新 `JavaCompile` Task 的创建和配置
  - [ ] 4.2.1 使用 `project.tasks.register()` 创建编译 Task
  - [ ] 4.2.2 配置源文件目录
  - [ ] 4.2.3 配置 classpath
  - [ ] 4.2.4 配置目标输出目录（使用 variant.artifacts）
  - [ ] 4.2.5 配置源码和目标兼容性
- [ ] 4.3 更新 Task 依赖关系设置（使用新的 API）
  - [ ] 4.3.1 确保编译任务在 Java/Kotlin 编译后运行
  - [ ] 4.3.2 确保编译 Task 在注册 Task 后运行
  - [ ] 4.3.3 使用 `.configure { dependsOn(...) }` 设置依赖
  - [ ] 4.3.4 验证 Task 执行顺序正确
- [ ] 4.4 验证 Task 输入输出配置正确
  - [ ] 4.4.1 检查 AutoServiceRegisterTask 是否需要增量构建注解
  - [ ] 4.4.2 添加 `@InputFiles`、`@OutputDirectory` 等注解（如需要）
  - [ ] 4.4.3 测试增量构建正确工作

## 5. 处理已废弃的 API 调用

- [ ] 5.1 替换所有 `project.buildDir` 为 `project.layout.buildDirectory`
- [ ] 5.2 检查并替换其他已废弃的 API 调用
- [ ] 5.3 更新目录访问方式使用 `ProjectLayout` API

## 6. 更新依赖库版本

- [ ] 6.1 检查并更新其他依赖库的兼容性
- [ ] 6.2 验证 Javassist、JavaPoet 等依赖的兼容性
- [ ] 6.3 更新任何不兼容的依赖库

## 7. 处理 Gro和y 特定问题

- [ ] 7.1 检查类型推断问题
  - [ ] 7.1.1 检查涉及泛型的调用
  - [ ] 7.1.2 添加必要的显式类型声明
  - [ ] 7.1.3 验证闭包参数类型正确
- [ ] 7.2 处理 Provider 类型延迟求值
  - [ ] 7.2.1 检查不必要的 `.get()` 调用
  - [ ] 7.2.2 尽可能保留 Provider 类型
  - [ ] 7.2.3 只在必要时调用 `.get()`
- [ ] 7.3 验证 Groovy DSL 与 AGP 8.x 兼容性
  - [ ] 7.3.1 测试插件在 Groovy 项目中工作
  - [ ] 7.3.2 测试插件在 Kotlin DSL 项目中工作

## 8. 测试和验证

- [ ] 8.1 测试 application 模块的 debug 编译
  - [ ] 8.1.1 运行 `./gradlew :app:assembleDebug`
  - [ ] 8.1.2 检查编译是否成功
  - [ ] 8.1.3 验证生成的 ServiceRegistry.java 存在
- [ ] 8.2 测试 application 模块的 release 编译
  - [ ] 8.2.1 运行 `./gradlew :app:assembleRelease`
  - [ ] 8.2.2 检查编译是否成功
  - [ ] 8.2.3 验证代码混淆不影响插件功能
- [ ] 8.3 测试多 variant 构建
  - [ ] 8.3.1 测试有 flavor 的项目
  - [ ] 8.3.2 验证所有 variant 都能正确处理
  - [ ] 8.3.3 检查 Task 是否为每个 variant 创建
- [ ] 8.4 验证 ServiceRegistry.java 正确生成
  - [ ] 8.4.1 检查文件位置正确
  - [ ] 8.4.2 检查文件内容格式正确
  - [ ] 8.4.3 检查所有服务实现都已注册
- [ ] 8.5 测试增量构建
  - [ ] 8.5.1 运行构建两次，第二次应该更快
  - [ ] 8.5.2 修改一个文件，验证只重新编译必要的部分
  - [ ] 8.5.3 使用 Gradle Build Scan 分析性能
- [ ] 8.6 测试插件配置功能
  - [ ] 8.6.1 测试 checkImplementation 功能
  - [ ] 8.6.2 测试 excludeAlias 功能
  - [ ] 8.6.3 测试 excludeClassName 功能
- [ ] 8.7 验证注解功能正常工作
  - [ ] 8.7.1 测试优先级排序
  - [ ] 8.7.2 测试单例模式
  - [ ] 8.7.3 测试别名功能
  - [ ] 8.7.4 测试排除规则

## 9. 更新文档

- [ ] 9.1 更新 CLAUDE.md 中的版本要求
  - [ ] 9.1.1 更新 JDK、Gradle、AGP、Kotlin 版本
  - [ ] 9.1.2 添加 AGP 8.13.0 特定的说明
- [ ] 9.2 更新 README 中的使用说明（如果有）
  - [ ] 9.2.1 更新版本要求
  - [ ] 9.2.2 更新示例代码（如果需要）
- [ ] 9.3 添加 AGP 8.13.0 迁移说明（如果有）
  - [ ] 9.3.1 记录重要的 API 变更
  - [ ] 9.3.2 提供迁移指南
  - [ ] 9.3.3 列出已知的兼容性问题

## 10. 代码清理和优化

- [ ] 10.1 移除未使用的导入
  - [ ] 10.1.1 检查所有 import 语句
  - [ ] 10.1.2 移除废弃 API 的导入
- [ ] 10.2 清理临时或调试代码
  - [ ] 10.2.1 移除调试日志
  - [ ] 10.2.2 移除临时测试代码
- [ ] 10.3 代码格式化和风格统一
  - [ ] 10.3.1 格式化 Groovy 代码
  - [ ] 10.3.2 统一命名规范
  - [ ] 10.3.3 添加必要的注释

## 11. 性能优化（可选）

- [ ] 11.1 分析构建性能
  - [ ] 11.1.1 使用 Gradle Build Scan
  - [ ] 11.1.2 识别性能瓶颈
- [ ] 11.2 优化 Task 执行
  - [ ] 11.2.1 利用 Provider 延迟求值
  - [ ] 11.2.2 优化增量构建
  - [ ] 11.2.3 考虑使用 WorkerExecutor 并行化
