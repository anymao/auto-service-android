package com.anymore.auto_service_android.demo

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.anymore.auto.ServiceLoader

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ServiceLoader.load<Runnable>(alias = "yyy").forEach {
            it.run()
        }
    }

}