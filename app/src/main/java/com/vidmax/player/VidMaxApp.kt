package com.vidmax.player

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Process
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.vidmax.player.ui.crash.CrashActivity
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

// 🔥 Coil এর ImageLoaderFactory ইমপ্লিমেন্ট করা হলো
class VidMaxApp : Application(), ImageLoaderFactory {
    
    override fun onCreate() {
        super.onCreate()
        Log.d("VidMaxApp", "VidMax Player initialized successfully!")

        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            val stringWriter = StringWriter()
            exception.printStackTrace(PrintWriter(stringWriter))
            
            val deviceInfo = """
            📱 Device Info:
            Brand: ${Build.BRAND}
            Model: ${Build.MODEL}
            Android Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})

            ⚠️ --- Crash Log ---

            """.trimIndent()

            val intent = Intent(this, CrashActivity::class.java)
            intent.putExtra("EXTRA_ERROR_DETAILS", deviceInfo + stringWriter.toString())
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)

            Process.killProcess(Process.myPid())
            exitProcess(1)
        }
    }

    // 🔥 গ্লোবাল ImageLoader কনফিগারেশন (ল্যাগ কমানোর ম্যাজিক)
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory()) // ভিডিও থেকে থাম্বনেইল বের করার জন্য
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25) // র‍্যামের ২৫% ক্যাশ হিসেবে ব্যবহার করবে
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.05) // স্টোরেজের ৫% ক্যাশ করবে, যাতে বারবার ল্যাগ না করে
                    .build()
            }
            .crossfade(true) // স্মুথ ট্রানজিশনের জন্য
            .build()
    }
}
