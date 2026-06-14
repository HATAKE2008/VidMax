package com.vidmax.player.ui.screen

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidmax.player.R
import com.vidmax.player.ui.theme.*
import com.vidmax.player.viewmodel.LibraryViewModel
import com.vidmax.player.viewmodel.PlayerEngine

@Composable
fun SettingsScreen(viewModel: LibraryViewModel, onBack: () -> Unit) {
  val resumePlayback by viewModel.resumePlayback.collectAsState()
  val autoRotate by viewModel.autoRotate.collectAsState()
  val currentTheme by viewModel.appTheme.collectAsState()
  val audioBoost by viewModel.audioBoost.collectAsState()

  // 🔥 Engine State
  val currentEngine by viewModel.playerEngine.collectAsState()

  val context = LocalContext.current

  Column(
      modifier =
          Modifier.fillMaxSize()
              .background(MaterialTheme.colorScheme.background)
              .systemBarsPadding() // 🔥 স্ট্যাটাস বারের সাথে ওভারল্যাপ ফিক্স করার জন্য
      ) {
        // 🔥 Custom Top Bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically) {
              Box(
                  modifier =
                      Modifier.size(44.dp)
                          .clip(CircleShape)
                          .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                          .clickable { onBack() },
                  contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_arrow_back),
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp))
                  }

              Text(
                  text = "Settings",
                  color = MaterialTheme.colorScheme.onBackground,
                  fontSize = 20.sp,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.weight(1f).offset(x = (-22).dp),
                  textAlign = TextAlign.Center)
            }

        LazyColumn(
            modifier = Modifier.padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)) {

              // --- Theme Section ---
              item { SettingsSectionHeader(title = "App theme") }
              item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp)) {
                      item {
                        ThemeBubble(
                            "Classic", Color(0xFFF7B638), currentTheme == AppTheme.DEFAULT_DARK) {
                              viewModel.setAppTheme(AppTheme.DEFAULT_DARK)
                            }
                      }
                      item {
                        ThemeBubble(
                            "Chartreuse",
                            Color(0xFFE1FF51),
                            currentTheme == AppTheme.CHARTREUSE_GUNMETAL) {
                              viewModel.setAppTheme(AppTheme.CHARTREUSE_GUNMETAL)
                            }
                      }
                      item {
                        ThemeBubble(
                            "Cyberpunk",
                            Color(0xFF00E5FF),
                            currentTheme == AppTheme.CYBERPUNK_NEON) {
                              viewModel.setAppTheme(AppTheme.CYBERPUNK_NEON)
                            }
                      }
                      item {
                        ThemeBubble(
                            "Cinematic",
                            Color(0xFFE50914),
                            currentTheme == AppTheme.CINEMATIC_CRIMSON) {
                              viewModel.setAppTheme(AppTheme.CINEMATIC_CRIMSON)
                            }
                      }
                      item {
                        ThemeBubble(
                            "Aqua", Color(0xFF00D4FF), currentTheme == AppTheme.AQUA_BREEZE) {
                              viewModel.setAppTheme(AppTheme.AQUA_BREEZE)
                            }
                      }
                      item {
                        ThemeBubble(
                            "Gold", Color(0xFFFFD700), currentTheme == AppTheme.AMOLED_GOLD) {
                              viewModel.setAppTheme(AppTheme.AMOLED_GOLD)
                            }
                      }
                      item {
                        ThemeBubble(
                            "Dark",
                            Color(0xFF000000),
                            currentTheme == AppTheme.PURE_PITCH_BLACK,
                            borderColor = Color.DarkGray) {
                              viewModel.setAppTheme(AppTheme.PURE_PITCH_BLACK)
                            }
                      }
                      item {
                        ThemeBubble(
                            "Light",
                            Color(0xFFFFFFFF),
                            currentTheme == AppTheme.CLEAN_PEARL_WHITE,
                            borderColor = Color.LightGray) {
                              viewModel.setAppTheme(AppTheme.CLEAN_PEARL_WHITE)
                            }
                      }
                      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        item {
                          DynamicThemeBubble(currentTheme == AppTheme.DYNAMIC_COLOR) {
                            viewModel.setAppTheme(AppTheme.DYNAMIC_COLOR)
                          }
                        }
                      }
                    }
              }

              // --- Advanced Player Section ---
              item { SettingsSectionHeader(title = "Advanced player") }
              item {
                SettingsToggleRow(
                    title = "Volume Boost (200%)",
                    subtitle = "Amplify software sound beyond limits",
                    iconId = R.drawable.ic_wrench,
                    checked = audioBoost,
                    onCheckedChange = { viewModel.setAudioBoost(it) })
              }

              item { Spacer(modifier = Modifier.height(16.dp)) }

              // --- Player Engine Section ---
              item { SettingsSectionHeader(title = "Player engine") }
              item {
                DecoderOption(
                    title = "ExoPlayer (Media3)",
                    subtitle = "Default. Best for smooth playback",
                    iconId = R.drawable.ic_gear,
                    selected = currentEngine == PlayerEngine.EXO,
                    onClick = { viewModel.setPlayerEngine(PlayerEngine.EXO) })
                DecoderOption(
                    title = "MPV Engine (HW)",
                    subtitle = "Superfast hardware-accelerated engine",
                    iconId = R.drawable.ic_gear,
                    selected = currentEngine == PlayerEngine.MPV, // 🔥 VLC changed to MPV
                    onClick = { viewModel.setPlayerEngine(PlayerEngine.MPV) }) // 🔥 VLC changed to MPV
              }

              item { Spacer(modifier = Modifier.height(16.dp)) }

              // --- Playback Section ---
              item { SettingsSectionHeader(title = "Playback") }
              item {
                SettingsToggleRow(
                    title = "Resume Playback",
                    subtitle = "Continue from where you left off",
                    iconId = R.drawable.ic_play_arrow,
                    checked = resumePlayback,
                    onCheckedChange = { viewModel.setResumePlayback(it) })
                SettingsToggleRow(
                    title = "Auto Rotate",
                    subtitle = "Rotate screen with video orientation",
                    iconId = R.drawable.ic_rotate,
                    checked = autoRotate,
                    onCheckedChange = { viewModel.setAutoRotate(it) })
              }

              item { Spacer(modifier = Modifier.height(40.dp)) }

              // 🔥 Developer Connect Section (GitHub & Telegram)
              item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically) {
                      // Telegram Card
                      Column(
                          horizontalAlignment = Alignment.CenterHorizontally,
                          modifier =
                              Modifier.clip(RoundedCornerShape(16.dp))
                                  .clickable {
                                    val intent =
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("https://t.me/Hatake2008"))
                                    context.startActivity(intent)
                                  }
                                  .padding(12.dp)) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_telegram),
                                contentDescription = "Telegram",
                                modifier = Modifier.size(46.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "@Hatake2008",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold)
                          }

                      // GitHub Card
                      Column(
                          horizontalAlignment = Alignment.CenterHorizontally,
                          modifier =
                              Modifier.clip(RoundedCornerShape(16.dp))
                                  .clickable {
                                    val intent =
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("https://github.com/HATAKE2008"))
                                    context.startActivity(intent)
                                  }
                                  .padding(12.dp)) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_github),
                                contentDescription = "GitHub",
                                tint =
                                    MaterialTheme.colorScheme
                                        .onBackground, // থিমের সাথে মানানসই কালার
                                modifier = Modifier.size(46.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "HATAKE2008",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold)
                          }
                    }
              }
            }
      }
}

