package com.vidmax.player.utils

/**
 * Holds the optional YouTube cookie used to avoid anonymous bot-blocks
 * ("Sign in to confirm you're not a bot" / LOGIN_REQUIRED).
 *
 * When the user pastes a cookie (from a logged-in browser session) into
 * Settings → YouTube, the OkHttp downloader attaches it to every YouTube
 * request, so YouTube treats the app as an authenticated session instead of
 * an anonymous datacenter IP.
 */
object YoutubeCookie {
    @Volatile
    var value: String = ""
}

object ErrorMessages {
    private val BOT_BLOCK_PATTERNS = listOf(
        "login_required",
        "sign in to confirm",
        "you're not a bot",
        "you are not a bot",
        "not a robot",
        "re_captcha",
        "recaptcha",
        "account associated with this ip",
        "temporarily blocked",
        "unusual traffic"
    )

    /**
     * Maps YouTube anonymous bot-blocks to a friendly, actionable message.
     * Everything else is passed through unchanged.
     */
    fun friendly(message: String?): String {
        if (message.isNullOrBlank()) return "Unable to play song"
        val lower = message.lowercase()
        if (BOT_BLOCK_PATTERNS.any { lower.contains(it) }) {
            return "YouTube temporarily blocked anonymous access.\nAdd your YouTube cookie in Settings → YouTube to fix this."
        }
        return message
    }
}
