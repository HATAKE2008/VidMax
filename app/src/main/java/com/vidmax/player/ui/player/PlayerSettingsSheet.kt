package com.vidmax.player.ui.player

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidmax.player.viewmodel.PlayerEngine
import com.vidmax.player.viewmodel.PlayerViewModel
import `is`.xyz.mpv.MPVLib

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettingsSheet(
    viewModel: PlayerViewModel,
    // ---- Controls ----
    autoHideControls: Boolean,
    onAutoHideControlsChange: (Boolean) -> Unit,
    controlsHideDelayMs: Int,
    onControlsHideDelayChange: (Int) -> Unit,
    showControlsOnPlay: Boolean,
    onShowControlsOnPlayChange: (Boolean) -> Unit,
    bottomControlsBelowSeekbar: Boolean,
    onBottomControlsBelowSeekbarChange: (Boolean) -> Unit,
    ambientMode: Boolean,
    onAmbientModeChange: (Boolean) -> Unit,
    keepScreenOn: Boolean,
    onKeepScreenOnChange: (Boolean) -> Unit,
    // ---- Aesthetics ----
    hideButtonBackground: Boolean,
    onHideButtonBackgroundChange: (Boolean) -> Unit,
    reduceMotion: Boolean,
    onReduceMotionChange: (Boolean) -> Unit,
    whiteSeekbar: Boolean,
    onWhiteSeekbarChange: (Boolean) -> Unit,
    showDoubleTapIndicator: Boolean,
    onShowDoubleTapIndicatorChange: (Boolean) -> Unit,
    // ---- Gestures ----
    brightnessGestureEnabled: Boolean,
    onBrightnessGestureChange: (Boolean) -> Unit,
    volumeGestureEnabled: Boolean,
    onVolumeGestureChange: (Boolean) -> Unit,
    pinchZoomEnabled: Boolean,
    onPinchZoomChange: (Boolean) -> Unit,
    horizontalSeekEnabled: Boolean,
    onHorizontalSeekChange: (Boolean) -> Unit,
    doubleTapSeekSeconds: Int,
    onDoubleTapSeekSecondsChange: (Int) -> Unit,
    reverseDoubleTap: Boolean,
    onReverseDoubleTapChange: (Boolean) -> Unit,
    seekGestureSensitivity: Int,
    onSeekGestureSensitivityChange: (Int) -> Unit,
    singleTapAction: String,
    onSingleTapActionChange: (String) -> Unit,
    preventSeekbarTap: Boolean,
    onPreventSeekbarTapChange: (Boolean) -> Unit,
    // ---- Settings / Advanced ----
    mpvVideoSync: String,
    onMpvVideoSyncChange: (String) -> Unit,
    mpvInterpolation: Boolean,
    onMpvInterpolationChange: (Boolean) -> Unit,
    mpvAudioPitchCorrection: Boolean,
    onMpvAudioPitchCorrectionChange: (Boolean) -> Unit,
    onOpenDecoder: () -> Unit,
    onDismiss: () -> Unit
) {
    val currentEngine by viewModel.currentEngine.collectAsState()
    val subtitleSize by viewModel.subtitleSize.collectAsState()

    val primary = MaterialTheme.colorScheme.primary
    val isMpv = currentEngine == PlayerEngine.MPV

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

            // ---------------- Controls ----------------
            SettingsSectionHeader("Controls", Icons.Outlined.Tune)
            SettingsSwitchRow(
                title = "Auto-hide Controls",
                subtitle = "Fade out controls after a delay",
                icon = Icons.Outlined.MoreVert,
                checked = autoHideControls,
                onCheckedChange = onAutoHideControlsChange
            )
            SettingsChipRow(
                title = "Auto-hide Delay",
                icon = Icons.Outlined.Timer,
                options = listOf("2s", "3s", "5s", "Never"),
                selectedIndex = when (controlsHideDelayMs) {
                    2000 -> 0
                    5000 -> 2
                    0 -> 3
                    else -> 1
                }
            ) { index ->
                onControlsHideDelayChange(listOf(2000, 3000, 5000, 0)[index])
            }
            SettingsSwitchRow(
                title = "Show Controls on Play",
                subtitle = "Reveal controls when playback starts",
                icon = Icons.Outlined.PlayArrow,
                checked = showControlsOnPlay,
                onCheckedChange = onShowControlsOnPlayChange
            )
            SettingsSwitchRow(
                title = "Controls Below Seek Bar",
                subtitle = "Place the button row under the progress bar",
                icon = Icons.Outlined.FitScreen,
                checked = bottomControlsBelowSeekbar,
                onCheckedChange = onBottomControlsBelowSeekbarChange
            )
            SettingsSwitchRow(
                title = "Ambient Mode",
                subtitle = "Dim the screen to reduce eye strain",
                icon = Icons.Outlined.BrightnessHigh,
                checked = ambientMode,
                onCheckedChange = onAmbientModeChange
            )
            SettingsSwitchRow(
                title = "Keep Screen On",
                subtitle = "Prevent the screen from sleeping",
                icon = Icons.Outlined.LockOpen,
                checked = keepScreenOn,
                onCheckedChange = onKeepScreenOnChange
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

            // ---------------- Aesthetics ----------------
            SettingsSectionHeader("Aesthetics", Icons.Default.Settings)
            SettingsChipRow(
                title = "Control Style",
                icon = Icons.Outlined.ZoomOut,
                options = listOf("Translucent", "Flat"),
                selectedIndex = if (hideButtonBackground) 1 else 0
            ) { index ->
                onHideButtonBackgroundChange(index == 1)
            }
            SettingsSwitchRow(
                title = "Reduce Motion",
                subtitle = "Use simpler animations for the controls",
                icon = Icons.Outlined.Fullscreen,
                checked = reduceMotion,
                onCheckedChange = onReduceMotionChange
            )
            SettingsSwitchRow(
                title = "White Progress Bar",
                subtitle = "Render the progress bar in white",
                icon = Icons.Outlined.Speed,
                checked = whiteSeekbar,
                onCheckedChange = onWhiteSeekbarChange
            )
            SettingsSwitchRow(
                title = "Double-tap Seek Indicator",
                subtitle = "Show the ripple when seeking by double-tap",
                icon = Icons.Outlined.AspectRatio,
                checked = showDoubleTapIndicator,
                onCheckedChange = onShowDoubleTapIndicatorChange
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

            // ---------------- Gestures ----------------
            SettingsSectionHeader("Gestures", Icons.Default.TouchApp)
            SettingsSwitchRow(
                title = "Brightness Gesture",
                subtitle = "Swipe up / down on the left edge",
                icon = Icons.Outlined.BrightnessHigh,
                checked = brightnessGestureEnabled,
                onCheckedChange = onBrightnessGestureChange
            )
            SettingsSwitchRow(
                title = "Volume Gesture",
                subtitle = "Swipe up / down on the right edge",
                icon = Icons.Outlined.VolumeUp,
                checked = volumeGestureEnabled,
                onCheckedChange = onVolumeGestureChange
            )
            SettingsSwitchRow(
                title = "Pinch to Zoom",
                subtitle = "Pinch to zoom the video in / out",
                icon = Icons.Outlined.ZoomIn,
                checked = pinchZoomEnabled,
                onCheckedChange = onPinchZoomChange
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
                title = "Reverse Double-tap",
                subtitle = "Swap the left and right seek directions",
                icon = Icons.Outlined.SwapHoriz,
                checked = reverseDoubleTap,
                onCheckedChange = onReverseDoubleTapChange
            )
            SettingsChipRow(
                title = "Seek Gesture Sensitivity",
                icon = Icons.Outlined.Speed,
                options = listOf("Low", "Medium", "High"),
                selectedIndex = when (seekGestureSensitivity) {
                    30000 -> 0
                    120000 -> 2
                    else -> 1
                }
            ) { index ->
                onSeekGestureSensitivityChange(listOf(30000, 60000, 120000)[index])
            }
            SettingsChipRow(
                title = "Single-tap Action",
                icon = Icons.Default.TouchApp,
                options = listOf("Toggle Controls", "Play / Pause"),
                selectedIndex = if (singleTapAction == "play_pause") 1 else 0
            ) { index ->
                onSingleTapActionChange(listOf("toggle_controls", "play_pause")[index])
            }
            SettingsSwitchRow(
                title = "Prevent Seek Bar Tap",
                subtitle = "Require dragging the seek bar to seek",
                icon = Icons.Default.Lock,
                checked = preventSeekbarTap,
                onCheckedChange = onPreventSeekbarTapChange
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

            // ---------------- Settings / Advanced ----------------
            SettingsSectionHeader("Settings / Advanced", Icons.Default.Settings)
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("Subtitle Size", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Slider(
                    value = subtitleSize,
                    onValueChange = { size ->
                        viewModel.setSubtitleSize(size)
                        if (isMpv) {
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
            SettingsNavRow(
                title = "Hardware Decoder",
                subtitle = "Auto / SW / HW / HW+",
                icon = Icons.Outlined.Memory,
                onClick = onOpenDecoder
            )
            if (isMpv) {
                SettingsChipRow(
                    title = "Video Sync",
                    icon = Icons.Outlined.Repeat,
                    options = listOf("Audio", "Display Resample"),
                    selectedIndex = if (mpvVideoSync == "display-resample") 1 else 0
                ) { index ->
                    onMpvVideoSyncChange(listOf("audio", "display-resample")[index])
                }
                SettingsSwitchRow(
                    title = "Interpolation",
                    subtitle = "Smooth motion by frame blending",
                    icon = Icons.Outlined.Movie,
                    checked = mpvInterpolation,
                    onCheckedChange = onMpvInterpolationChange
                )
                SettingsSwitchRow(
                    title = "Audio Pitch Correction",
                    subtitle = "Keep pitch stable when changing speed",
                    icon = Icons.Outlined.Audiotrack,
                    checked = mpvAudioPitchCorrection,
                    onCheckedChange = onMpvAudioPitchCorrectionChange
                )
            } else {
                SettingsInfoRow(
                    title = "MPV Advanced",
                    value = "Switch to MPV (HW) to configure"
                )
            }
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