// 🔥 Pill Design Component
@Composable
private fun SettingsItemPill(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
    onClick: (() -> Unit)? = null
) {
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .padding(vertical = 6.dp)
              .clip(RoundedCornerShape(24.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
              .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
              .padding(horizontal = 16.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier =
                Modifier.size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center) {
              icon()
            }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
              text = title,
              color = MaterialTheme.colorScheme.onSurface,
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold)
          Text(
              text = subtitle,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              fontSize = 12.sp,
              modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        trailing()
      }
}

@Composable
private fun DecoderOption(
    title: String,
    subtitle: String,
    iconId: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
  SettingsItemPill(
      title = title,
      subtitle = subtitle,
      icon = {
        Icon(
            painter = painterResource(id = iconId),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurface)
      },
      trailing = {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors =
                RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary,
                    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant))
      },
      onClick = onClick)
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    iconId: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
  SettingsItemPill(
      title = title,
      subtitle = subtitle,
      icon = {
        Icon(
            painter = painterResource(id = iconId),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurface)
      },
      trailing = {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.background,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surface))
      })
}

@Composable
private fun SettingsSectionHeader(title: String) {
  Text(
      text = title,
      color = MaterialTheme.colorScheme.onBackground,
      fontSize = 14.sp,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(start = 8.dp, bottom = 8.dp, top = 8.dp))
}

@Composable
private fun ThemeBubble(
    name: String,
    color: Color,
    isSelected: Boolean,
    borderColor: Color = Color.Transparent,
    onClick: () -> Unit
) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Box(
        modifier =
            Modifier.size(56.dp)
                .clip(CircleShape)
                .background(color)
                .clickable { onClick() }
                .border(
                    width =
                        if (isSelected) 3.dp
                        else if (borderColor != Color.Transparent) 1.dp else 0.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.onBackground else borderColor,
                    shape = CircleShape),
        contentAlignment = Alignment.Center) {
          if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint =
                    if (color == Color(0xFFFFFFFF)) Color.Black
                    else if (color == Color(0xFF000000)) Color.White
                    else MaterialTheme.colorScheme.background,
                modifier = Modifier.size(28.dp))
          }
        }
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = name,
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 11.sp,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
  }
}

@Composable
private fun DynamicThemeBubble(isSelected: Boolean, onClick: () -> Unit) {
  val rainbowColors =
      listOf(Color.Red, Color.Magenta, Color.Blue, Color.Cyan, Color.Green, Color.Yellow, Color.Red)
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Box(
        modifier =
            Modifier.size(56.dp)
                .clip(CircleShape)
                .background(Brush.sweepGradient(rainbowColors))
                .clickable { onClick() }
                .border(
                    width = if (isSelected) 3.dp else 0.dp,
                    color =
                        if (isSelected) MaterialTheme.colorScheme.onBackground
                        else Color.Transparent,
                    shape = CircleShape),
        contentAlignment = Alignment.Center) {
          if (isSelected) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = Color.White,
                modifier = Modifier.size(28.dp))
          }
        }
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = "Monet",
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 11.sp,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
  }
}
