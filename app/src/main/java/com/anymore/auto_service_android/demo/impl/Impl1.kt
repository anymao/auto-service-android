package com.anymore.auto_service_android.demo.impl

import android.util.Log
import com.anymore.auto.AutoService
import java.util.concurrent.Callable

/**
 * Created by anymore on 2022/4/3.
 */
@AutoService(value = [Runnable::class, Callable::class], singleton = true)
class Impl1 : Runnable, Callable<Int> {

    init {
        Log.e("lym","impl1 init")
    }
    override fun run() {
        Log.e("lym", "impl1")
    }

    override fun call(): Int {
        Log.e("lym", "call impl1")
        return 1
    }
}