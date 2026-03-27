## MODIFIED Requirements

### Requirement: Android Gradle Plugin 版本兼容性
项目 SHALL 使用 Android Gradle Plugin 7.4.0 版本进行构建。

#### Scenario: 正常编译
- **WHEN** 执行 `./gradlew :app:assembleDebug`
- **THEN** 编译成功且无错误

#### Scenario: 插件正常工作
- **WHEN** 执行 `./gradlew :app:assembleDebug` 且使用了 auto-service 插件
- **THEN** ServiceRegistry.java 正确生成并编译通过

### Requirement: Gradle Wrapper 版本
项目 SHALL 使用 Gradle 7.5 版本。

#### Scenario: Gradle 版本验证
- **WHEN** 执行 `./gradlew --version`
- **THEN** 显示 Gradle 版本为 7.5.x

### Requirement: JDK 版本要求
构建环境 SHALL 使用 JDK 11 或更高版本。

**原因**: AGP 7.x 强制要求 JDK 11+，这是硬性要求。

#### Scenario: JDK 版本验证
- **WHEN** 执行 `java -version`
- **THEN** 显示版本号 >= 11

#### Scenario: buildSrc 编译环境
- **WHEN** Gradle 编译 buildSrc 模块
- **THEN** 使用 JDK 11+ 进行编译

### Requirement: Android DSL 语法兼容性
项目 SHALL 使用 AGP 7.x 兼容的 DSL 配置语法。

#### Scenario: compileSdk 语法
- **WHEN** 检查 app/build.gradle
- **THEN** 使用 `compileSdk` 而非 `compileSdkVersion`

**DSL 变更对照表**:
| 旧语法 (AGP 4.x) | 新语法 (AGP 7.x) |
| `compileSdkVersion 30` | `compileSdk 33` |

#### Scenario: targetSdk 语法
- **WHEN** 检查 app/build.gradle
- **THEN** 使用 `targetSdk` 而非 `targetSdkVersion`

**DSL 变更对照表**:
| 旧语法 (AGP 4.x) | 新语法 (AGP 7.x) |
| `targetSdkVersion 30` | `targetSdk 33` |

#### Scenario: minSdk 语法
- **WHEN** 检查 app/build.gradle
- **THEN** 使用 `minSdk` 而非 `minSdkVersion`

**DSL 变更对照表**:
| 旧语法 (AGP 4.x) | 新语法 (AGP 7.x) |
| `minSdkVersion 17` | `minSdk 17` |

#### Scenario: buildToolsVersion 移除
- **WHEN** 检查 app/build.gradle
- **THEN** 不包含 `buildToolsVersion` 配置

**原因**: AGP 7.x 自动管理 buildTools 版本，无需手动配置。

### Requirement: Kotlin 版本兼容性
项目 SHALL 使用与 AGP 7.4.0 兼容的 Kotlin 版本（1.8.x）。

#### Scenario: Kotlin 编译成功
- **WHEN** 执行 `./gradlew :app:assembleDebug`
- **THEN** Kotlin 代码正确编译无错误

#### Scenario: Kotlin 版本验证
- **WHEN** 检查根目录 build.gradle
- **THEN** `ext.kotlin_version = "1.8.10"`

### Requirement: 插件模块 AGP 依赖版本
buildSrc 模块 SHALL 依赖 AGP 7.4.0（因为项目使用 buildSrc 模式调试插件）。

#### Scenario: compileOnly 依赖版本正确
- **WHEN** 检查 buildSrc/build.gradle
- **THEN** compileOnly AGP 依赖版本为 7.4.0

#### Scenario: implementation 依赖版本正确
- **WHEN** 检查 buildSrc/build.gradle
- **THEN** implementation AGP 依赖版本为 7.4.0

**变更对照表**:
| 旧版本 | 新版本 |
| `compileOnly("com.android.tools.build:gradle:1.3.1")` | `compileOnly("com.android.tools.build:gradle:7.4.0")` |
| `implementation("com.android.tools.build:gradle:3.5.0")` | `implementation("com.android.tools.build:gradle:7.4.0")` |

### Requirement: 版本一致性
项目 SHALL 确保所有模块使用一致的 AGP 版本。

#### Scenario: 根目录 build.gradle AGP 版本
- **WHEN** 检查根目录 build.gradle 的 buildscript dependencies
- **THEN** AGP 版本为 7.4.0

#### Scenario: buildSrc AGP 版本一致性
- **WHEN** 检查 buildSrc/build.gradle 的 dependencies
- **THEN** compileOnly 和 implementation 的 AGP 版本均为 7.4.0