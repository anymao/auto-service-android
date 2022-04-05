package com.anymore.auto.gradle;

/**
 * Created by anymore on 2022/4/3.
 */
class Logger {

    static final String tag = "[AutoService]::>>"

    static void d(String message) {
        println(String.format("$tag$message"))
    }

    static void tell(String message){
        println(String.format("$tag$message"))
    }
}
