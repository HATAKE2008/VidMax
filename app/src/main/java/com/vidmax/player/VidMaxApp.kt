package com.vidmax.player

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Process
import android.util.Log
import com.vidmax.player.ui.crash.CrashActivity
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

@HiltAndroidApp
class VidMaxApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.d("VidMaxApp", "VidMax Player initialized successfully!")

        // NewPipe init — OkHttp downloader দিয়ে
        NewPipe.init(getDownloader())

        // 🍎 iOS client ব্যবহার করি — WEB client-এর "Sign in to confirm you're
        // not a bot" (LOGIN_REQUIRED) wall বাইপাস করে anonymous playback চলে।
        YoutubeStreamExtractor.setFetchIosClient(true)

        // Crash handler
        Thread.setDefaultUncaughtExceptionHandler { _, exception ->
            val stringWriter = StringWriter()
            exception.printStackTrace(PrintWriter(stringWriter))

            val deviceInfo = """
                📱 Device Info:
                Brand: ${Build.BRAND}
                Model: ${Build.MODEL}
                Android Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})

                ⚠️ --- Crash Log ---

            """.trimIndent()

            val intent = Intent(this, CrashActivity::class.java).apply {
                putExtra("EXTRA_ERROR_DETAILS", deviceInfo + stringWriter.toString())
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(intent)

            Process.killProcess(Process.myPid())
            exitProcess(1)
        }
    }

    private fun getDownloader(): Downloader {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                // YouTube User-Agent না থাকলে request block হয়
                val request = chain.request().newBuilder()
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 12; Pixel 5) " +
                        "AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "Chrome/120.0.0.0 Mobile Safari/537.36"
                    )
                    .build()
                chain.proceed(request)
            }
            .build()

        return object : Downloader() {
            override fun execute(request: Request): Response {
                val builder = okhttp3.Request.Builder()
                    .url(request.url())

                // Headers add
                request.headers().forEach { (key, values) ->
                    values.forEach { value -> builder.addHeader(key, value) }
                }

                // GET / POST handle
                val body = if (request.httpMethod() == "POST") {
                    (request.dataToSend() ?: ByteArray(0)).toRequestBody(null)
                } else null

                builder.method(request.httpMethod(), body)

                val response = client.newCall(builder.build()).execute()

                return Response(
                    response.code,
                    response.message,
                    response.headers.toMultimap(),    // null key problem নেই OkHttp-এ
                    response.body?.string(),
                    response.request.url.toString()
                )
            }
        }
    }
}
