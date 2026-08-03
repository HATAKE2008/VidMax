package com.vidmax.player.ui.spotify

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.vidmax.player.R
import com.vidmax.player.data.spotify.SpotifyAuth
import com.vidmax.player.data.spotify.SpotifyClient
import com.vidmax.player.data.spotify.SpotifyTokenManager
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Meld-স্টাইল Spotify লগইন স্ক্রিন — একটি embedded WebView দিয়ে
 * accounts.spotify.com-এর লগইন পেজ লোড হয়। লগইন সফল হলে open.spotify.com-এ
 * redirect হয়; তখন sp_dc / sp_key কুকি বের করে token fetch করা হয় (TOTP
 * সহ), সেশন সংরক্ষিত হয় এবং [onClose] ট্রিগার হয়।
 *
 * পুরো ফ্লো স্ক্রিনের ভেতরেই (self-contained) চলে — ViewModel-এর উপর
 * নির্ভরশীল নয়, Meld-এর মতো। লগইন শেষে মূল স্ক্রিন নিজে session refresh
 * করে নেয়।
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SpotifyLoginScreen(
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // WebView পেজ লোড হচ্ছে কিনা
    var isLoading by remember { mutableStateOf(true) }

    // কুকি বের করা / লগইন কল চলছে কিনা
    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }

    // লগইন ব্যর্থ হলে এরর মেসেজ
    var loginError by remember { mutableStateOf<String?>(null) }

    // Retry-এ WebView আবার লোড করতে retryCount বাড়ানো হয়
    var retryCount by remember { mutableIntStateOf(0) }

    // shouldOverrideUrlLoading আর onPageFinished এর মধ্যে race আটকাতে atomic guard
    val tokenFetchStarted = remember { AtomicBoolean(false) }

    // WebView instance ধরে রাখা হয় retry তে reload করার জন্য
    var webViewState by remember { mutableStateOf<WebView?>(null) }

    // স্ক্রিনটি ক্লোজ হলে Memory Leak এড়াতে WebView ক্লিনআপ
    DisposableEffect(Unit) {
        onDispose {
            webViewState?.stopLoading()
            webViewState?.destroy()
            webViewState = null
        }
    }

    // Retry: সব অবস্থা রিসেট করে WebView আবার লগইন পেজ লোড করো
    LaunchedEffect(retryCount) {
        if (retryCount > 0) {
            tokenFetchStarted.set(false)
            loginError = null
            isProcessing = false
            statusMessage = ""
            webViewState?.loadUrl(SpotifyAuth.LOGIN_URL)
        }
    }

    // কুকি থেকে sp_dc / sp_key বের করে token fetch + session সংরক্ষণ
    fun extractAndLogin(view: WebView?) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.flush()
        val allCookies = cookieManager.getCookie("https://open.spotify.com")
        val cookieMap = allCookies?.split(";")
            ?.mapNotNull { cookie ->
                val parts = cookie.trim().split("=", limit = 2)
                if (parts.size == 2 && parts[0].trim().isNotEmpty()) {
                    parts[0].trim() to parts[1].trim()
                } else {
                    null
                }
            }?.toMap() ?: emptyMap()

        val spDc = cookieMap["sp_dc"]
        if (spDc.isNullOrBlank()) {
            isProcessing = false
            statusMessage = context.getString(R.string.spotify_login_error_no_cookie)
            loginError = statusMessage
            tokenFetchStarted.set(false)
            return
        }

        val spKey = cookieMap["sp_key"] ?: ""
        isProcessing = true
        loginError = null
        statusMessage = context.getString(R.string.spotify_status_verifying)

        view?.stopLoading()
        view?.loadUrl("about:blank")

        scope.launch(Dispatchers.IO) {
            try {
                // 1. কুকি সংরক্ষণ
                val tokenManager = SpotifyTokenManager(context)
                tokenManager.saveCookies(spDc, spKey)

                // 2. TOTP সহ internal access token fetch
                withContext(Dispatchers.Main) {
                    statusMessage = context.getString(R.string.spotify_status_connecting)
                }
                val token = SpotifyAuth.fetchAccessToken(spDc, spKey).getOrThrow()
                SpotifyClient.accessToken = token.accessToken

                // 3. ইউজার প্রোফাইল লোড (নন-ফ্যাটাল)
                withContext(Dispatchers.Main) {
                    statusMessage = context.getString(R.string.spotify_status_loading_profile)
                }
                SpotifyClient.me().onSuccess { user ->
                    tokenManager.saveProfile(user.displayName ?: "", user.id)
                }

                // 4. টোকেন + প্রোফাইল সংরক্ষণ
                tokenManager.saveToken(token.accessToken, token.accessTokenExpirationTimestampMs)

                withContext(Dispatchers.Main) {
                    statusMessage = context.getString(R.string.spotify_login_success)
                }

                delay(300)

                withContext(Dispatchers.Main) {
                    isProcessing = false
                    onClose()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isProcessing = false
                    statusMessage = classifyLoginError(context, e)
                    loginError = statusMessage
                }
                tokenFetchStarted.set(false)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.spotify_login)) },
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_arrow_back),
                        contentDescription = "Back",
                    )
                }
            },
            windowInsets = WindowInsets(0.dp),
        )

        if (isLoading || isProcessing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.removeAllCookies(null)
                    cookieManager.flush()

                    WebView(ctx).apply {
                        cookieManager.setAcceptThirdPartyCookies(this, true)

                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        settings.javaScriptCanOpenWindowsAutomatically = true
                        settings.setSupportMultipleWindows(false)
                        settings.userAgentString = USER_AGENT_DESKTOP

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoading = true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false

                                if (url?.startsWith("https://open.spotify.com") == true &&
                                    tokenFetchStarted.compareAndSet(false, true)
                                ) {
                                    extractAndLogin(view)
                                }
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): Boolean {
                                val requestUrl = request?.url?.toString() ?: return false

                                if (requestUrl.startsWith("https://open.spotify.com")) {
                                    val spDc = extractSpDcCookie()
                                    if (spDc != null && tokenFetchStarted.compareAndSet(false, true)) {
                                        extractAndLogin(view)
                                        return true
                                    }
                                    // sp_dc এখনও প্রস্তুত নয় — onPageFinished-এ পুনরায় চেষ্টা
                                    return false
                                }

                                return false
                            }
                        }

                        webViewState = this
                        loadUrl(SpotifyAuth.LOGIN_URL)
                    }
                },
            )

            if (isProcessing || loginError != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (loginError == null) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        Text(
                            text = loginError ?: statusMessage.ifEmpty {
                                context.getString(R.string.spotify_logging_in)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (loginError != null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                        if (loginError != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            TextButton(
                                onClick = {
                                    loginError = null
                                    isProcessing = false
                                    statusMessage = ""
                                    tokenFetchStarted.set(false)
                                    retryCount++
                                },
                                modifier = Modifier.padding(horizontal = 16.dp),
                            ) {
                                Text(stringResource(R.string.spotify_retry))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * open.spotify.com ডোমেইনের sp_dc কুকি পড়ে। কুকি এখনও না এলে null ফেরে।
 */
private fun extractSpDcCookie(): String? {
    val cookieManager = CookieManager.getInstance()
    cookieManager.flush()
    val allCookies = cookieManager.getCookie("https://open.spotify.com")
    if (allCookies.isNullOrBlank()) return null

    return allCookies.split(";")
        .mapNotNull { cookie ->
            val parts = cookie.trim().split("=", limit = 2)
            if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
        }
        .firstOrNull { it.first == "sp_dc" && it.second.isNotBlank() }
        ?.second
}

/**
 * Backend-এর raw error message থেকে ব্যবহারকারী-বান্ধব এরর মেসেজ বানায়।
 */
private fun classifyLoginError(context: Context, e: Exception): String {
    val msg = e.message.orEmpty()
    return when {
        "anonymous" in msg || "expired" in msg ->
            context.getString(R.string.spotify_login_error_expired)
        "HTTP 403" in msg || "HTTP 401" in msg || "rejected" in msg ->
            context.getString(R.string.spotify_login_error_rejected)
        "gist" in msg.lowercase() || "nuance" in msg.lowercase() ||
            "unknownhost" in msg.lowercase() || "timeout" in msg.lowercase() ||
            "socket" in msg.lowercase() || "connect" in msg.lowercase() ->
            context.getString(R.string.spotify_login_error_network)
        else ->
            context.getString(R.string.spotify_login_error)
    }
}

/**
 * Desktop Chrome User-Agent — মোবাইল WebView-এ Spotify / সামাজিক লগইন
 * পেজগুলো স্টেবল দেখানোর জন্য জরুরি।
 */
private const val USER_AGENT_DESKTOP =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
