@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.vidmax.player.ui.player

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.vidmax.player.viewmodel.AspectRatioMode
import com.vidmax.player.viewmodel.LoopMode
import com.vidmax.player.viewmodel.PlayerEngine
import com.vidmax.player.viewmodel.PlayerViewModel
import `is`.xyz.mpv.MPVLib

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettingsSheet(
    viewModel: PlayerViewModel,
    currentPath: String,
    audioBoostEnabled: Boolean,
    currentPlaybackSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    videoScale: Float,
    onVideoScaleChange: (Float, Offset) -> Unit,
    exoPlayer: Player?,
    bgPlayEnabled: Boolean,
    onBgPlayToggle: (Boolean) -> Unit,
    onPickSubtitle: () -> Unit,
    verticalGesturesEnabled: Boolean,
    onVerticalGesturesChange: (Boolean) -> Unit,
    horizontalSeekEnabled: Boolean,
    onHorizontalSeekChange: (Boolean) -> Unit,
    doubleTapSeekSeconds: Int,
    onDoubleTapSeekSecondsChange: (Int) -> Unit,
    autoHideControls: Boolean,
    onAutoHideControlsChange: (Boolean) -> Unit,
    onEngineChange: (PlayerEngine) -> Unit,
    onOpenDecoder: () -> Unit,
    onOpenTimer: () -> Unit,
    onOpenZoom: () -> Unit,
    onOpenAspect: () -> Unit,
    onOpenSpeedSync: () -> Unit,
    onOpenAudio: () -> Unit,
    onOpenSubtitle: () -> Unit,
    onRotateScreen: () -> Unit,
    onToggleImmersive: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val prefs = remember { context.getSharedPreferences("vidmax_settings", Context.MODE_PRIVATE) }

    val currentEngine by viewModel.currentEngine.collectAsState()
    val loopMode by viewModel.loopMode.collectAsState()
    val aspect by viewModel.aspectRatio.collectAsState()
    val subtitleSize by viewModel.subtitleSize.collectAsState()

    var autoRotate by remember { mutableStateOf(prefs.getBoolean("auto_rotate", true)) }

    val primary = MaterialTheme.colorScheme.primary

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF1E1E1E)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "Player Settings",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // ---------------- Playback ----------------
            SettingsSectionHeader("Playback", Icons.Default.PlayArrow)
            SettingsSwitchRow(
                title = "Background Playback",
                subtitle = "Continue audio when app is backgrounded",
                icon = Icons.Outlined.Headset,
                checked = bgPlayEnabled,
                onCheckedChange = onBgPlayToggle
            )
            SettingsNavRow(
                title = "Sleep Timer",
                subtitle = "Auto-pause after a set time",
                icon = Icons.Outlined.Timer,
                onClick = onOpenTimer
            )
            SettingsChipRow(
                title = "Repeat Mode",
                icon = Icons.Outlined.Repeat,
                options = listOf("Off", "One", "All"),
                selectedIndex = when (loopMode) {
                    LoopMode.NONE -> 0
                    LoopMode.ONE -> 1
                    LoopMode.ALL -> 2
                }
            ) { index ->
                viewModel.setLoopMode(
                    when (index) {
                        0 -> LoopMode.NONE
                        1 -> LoopMode.ONE
                        else -> LoopMode.ALL
                    }
                )
            }

            HorizontalDivider(Color.White.copy(alpha = 0.08f))

            // ---------------- Video ----------------
            SettingsSectionHeader("Video", Icons.Outlined.Movie)
            SettingsChipRow(
                title = "Player Engine",
                icon = Icons.Outlined.Memory,
                options = listOf("ExoPlayer", "MPV (HW)"),
                selectedIndex = if (currentEngine == PlayerEngine.EXO) 0 else 1
            ) { index ->
                onEngineChange(if (index == 0) PlayerEngine.EXO else PlayerEngine.MPV)
            }

            HorizontalDivider(Color.White.copy(alpha = 0.08f))

            // ---------------- Zoom ----------------
            SettingsSectionHeader("Zoom", Icons.Outlined.ZoomIn)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("${(videoScale * 100).toInt()}%", color = primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Slider(
                    value = videoScale,
                    onValueChange = { newZoom -> onVideoScaleChange(newZoom / videoScale, Offset.Zero) },
                    valueRange = 1f..4f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = primary,
                        activeTrackColor = primary,
                        inactiveTrackColor = primary.copy(alpha = 0.3f)
                    )
                )
                TextButton(onClick = { onVideoScaleChange(1f / videoScale, Offset.Zero) }) {
                    Text("Reset", color = Color.White)
                }
            }

            HorizontalDivider(Color.White.copy(alpha = 0.08f))

            // ---------------- Scaling / Aspect Ratio ----------------
            SettingsSectionHeader("Scaling & Aspect Ratio", Icons.Outlined.AspectRatio)
            SettingsChipRow(
                title = "Aspect Ratio",
                icon = Icons.Outlined.AspectRatio,
                options = listOf("Fit", "Crop", "Stretch"),
                selectedIndex = when (aspect) {
                    AspectRatioMode.FIT -> 0
                    AspectRatioMode.FILL -> 1
                    AspectRatioMode.STRETCH -> 2
                }
            ) { index ->
                when (index) {
                    0 -> viewModel.setAspectRatio(AspectRatioMode.FIT)
                    1 -> viewModel.setAspectRatio(AspectRatioMode.FILL)
                    else -> viewModel.setAspectRatio(AspectRatioMode.STRETCH)
                }
            }

            HorizontalDivider(Color.White.copy(alpha = 0.08f))

            // ---------------- Audio ----------------
            SettingsSectionHeader("Audio", Icons.Outlined.Audiotrack)
            SettingsSwitchRow(
                title = "Volume Boost",
                subtitle = "Boost volume above 100%",
                icon = Icons.Outlined.VolumeUp,
                checked = audioBoostEnabled,
                onCheckedChange = { /* toggled from the player bottom bar */ }
            )
            SettingsNavRow(
                title = "Audio Tracks",
                subtitle = "Choose the active audio track",
                icon = Icons.Outlined.Audiotrack,
                onClick = onOpenAudio
            )

            HorizontalDivider(Color.White.copy(alpha = 0.08f))

            // ---------------- Subtitle ----------------
            SettingsSectionHeader("Subtitle", Icons.Outlined.Subtitles)
            SettingsNavRow(
                title = "Open Local Subtitle",
                subtitle = "Load a subtitle file",
                icon = Icons.Outlined.FolderOpen,
                onClick = onPickSubtitle
            )
            SettingsNavRow(
                title = "Subtitle Tracks",
                subtitle = "Choose the active subtitle track",
                icon = Icons.Outlined.Subtitles,
                onClick = onOpenSubtitle
            )
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("Subtitle Size", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Slider(
                    value = subtitleSize,
                    onValueChange = { size ->
                        viewModel.setSubtitleSize(size)
                        if (currentEngine == PlayerEngine.MPV) {
                            try {
                                MPVLib.setPropertyDouble("sub-scale", size / 16.0)
                            } catch (e: Exception) {}
                        }
                    },
                    valueRange = 10f..40f,
                    colors = SliderDefaults.colors(
                        thumbColor = primary,
                        activeTrackColor = primary,
                        inactiveTrackColor = primary.copy(alpha = 0.3f)
                    )
                )
            }

            HorizontalDivider(Color.White.copy(alpha = 0.08f))

            // ---------------- Speed ----------------
            SettingsSectionHeader("Speed", Icons.Outlined.Speed)
            SettingsChipRow(
                title = "Playback Speed",
                icon = Icons.Outlined.Speed,
                options = listOf("0.5x", "0.75x", "1x", "1.25x", "1.5x", "2x"),
                selectedIndex = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).indexOf(currentPlaybackSpeed).coerceAtLeast(0)
            ) { index ->
                onSpeedChange(listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)[index])
            }
            SettingsNavRow(
                title = "Speed & Sync",
                subtitle = "Audio / subtitle delay",
                icon = Icons.Outlined.Tune,
                onClick = onOpenSpeedSync
            )

            HorizontalDivider(Color.White.copy(alpha = 0.08f))

            // ---------------- Gestures ----------------
            SettingsSectionHeader("Gestures", Icons.Default.TouchApp)
            SettingsSwitchRow(
                title = "Vertical Gestures",
                subtitle = "Brightness (left) / Volume (right)",
                icon = Icons.Outlined.BrightnessHigh,
                checked = verticalGesturesEnabled,
                onCheckedChange = onVerticalGesturesChange
            )
            SettingsSwitchRow(
                title = "Horizontal Swipe Seek",
                subtitle = "Drag across the screen to seek",
                icon = Icons.Default.FastForward,
                checked = horizontalSeekEnabled,
                onCheckedChange = onHorizontalSeekChange
            )
            SettingsChipRow(
                title = "Double-tap Seek",
                icon = Icons.Default.TouchApp,
                options = listOf("10s", "30s", "60s"),
                selectedIndex = when (doubleTapSeekSeconds) {
                    30 -> 1
                    60 -> 2
                    else -> 0
                }
            ) { index ->
                onDoubleTapSeekSecondsChange(listOf(10, 30, 60)[index])
            }
            SettingsSwitchRow(
                title = "Auto-hide Controls",
                subtitle = "Fade out controls after 3s",
                icon = Icons.Outlined.MoreVert,
                checked = autoHideControls,
                onCheckedChange = onAutoHideControlsChange
            )

            HorizontalDivider(Color.White.copy(alpha = 0.08f))

            // ---------------- Screen / Orientation ----------------
            SettingsSectionHeader("Screen / Orientation", Icons.Outlined.ScreenRotation)
            SettingsSwitchRow(
                title = "Auto-rotate",
                subtitle = "Follow the sensor orientation",
                icon = Icons.Outlined.ScreenRotation,
                checked = autoRotate,
                onCheckedChange = {
                    autoRotate = it
                    context.getSharedPreferences("vidmax_settings", Context.MODE_PRIVATE)
                        .edit().putBoolean("auto_rotate", it).apply()
                    if (it) {
                        (context as? android.app.Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
                    }
                }
            )
            SettingsNavRow(
                title = "Rotate Screen",
                subtitle = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) "Switch to portrait" else "Switch to landscape",
                icon = Icons.Outlined.ScreenRotation,
                onClick = onRotateScreen
            )
            SettingsNavRow(
                title = "Fullscreen",
                subtitle = "Toggle system bars",
                icon = Icons.Outlined.Fullscreen,
                onClick = onToggleImmersive
            )

            HorizontalDivider(Color.White.copy(alpha = 0.08f))

            // ---------------- Controls ----------------
            SettingsSectionHeader("Controls", Icons.Outlined.Tune)
            SettingsNavRow(
                title = "Video Zoom",
                subtitle = "Pinch or use the zoom sheet",
                icon = Icons.Outlined.ZoomIn,
                onClick = onOpenZoom
            )
            SettingsNavRow(
                title = "Aspect Ratio",
                subtitle = "Fit / Crop / Stretch",
                icon = Icons.Outlined.AspectRatio,
                onClick = onOpenAspect
            )

            HorizontalDivider(Color.White.copy(alpha = 0.08f))

            // ---------------- Performance ----------------
            SettingsSectionHeader("Performance", Icons.Outlined.Memory)
            SettingsNavRow(
                title = "Hardware Decoder",
                subtitle = "Auto / SW / HW / HW+",
                icon = Icons.Outlined.Memory,
                onClick = onOpenDecoder
            )
            SettingsInfoRow(
                title = "Current Engine",
                value = if (currentEngine == PlayerEngine.EXO) "ExoPlayer (Media3)" else "MPV (HW Decode)"
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String, icon: ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (subtitle.isNotBlank()) {
                Text(subtitle, color = Color.Gray, fontSize = 12.sp)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.onPrimary, checkedTrackColor = MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
private fun SettingsNavRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (subtitle.isNotBlank()) {
                Text(subtitle, color = Color.Gray, fontSize = 12.sp)
            }
        }
        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SettingsInfoRow(title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Default.Info, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(22.dp))
        Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text(value, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SettingsChipRow(
    title: String,
    icon: ImageVector,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(22.dp))
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEachIndexed { index, label ->
                val isSelected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.1f))
                        .clickable { onSelect(index) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
