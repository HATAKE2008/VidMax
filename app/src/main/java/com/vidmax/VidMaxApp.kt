package com.vidmax.player

import android.app.Application
import android.util.Log

class VidMaxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // CodeAssist এর Logger এর বদলে স্ট্যান্ডার্ড Android Log ব্যবহার করা হলো
        Log.d("VidMaxApp", "VidMax Player initialized successfully!")
    }
}
