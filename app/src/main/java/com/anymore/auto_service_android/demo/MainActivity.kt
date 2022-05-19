package com.anymore.auto_service_android.demo

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.anymore.auto.ServiceLoader
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ServiceLoader.load<Runnable>(alias = "lym").forEach {
            Log.d("lym", Thread.currentThread().name + it.toString())
            it.run()
        }
        for (i in 1..10) {
            thread {
                ServiceLoader.load<Runnable>().forEach {
                    Log.d("lym", Thread.currentThread().name + it.toString())
                    it.run()
                }
            }
        }
    }

}