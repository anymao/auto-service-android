package com.anymore.auto

import kotlin.reflect.KClass


/**
 * 用于注解在你的实现类上，然后可以通过[com.anymore.auto.ServiceLoader]加载出来
 * @param priority 优先级，当一个接口有多个实现的时候，[priority]越小越靠前，
 * [com.anymore.auto.ServiceLoader]获取到的顺序是按照[priority]从小到大的
 * Created by anymore on 2022/4/3.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class AutoService(
    vararg val value: KClass<*>,
    val priority: Int = 0
)