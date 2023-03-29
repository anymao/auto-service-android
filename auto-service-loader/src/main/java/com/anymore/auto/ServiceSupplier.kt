package com.anymore.auto

import java.util.function.Supplier

/**
 * Created by anymore on 2022/4/10.
 */
internal class ServiceSupplier<T>(val alias: String = "", val supplier: Supplier<out T>) :
    Supplier<T> {

    override fun get() = supplier.get()
}