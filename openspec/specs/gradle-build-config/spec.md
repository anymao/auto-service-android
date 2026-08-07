# Gradle 构建配置规范

## Requirements

### Requirement: 工具链基线

项目 SHALL 使用 AGP 8.13.0、Gradle Wrapper 8.13、JDK 17 和 Kotlin 2.1.0 构建。

#### Scenario: 发布级构建

- **WHEN** 在 JDK 17 下执行四模块测试和 `:app:assembleDebug :app:assembleRelease`
- **THEN** 所有任务成功
- **AND** 不使用 AGP 7.x 或旧 Transform API

### Requirement: 组件版本一致

项目 SHALL 通过根 `VERSION=0.0.13` 对齐 annotation、registry、loader 和 plugin。

#### Scenario: loader POM

- **WHEN** 执行 `:auto-service-loader:generatePomFileForMavenPublication`
- **THEN** POM 版本为 0.0.13
- **AND** compile 依赖包含 `auto-service-annotation:0.0.13`
- **AND** compile 依赖包含 `auto-service-registry:0.0.13`

#### Scenario: plugin POM

- **WHEN** 执行 `:auto-service-plugin:generatePomFileForMavenPublication`
- **THEN** artifactId 为 `auto-service-register`
- **AND** 版本为 0.0.13
- **AND** 不包含 annotation 运行时依赖

### Requirement: 无凭据普通构建

普通配置、测试和 assemble SHALL 在没有私服凭据时成功，且 SHALL NOT 配置需要认证的私服解析仓库。

#### Scenario: 无凭据配置

- **GIVEN** 环境变量和 Gradle 属性均没有有效的 `ALIYUN_USERNAME`、`ALIYUN_PASSWORD`
- **WHEN** 执行 `./gradlew projects`
- **THEN** 配置成功
- **AND** 日志不输出 Maven 用户名或密码

#### Scenario: 无凭据应用构建

- **GIVEN** 没有私服凭据，依赖已能从公开仓库或项目模块解析
- **WHEN** 执行 `:app:assembleDebug`
- **THEN** 构建成功

### Requirement: 发布凭据门禁

发布任务 SHALL 只从环境变量或用户级 Gradle 属性读取私服凭据，并 SHALL 在网络写入前验证两个值都存在。

#### Scenario: 缺少凭据

- **WHEN** 无有效凭据执行任一 `PublishToMavenRepository` 任务
- **THEN** 任务失败
- **AND** 错误为 `发布到私有 Maven 仓库需要 ALIYUN_USERNAME 和 ALIYUN_PASSWORD`
- **AND** 发布仓库没有写入

#### Scenario: 仓库安全

- **WHEN** 扫描受版本控制的项目属性文件
- **THEN** 不存在两个凭据的赋值
- **AND** 构建脚本不打印凭据值

### Requirement: 可缓存与可复现变换

服务注册变换 SHALL 建模为 `@CacheableTask`，class 输入 SHALL 使用 `@CompileClasspath`，输出 JAR SHALL 可复现。

#### Scenario: 未修改输入

- **WHEN** 连续两次执行同一变体注册任务
- **THEN** 第二次结果为 `UP-TO-DATE`

#### Scenario: 从缓存恢复

- **GIVEN** 第一次执行已写入 build cache
- **WHEN** 删除模块 build 输出后以相同输入再次执行
- **THEN** 结果为 `FROM-CACHE`
- **AND** 输出 SHA-256 与第一次一致

### Requirement: buildSrc 与发布插件基线一致

buildSrc 和 `auto-service-plugin` SHALL 使用 JDK 17、AGP 8.13.0 与相同插件实现入口。

#### Scenario: 本地应用验收

- **WHEN** app 直接应用 buildSrc 的 `AutoServiceRegisterPlugin`
- **THEN** Debug 和 Release 均生成可加载的注册表
- **AND** 行为与 TestKit 中发布插件一致
