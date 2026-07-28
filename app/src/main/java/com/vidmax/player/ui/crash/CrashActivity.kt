package com.vidmax.player.ui.crash

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.system.exitProcess

class CrashActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val errorDetails = intent.getStringExtra("EXTRA_ERROR_DETAILS") ?: "Unknown Error"

    setContent {
      MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(), color = Color(0xFF1E1E1E) // ডার্ক থিম
            ) {
              Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text(
                    text = "Oh snap! App Crashed \uD83D\uDE2D",
                    color = Color(0xFFFF5252),
                    fontSize = 22.sp,
                    modifier = Modifier.padding(bottom = 12.dp))

                // স্ক্রল করা যায় এমন টেক্সট বক্স (লগ পড়ার জন্য)
                Box(
                    modifier =
                        Modifier.weight(1f)
                            .fillMaxWidth()
                            .background(Color.Black, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())) {
                      Text(
                          text = errorDetails,
                          color = Color(0xFF00FF00), // হ্যাকারদের মত সবুজ টেক্সট
                          fontFamily = FontFamily.Monospace,
                          fontSize = 12.sp)
                    }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                      // কপি বাটন
                      Button(
                          onClick = {
                            val clipboard =
                                getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("VidMax Crash Log", errorDetails)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(
                                    this@CrashActivity,
                                    "Log Copied! Send it to Dev \uD83D\uDE80",
                                    Toast.LENGTH_LONG)
                                .show()
                          },
                          colors =
                              ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) {
                            Text("Copy Error", color = Color.White)
                          }

                      // ক্লোজ বাটন
                      Button(
                          onClick = {
                            finishAffinity()
                            exitProcess(0)
                          },
                          colors =
                              ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))) {
                            Text("Close App", color = Color.White)
                          }
                    }
              }
            }
      }
    }
  }
}
