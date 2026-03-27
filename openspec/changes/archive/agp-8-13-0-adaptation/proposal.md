## Why

Android Gradle Plugin (AGP) 8.13.0 引入了重大的 API 变更，特别是废弃了旧的 `VariantManager` API 并引入了新的 `AndroidComponentsExtension` API。当前 auto-service-plugin 使用 AGP 7.4.0 的 API，需要进行适配以支持 AGP 8.13.0，确保插件在新版本 AGP 下能够正常工作。

## What Changes

- **升级 AGP 依赖**: 将 auto-service-plugin 的 AGP 依赖从 7.4.0 升级到 8.13.0
- **迁移到新 API**: 将 `VariantManager` API 迁移到 `AndroidComponentsExtension.beforeVariants()` API
- **适配 Task 创建方式**: 更新 Task 创建和配置方式以符合 AGP 8.x 的要求
- **更新构建工具**: 升级 Gradle 版本以满足 AGP 8.13.0 的最低要求
- **更新 Kotlin 版本**: 升级 Kotlin 版本以兼容新的 AGP

## Capabilities

### New Capabilities

- `agp-8-13-api-migration`: 将插件代码迁移到 AGP 8.13.0 的 API
- `variant-api-adaptation`: 使用新的 variant API 替代旧的 variant manager API

### Modified Capabilities

- 无

## Impact

- **auto-service-plugin 模块**: 需要重写插件入口和 Task 注册逻辑
- **build.gradle.kts 文件**: 需要更新 AGP 版本和依赖配置
- **gradle-wrapper.properties**: 需要更新 Gradle 版本
- **测试用例**: 需要更新以适配新的 API 调用方式
- **向后兼容性**: 需要考虑是否保持对 AGP 7.x 的兼容性
