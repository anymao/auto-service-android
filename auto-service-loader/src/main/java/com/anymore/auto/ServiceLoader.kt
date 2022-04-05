package com.anymore.auto


class ServiceLoader<T> private constructor(private val clazz: Class<T>) : Iterable<T> {

    companion object {
        @JvmStatic
        fun <T> load(clazz: Class<T>) = ServiceLoader(clazz)

        inline fun <reified T> load() = load(T::class.java)
    }

    private var services: List<T> = ServiceRegistry.get(clazz)

    override fun iterator(): Iterator<T> = services.iterator()

    fun reload() {
        services = ServiceRegistry.get(clazz)
    }

}