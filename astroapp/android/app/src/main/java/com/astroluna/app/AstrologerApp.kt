package com.astroluna.app

import android.app.Application

class AstrologerApp : Application() {
    override fun onCreate() {
        super.onCreate()
         com.astroluna.app.data.remote.SocketManager.init()
    }
}
