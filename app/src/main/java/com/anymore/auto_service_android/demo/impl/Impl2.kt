package com.anymore.auto_service_android.demo.impl

import android.util.Log
import com.anymore.auto.AutoService

/**
 * Created by anymore on 2022/4/3.
 */
@AutoService(Runnable::class,priority = 1,alias ="lym23")
class Impl2:Runnable {

    init {
        Log.e("lym","impl2 init")

    }
    override fun run() {
        Log.e("lym","impl2")
    }
}