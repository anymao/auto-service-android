package com.anymore.auto


class ServiceLoader<T> private constructor(private val clazz: Class<T>) : Iterable<T> {

    companion object {
        @JvmStatic
        fun <T> load(clazz: Class<T>) = ServiceLoader(clazz)

        inline fun <reified T> load() = load(T::class.java)
    }

    private var services: List<T> = ServiceRegistry.get(clazz)

    override fun iterator(): Iterator<T> = services.iterator()

    /**
     * 获取最优先的 priority最小的那个实现
     */
    val firstPriority get() = firstOrNull()

    /**
     * 同[firstPriority],但是如果此接口没有一个实现的时候会抛出异常
     * 建议调用此方法的接口服务在application模块中开启预检查，
     * 开启后如果在模块中没有发现对应的服务实现，则会编译失败
     * e.g.
     * autoService {
     *      checkImplementation=false
     *      require(Runnable.class.name)
     *      require(Callable.class.name)
     * }
     */
    fun requireFirstPriority() =
        requireNotNull(firstPriority, { "there is no implementation of ${clazz.canonicalName}" })

    /**
     * 获取最不优先的 priority最大的那个实现
     */
    val lastPriority get() = lastOrNull()

    /**
     * 同[lastPriority],但是如果此接口没有一个实现的时候会抛出异常
     * @see requireFirstPriority
     */
    fun requireLastPriority() =
        requireNotNull(lastPriority, { "there is no implementation of ${clazz.canonicalName}" })

    /**
     * 重新加载,产生的新的实例
     */
    fun reload() {
        services = ServiceRegistry.get(clazz)
    }

}