package com.anymore.auto

import java.util.function.Supplier

/**
 * Created by anymore on 2023/3/28.
 */
internal class ServiceLazy<T>(private val supplier: Supplier<T>) : SingletonServiceSupplier<T>() {
    override fun newInstance() = supplier.get()
}