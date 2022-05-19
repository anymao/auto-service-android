package com.anymore.auto_service_android.demo.impl

import android.util.Log
import com.anymore.auto.AutoService

/**
 * Created by anymore on 2022/4/3.
 */
@AutoService(Runnable::class,alias = "lym",singleton = true)
class Impl1:Runnable {
    override fun run() {
        Log.e("lym","impl1")
    }
}