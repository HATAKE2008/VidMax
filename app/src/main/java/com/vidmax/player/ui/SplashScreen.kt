package com.vidmax.player.ui.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidmax.player.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * In-app splash shown right after the system splash screen.
 *
 * The system splash already displays the app logo on a pure black background,
 * so this composable starts with the logo fully visible and only settles it
 * with a short scale animation — no fade-in-from-black gap.
 */
@Composable
fun SplashScreen(onSplashFinished: () -> Unit) {
  val scale = remember { Animatable(0.85f) }
  val textAlpha = remember { Animatable(0f) }

  LaunchedEffect(key1 = true) {
    launch { scale.animateTo(1f, animationSpec = tween(300)) }
    launch { textAlpha.animateTo(1f, animationSpec = tween(350, delayMillis = 120)) }
    delay(700)
    onSplashFinished()
  }

  Box(
      modifier = Modifier.fillMaxSize().background(Color.Black),
      contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Image(
              painter = painterResource(id = R.drawable.app_logo),
              contentDescription = "App Logo",
              modifier =
                  Modifier.size(140.dp)
                      .scale(scale.value)
                      .clip(RoundedCornerShape(32.dp)))

          Spacer(modifier = Modifier.height(20.dp))

          Text(
              text = "VidMax",
              color = Color.White,
              fontSize = 36.sp,
              fontWeight = FontWeight.ExtraBold,
              letterSpacing = 2.sp,
              modifier = Modifier.scale(scale.value))
        }
      }
}
