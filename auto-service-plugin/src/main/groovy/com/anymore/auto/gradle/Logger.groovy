package com.anymore.auto.gradle;

/**
 * Created by anymore on 2022/4/3.
 */
class Logger {

    public static final int VERBOSE = 2

    public static final int DEBUG = 3

    public static final int INFO = 4

    public static final int WARN = 5

    public static final int ERROR = 6

    public static int level = INFO

    static final String tag = "[AutoService]"

    static void v(String message) {
        if (isLoggable(level, VERBOSE)) {
            println(String.format("$tag[V]::>>$message"))
        }
    }

    static void d(String message) {
        if (isLoggable(level, DEBUG)) {
            println(String.format("$tag[D]::>>$message"))
        }
    }

    static void i(String message) {
        if (isLoggable(level, INFO)) {
            println(String.format("$tag[I]::>>$message"))
        }
    }

    static void w(String message) {
        if (isLoggable(level, WARN)) {
            println(String.format("$tag[W]::>>$message"))
        }
    }

    static void e(String message) {
        if (isLoggable(level, ERROR)) {
            System.err.println(String.format("$tag[E]::>>$message"))
        }
    }


    private static boolean isLoggable(int currentLevel, int level) {
        return currentLevel <= level
    }
}
