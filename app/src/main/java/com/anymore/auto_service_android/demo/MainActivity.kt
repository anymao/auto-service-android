package com.anymore.auto_service_android.demo

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.anymore.auto.ServiceLoader
import java.util.concurrent.Callable
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ServiceLoader.load<Runnable>().forEach {
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
        Thread.sleep(1500)
        ServiceLoader.load<Callable<Any>>().forEach {
            Log.e("lym",Thread.currentThread().name+it.toString())
            it.call()
        }
    }

}