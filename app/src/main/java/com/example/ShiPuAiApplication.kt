package com.example

import android.app.Application
import android.os.StrictMode
import android.util.Log

class ShiPuAiApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this

        Log.d("ShiPuAi_Startup", "STARTUP: Application.onCreate BEGIN")

        // Enable StrictMode in debug builds to catch main-thread disk/network operations
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build()
            )
        }

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("ShiPuAi_Crash", "[STARTUP_STAGE: UNCAUGHT_EXCEPTION] Crash in thread ${thread.name}", throwable)
            val rootCause = getRootCause(throwable)
            val stackElement = rootCause.stackTrace.firstOrNull { it.className.startsWith("com.example") }
            Log.e("ShiPuAi_Crash", "CRASH ROOT CAUSE: ${rootCause.javaClass.name}: ${rootCause.message}")
            if (stackElement != null) {
                Log.e("ShiPuAi_Crash", "CRASHING FILE: ${stackElement.fileName}")
                Log.e("ShiPuAi_Crash", "CRASHING LINE: ${stackElement.lineNumber}")
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        Log.d("ShiPuAi_Startup", "STARTUP: Application.onCreate END")
    }

    private fun getRootCause(throwable: Throwable): Throwable {
        var cause: Throwable? = throwable
        while (cause?.cause != null && cause.cause != cause) {
            cause = cause.cause
        }
        return cause ?: throwable
    }

    companion object {
        lateinit var instance: ShiPuAiApplication
            private set
        val isInitialized: Boolean
            get() = ::instance.isInitialized
    }
}

