package com.vidmax.player.utils

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
            return "YouTube blocked anonymous access on this network.\nTry again in a few minutes or switch networks."
        }
        return message
    }
}
