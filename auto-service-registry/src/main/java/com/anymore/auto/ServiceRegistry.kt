package com.anymore.auto

/**
 * Created by anymore on 2022/3/31.
 */
object ServiceRegistry {
    @JvmStatic
    fun <S> get(clazz: Class<S>, alias: String): List<SingletonServiceSupplier<S>> {
        throw UnsupportedOperationException()
    }
}