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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.vidmax.player.viewmodel.SpotifyViewModel
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Meld-স্টাইল Spotify লগইন স্ক্রিন — একটি embedded WebView দিয়ে
 * accounts.spotify.com-এর লগইন পেজ লোড হয়। লগইন সফল হলে open.spotify.com-এ
 * redirect হয়; তখন sp_dc / sp_key কুকি বের করে [SpotifyViewModel.loginWithCookies]
 * ডাকা হয়। সফল হলে [onClose] ট্রিগার হয়।
 *
 * Desktop User-Agent ব্যবহার করা জরুরি — মোবাইল WebView-এ Facebook/Google
 * লগইন JS ঠিকমতো কাজ করে না।
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SpotifyLoginScreen(
    viewModel: SpotifyViewModel,
    onClose: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // WebView পেজ লোড হচ্ছে কিনা
    var isLoading by remember { mutableStateOf(true) }

    // কুকি বের করা / লগইন কল চলছে কিনা (লোকাল + viewModel মিলিয়ে)
    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }

    // লগইন ব্যর্থ হলে এরর মেসেজ
    var loginError by remember { mutableStateOf<String?>(null) }

    // Retry-এ WebView আবার লোড করতে retryCount বাড়ানো হয়
    var retryCount by remember { mutableIntStateOf(0) }

    // shouldOverrideUrlLoading আর onPageFinished এর মধ্যে race আটকাতে atomic guard
    val tokenFetchStarted = remember { AtomicBoolean(false) }
    var loginStarted by remember { mutableStateOf(false) }

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

    // লগইন সফল হলে স্ক্রিন বন্ধ
    LaunchedEffect(uiState.isLoggedIn) {
        if (uiState.isLoggedIn) {
            onClose()
        }
    }

    // লগইন ব্যর্থ হলে viewModel এর error মেসেজটিকে লোকালাইজড মেসেজে রূপান্তর করে দেখাও
    LaunchedEffect(uiState.error, uiState.isLoginInProgress, loginStarted) {
        if (loginStarted && !uiState.isLoginInProgress && uiState.error != null) {
            isProcessing = false
            statusMessage = ""
            loginError = classifyLoginError(context, uiState.error)
            tokenFetchStarted.set(false)
        }
    }

    // Retry: সব অবস্থা রিসেট করে WebView আবার লগইন পেজ লোড করো
    LaunchedEffect(retryCount) {
        if (retryCount > 0) {
            tokenFetchStarted.set(false)
            loginStarted = false
            loginError = null
            isProcessing = false
            statusMessage = ""
            webViewState?.loadUrl(SpotifyAuth.LOGIN_URL)
        }
    }

    // কুকি থেকে sp_dc / sp_key বের করে repository-তে লগইন শুরু করো
    fun extractAndLogin() {
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
            statusMessage = ""
            loginError = context.getString(R.string.spotify_login_error_no_cookie)
            tokenFetchStarted.set(false)
            return
        }

        val spKey = cookieMap["sp_key"] ?: ""
        loginStarted = true
        isProcessing = true
        loginError = null
        statusMessage = context.getString(R.string.spotify_status_verifying)

        webViewState?.stopLoading()
        webViewState?.loadUrl("about:blank")

        viewModel.loginWithCookies(spDc, spKey)
    }

    val showProcessing = isProcessing || uiState.isLoginInProgress

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

        if (isLoading || showProcessing) {
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
                                    extractAndLogin()
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
                                        extractAndLogin()
                                        return true
                                    }
                                }
                                return false
                            }
                        }

                        webViewState = this
                        loadUrl(SpotifyAuth.LOGIN_URL)
                    }
                },
            )

            if (showProcessing || loginError != null) {
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
                                    loginStarted = false
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
private fun classifyLoginError(context: Context, message: String): String {
    val msg = message.lowercase()
    return when {
        "anonymous" in msg || "expired" in msg ->
            context.getString(R.string.spotify_login_error_expired)
        "403" in msg || "401" in msg || "rejected" in msg ->
            context.getString(R.string.spotify_login_error_rejected)
        "unknownhost" in msg || "timeout" in msg || "socket" in msg || "connect" in msg ->
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
