package com.astro5star.app

import android.app.Application

class AstrologerApp : Application() {
    override fun onCreate() {
        super.onCreate()
         com.astro5star.app.data.remote.SocketManager.init()
    }
}
