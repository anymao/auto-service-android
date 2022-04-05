package com.anymore.auto_service_android.demo

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.anymore.auto.ServiceLoader
import java.util.*

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ServiceLoader.load<Runnable>().forEach {
            it.run()
        }
    }

}