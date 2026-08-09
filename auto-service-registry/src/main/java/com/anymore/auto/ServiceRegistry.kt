package com.anymore.auto

/**
 * Created by anymore on 2022/3/31.
 */
object ServiceRegistry {
    @JvmStatic
    fun <S> get(clazz: Class<S>, alias: String): List<ServiceFactory<S>> {
        throw IllegalStateException(
            "未生成服务注册表，请在 Android Application 模块中应用 auto-service 插件"
        )
    }
}
