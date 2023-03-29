package com.anymore.auto

import java.util.function.Supplier

/**
 * 单例服务提供者
 *
 * Created by anymore on 2022/5/20.
 */
internal abstract class SingletonServiceSupplier<T> : Supplier<T> {

    @Volatile
    private var instance: T? = null

    abstract fun newInstance(): T

    final override fun get() = instance ?: synchronized(this) {
        instance ?: newInstance().also {
            instance = it
        }
    }
}