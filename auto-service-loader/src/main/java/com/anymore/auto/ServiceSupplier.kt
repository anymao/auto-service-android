package com.anymore.auto

import java.util.function.Supplier

/**
 * Created by anymore on 2022/4/10.
 */
abstract class ServiceSupplier<T>(val alias: String = "") : Supplier<T>