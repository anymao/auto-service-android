package com.anymore.auto

import kotlin.reflect.KClass


@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class AutoService(
    vararg val value: KClass<*>,
    val priority: Int = 0
)