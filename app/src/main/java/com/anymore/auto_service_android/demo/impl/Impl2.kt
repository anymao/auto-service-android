package com.anymore.auto_service_android.demo.impl

import android.util.Log
import com.anymore.auto.AutoService

/**
 * Created by anymore on 2022/4/3.
 */
@AutoService(Runnable::class,priority = -1)
class Impl2:Runnable {
    override fun run() {
        Log.e("lym","impl2")
    }
}