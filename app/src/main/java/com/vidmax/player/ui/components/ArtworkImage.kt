package com.vidmax.player.ui.components

import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder

/**
 * YouTube artwork loader — always tries the high-res maxresdefault thumbnail
 * first, falling back to the stored hqdefault URL on failure. Never shows a
 * gray/broken card: if the URL set is empty it simply renders nothing.
 *
 * @param videoId           YouTube video id; when present a maxresdefault URL
 *                          is tried before [fallbackUrl].
 * @param fallbackUrl       hqdefault / already stored thumbnail (kept as-is).
 * @param loadingPlaceholder optional composable shown while loading (e.g.
 *                          shimmer); it fades out when the art is ready.
 * @param requestBuilder    optional extra Glide tuning (e.g. disk cache).
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun ArtworkImage(
    videoId: String?,
    fallbackUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    loadingPlaceholder: (@Composable () -> Unit)? = null,
    requestBuilder: (RequestBuilder<Drawable>) -> RequestBuilder<Drawable> = { it },
) {
    val highResUrl = videoId?.let { "https://i.ytimg.com/vi/$it/maxresdefault.jpg" }
    val primaryUrl = highResUrl ?: fallbackUrl
    if (primaryUrl == null) return

    val fallbackOnError = highResUrl != null && fallbackUrl != null && highResUrl != fallbackUrl
    val shimmerPlaceholder = if (loadingPlaceholder != null) placeholder { loadingPlaceholder() } else null

    GlideImage(
        model = primaryUrl,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        loading = shimmerPlaceholder,
        requestBuilderTransform = { builder ->
            val tuned = requestBuilder(builder)
            if (fallbackOnError) tuned.error(fallbackUrl) else tuned
        },
    )
}
