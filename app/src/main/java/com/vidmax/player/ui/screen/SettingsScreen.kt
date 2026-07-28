package com.vidmax.player.ui.screen

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidmax.player.R
import com.vidmax.player.ui.theme.AppTheme
import com.vidmax.player.viewmodel.DarkMode
import com.vidmax.player.viewmodel.LibraryViewModel
import com.vidmax.player.viewmodel.PlayerEngine

@Composable
fun SettingsScreen(viewModel: LibraryViewModel, onBack: () -> Unit) {
    val resumePlayback by viewModel.resumePlayback.collectAsState()
    val autoRotate by viewModel.autoRotate.collectAsState()
    val audioBoost by viewModel.audioBoost.collectAsState()
    val currentEngine by viewModel.playerEngine.collectAsState()
    val currentTheme by viewModel.appTheme.collectAsState()
    val darkMode by viewModel.darkMode.collectAsState()
    val amoledMode by viewModel.amoledMode.collectAsState()
    val context = LocalContext.current

    val isSystemDark = isSystemInDarkTheme()
    val isCurrentlyDark = when (darkMode) {
        DarkMode.Dark -> true
        DarkMode.Light -> false
        DarkMode.System -> isSystemDark
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
    ) {

        // ── Top Bar ──────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // Back button — neutral surface color, never theme-tinted
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrow_back),
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Title — properly centered in the Box
            Text(
                text = "Settings",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Subtle separator under top bar
        Divider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            thickness = 0.5.dp
        )

        // ── Content ──────────────────────────────────────────────────────────
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 40.dp)
        ) {

            // ── Dark / Light / System toggle ──────────────────────────────
            item { SettingsSectionHeader(title = "Theme") }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .height(48.dp)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(50)
                        )
                        .clip(RoundedCornerShape(50))
                ) {
                    val options = listOf(DarkMode.Dark, DarkMode.Light, DarkMode.System)
                    options.forEach { mode ->
                        val isSelected = darkMode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                                    else Color.Transparent
                                )
                                .clickable { viewModel.setDarkMode(mode) },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    text = mode.name,
                                    color = if (isSelected)
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            // ── App Theme picker ──────────────────────────────────────────
            item { SettingsSectionHeader(title = "App Theme", paddingTop = 20.dp) }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(vertical = 8.dp, horizontal = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(AppTheme.values()) { theme ->
                        if (theme.isDynamic && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@items
                        AppThemePreviewItem(
                            theme = theme,
                            isSelected = currentTheme == theme,
                            isDark = isCurrentlyDark,
                            isAmoled = amoledMode,
                            onClick = { viewModel.setAppTheme(theme) }
                        )
                    }
                }
            }

            // ── AMOLED toggle ─────────────────────────────────────────────
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                SettingsToggleRow(
                    title = "AMOLED Black Mode",
                    subtitle = "Pure black background to save battery on OLED",
                    iconId = R.drawable.ic_brightness,
                    checked = amoledMode,
                    enabled = isCurrentlyDark,
                    onCheckedChange = { viewModel.setAmoledMode(it) }
                )
            }

            // ── Advanced player ───────────────────────────────────────────
            item {
                SettingsDivider()
                SettingsSectionHeader(title = "Advanced Player", paddingTop = 4.dp)
            }
            item {
                SettingsToggleRow(
                    title = "Volume Boost (200%)",
                    subtitle = "Amplify software sound beyond device limits",
                    iconId = R.drawable.ic_wrench,
                    checked = audioBoost,
                    onCheckedChange = { viewModel.setAudioBoost(it) }
                )
            }

            // ── Player engine ─────────────────────────────────────────────
            item {
                SettingsDivider()
                SettingsSectionHeader(title = "Player Engine", paddingTop = 4.dp)
            }
            item {
                DecoderOption(
                    title = "ExoPlayer  ·  Media3",
                    subtitle = "Default — smooth, battery-efficient playback",
                    iconId = R.drawable.ic_gear,
                    selected = currentEngine == PlayerEngine.EXO,
                    onClick = { viewModel.setPlayerEngine(PlayerEngine.EXO) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                DecoderOption(
                    title = "MPV Engine  ·  HW",
                    subtitle = "Hardware-accelerated, codec-rich powerhouse",
                    iconId = R.drawable.ic_gear,
                    selected = currentEngine == PlayerEngine.MPV,
                    onClick = { viewModel.setPlayerEngine(PlayerEngine.MPV) }
                )
            }

            // ── Playback ──────────────────────────────────────────────────
            item {
                SettingsDivider()
                SettingsSectionHeader(title = "Playback", paddingTop = 4.dp)
            }
            item {
                SettingsToggleRow(
                    title = "Resume Playback",
                    subtitle = "Continue from where you left off",
                    iconId = R.drawable.ic_play_arrow,
                    checked = resumePlayback,
                    onCheckedChange = { viewModel.setResumePlayback(it) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                SettingsToggleRow(
                    title = "Auto Rotate",
                    subtitle = "Rotate screen with video orientation",
                    iconId = R.drawable.ic_rotate,
                    checked = autoRotate,
                    onCheckedChange = { viewModel.setAutoRotate(it) }
                )
            }

            // ── About / Links ─────────────────────────────────────────────
            item { SettingsDivider() }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SocialLinkButton(
                        iconId = R.drawable.ic_telegram,
                        label = "@Hatake2008",
                        isTinted = false,
                        url = "https://t.me/Hatake2008",
                        context = context
                    )
                    SocialLinkButton(
                        iconId = R.drawable.ic_github,
                        label = "HATAKE2008",
                        isTinted = true,
                        url = "https://github.com/HATAKE2008/vidamx",
                        context = context
                    )
                }
            }

            // Version chip at the very bottom
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "VidMax · Open Source · MIT License",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// ── Theme Preview Card ────────────────────────────────────────────────────────

@Composable
fun AppThemePreviewItem(
    theme: AppTheme,
    isSelected: Boolean,
    isDark: Boolean,
    isAmoled: Boolean,
    onClick: () -> Unit
) {
    val bgColor = when {
        isDark && isAmoled -> Color.Black
        isDark -> theme.backgroundDark
        else -> theme.backgroundLight
    }
    val primary = if (isDark) theme.primaryDark else theme.primaryLight
    val secondary = if (isDark) theme.secondaryDark else theme.secondaryLight
    val surfaceColor = when {
        isDark && isAmoled -> Color(0xFF111111)
        isDark -> Color.White.copy(alpha = 0.1f)
        else -> Color.White
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .width(80.dp)
                .height(132.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(bgColor)
                .border(
                    width = if (isSelected) 2.5.dp else 0.8.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(14.dp)
                )
                .clickable { onClick() }
                .padding(10.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(surfaceColor)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(50))
                            .background(primary)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(secondary)
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(surfaceColor)
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(primary)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = theme.name,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Shared pill component ─────────────────────────────────────────────────────

@Composable
private fun SettingsItemPill(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(
                if (onClick != null && enabled) Modifier.clickable { onClick() } else Modifier
            )
            .alpha(if (enabled) 1f else 0.45f)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp),
                lineHeight = 16.sp
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        trailing()
    }
}

// ── Concrete setting rows ─────────────────────────────────────────────────────

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
                modifier = Modifier.size(19.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        trailing = {
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary,
                    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        },
        onClick = onClick
    )
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    iconId: Int,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    SettingsItemPill(
        title = title,
        subtitle = subtitle,
        enabled = enabled,
        icon = {
            Icon(
                painter = painterResource(id = iconId),
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.background,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    )
}

// ── Social link button ────────────────────────────────────────────────────────

@Composable
private fun SocialLinkButton(
    iconId: Int,
    label: String,
    isTinted: Boolean,
    url: String,
    context: android.content.Context
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
            }
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            if (isTinted) {
                Icon(
                    painter = painterResource(id = iconId),
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(26.dp)
                )
            } else {
                Image(
                    painter = painterResource(id = iconId),
                    contentDescription = label,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun SettingsSectionHeader(
    title: String,
    paddingTop: androidx.compose.ui.unit.Dp = 8.dp
) {
    Text(
        text = title.uppercase(),
        color = MaterialTheme.colorScheme.primary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 10.dp, top = paddingTop)
    )
}

@Composable
private fun SettingsDivider() {
    Divider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        thickness = 0.5.dp,
        modifier = Modifier.padding(vertical = 20.dp)
    )
}
