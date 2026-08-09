package com.anymore.auto

/**
 * Created by anymore on 2022/4/10.
 */
internal class ServiceSupplier<T>(val alias: String = "", val supplier: ServiceFactory<T>) :
    ServiceFactory<T> {

    override fun get() = supplier.get()
}
