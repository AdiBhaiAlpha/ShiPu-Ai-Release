package com.example

import android.app.Application

class ShiPuAiApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: ShiPuAiApplication
            private set
    }
}
