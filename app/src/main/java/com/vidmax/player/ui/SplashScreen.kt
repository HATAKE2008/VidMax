package com.vidmax.player.ui.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidmax.player.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(onSplashFinished: () -> Unit) {
  val scale = remember { Animatable(0.6f) }
  val alpha = remember { Animatable(0f) }

  LaunchedEffect(key1 = true) {
    // 🔥 দুটো অ্যানিমেশন একসাথে খুব দ্রুত (400ms) হবে
    launch { alpha.animateTo(1f, animationSpec = tween(400)) }
    launch { scale.animateTo(1f, animationSpec = tween(400)) }

    // মাত্র ৭০০ মিলিসেকেন্ড (০.৭ সেকেন্ড) পরেই মেইন স্ক্রিনে চলে যাবে
    delay(700)
    onSplashFinished()
  }

  Box(
      modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
      contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Image(
              painter = painterResource(id = R.drawable.app_logo),
              contentDescription = "App Logo",
              modifier =
                  Modifier.size(140.dp)
                      .scale(scale.value)
                      .alpha(alpha.value)
                      // 🔥 কোণাগুলো গোল করে দেওয়া হলো (Rounded Corners)
                      .clip(RoundedCornerShape(32.dp)))

          Spacer(modifier = Modifier.height(20.dp))

          Text(
              text = "VidMax",
              color = MaterialTheme.colorScheme.onBackground,
              fontSize = 36.sp,
              fontWeight = FontWeight.ExtraBold,
              letterSpacing = 2.sp,
              modifier = Modifier.alpha(alpha.value))
        }
      }
}
