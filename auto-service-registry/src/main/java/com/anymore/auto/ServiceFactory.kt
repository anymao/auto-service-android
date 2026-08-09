package com.anymore.auto

/** 框架内部使用的服务实例工厂，不依赖 Android API 24 的 Supplier。 */
fun interface ServiceFactory<out T> {
    fun get(): T
}
