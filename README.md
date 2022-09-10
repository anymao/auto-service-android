# auto-service-android

和[Google AutoService](https://github.com/google/auto/tree/master/service)用法类似的一个库

1. 增加了接口实现的优先级功能
2. 通过编译期间生成代码注册实现类，避免了运行时反射行为。

#### 用法

1. 添加私有仓库,并且引入插件

   ```groovy
   buildscript {
       repositories {
         	//other code...
           maven {
               credentials {
                   username '624f147cec85cdece2d2d16d'
                   password 'fSZwBHrhFOI_'
               }
               url 'https://packages.aliyun.com/maven/repository/2202395-release-jr0puW/'
           }
       }
       
       dependencies {
          // other code...
           classpath("com.anymore:auto-service-register:0.0.6")
       }
   }
   
   allprojects {
     repositories {
       //other code...
       maven {
         credentials {
           username '624f147cec85cdece2d2d16d'
           password 'fSZwBHrhFOI_'
         }
         url 'https://packages.aliyun.com/maven/repository/2202395-release-jr0puW/'
       }
     }
   }
   ```

2. application模块的build.gradle引入auto-service

   ```groovy
   plugins {
       id 'com.android.application'
       id 'kotlin-android'
       id 'auto-service'
   }
   ```

3. 添加依赖

   ```groovy
   api("com.anymore:auto-service-loader:0.0.6")
   ```

4. 定义接口的实现并使用@AutoService注解标记

   ```kotlin
   //单例实现Impl1
   @AutoService(Runnable::class,singleton = true)
   class Impl1:Runnable {
       override fun run() {
           Log.e("lym","impl1")
       }
   }
   
   @AutoService(Runnable::class,priority = -1)
   class Impl2:Runnable {
       override fun run() {
           Log.e("lym","impl2")
       }
   }
   ```

5. 调用

   ```kotlin
   ServiceLoader.load<Runnable>().forEach {
       it.run()
   }
   ```

   结果输出：

   ```
   2022-04-09 10:31:27.711 11824-11824/com.anymore.auto_service_android.demo E/lym: impl2
   2022-04-09 10:31:27.711 11824-11824/com.anymore.auto_service_android.demo E/lym: impl1
   ```

6. 开启编译预检查（可选，默认关闭）

   如果我们明确需要某个接口至少需要提供一个实现，则可通过开启预检查来检测是否存在被@AutoService标记的实现类，这样可以避免出现运行时候对于其实现一个也找不到的情况

   在application模块的build.gradle中开启编译预检查

   ```groovy
   autoService {
       checkImplementation=true
       require(Runnable.class.name)
       //检查Runnable接口且 alias="lym"的实现
       require(Runnable.class.name,"lym")
       require("java.util.concurrent.Callable")
   }
   ```

7. 排除注入

   如果我们依赖的其他aar中注入了某个实现类，但是我们不想将这个实现类注入进来，可以通过设置排除规则将这个实现排除掉。

   ```groovy
   autoService {
       excludeAlias("lym23")//排除掉所有别名为lym23的实现
       excludeClassName("com\\.anymore\\..*")//排除掉所有包名为com.anymore的实现
       exclude("com\\.anymore\\..*","lym.*")//排除掉所有包名为com.anymore 且 别名以 lym开头的实现
   }
   ```

#### Thanks

[service-loader-android](https://github.com/johnsonlee/service-loader-android)

[Google AutoService](https://github.com/google/auto/tree/master/service)