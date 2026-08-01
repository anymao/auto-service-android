package com.anymore.auto

import java.util.function.Supplier

/**
 * Created by anymore on 2022/3/31.
 */
object ServiceRegistry {
    @JvmStatic
    fun <S> get(clazz: Class<S>, alias: String): List<Supplier<S>> {
        throw UnsupportedOperationException()
    }
}
