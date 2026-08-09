package com.anymore.auto

/**
 * Created by anymore on 2023/3/28.
 */
internal class ServiceLazy<T>(private val supplier: ServiceFactory<T>) : SingletonServiceSupplier<T>() {
    override fun newInstance() = supplier.get()
}
