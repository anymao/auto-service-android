package com.anymore.auto

import java.util.function.Supplier

/**
 * Created by anymore on 2022/4/10.
 */
abstract class ServiceSupplier<T>(val alias: String = "", private val singleton: Boolean = false) :
    Supplier<T> {

    @Volatile
    private var instance: T? = null

    abstract fun newInstance(): T

    final override fun get(): T = if (!singleton) {
        newInstance()
    } else {
        instance ?: synchronized(this) {
            instance ?: newInstance().also {
                instance = it
            }
        }
    }

}