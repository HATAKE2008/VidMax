package com.vidmax.player.ui.player

import android.app.Activity
import android.content.Context
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidmax.player.R
import com.vidmax.player.viewmodel.PlayerEngine
import com.vidmax.player.viewmodel.PlayerViewModel
import `is`.xyz.mpv.MPVLib

@Composable
fun PlayerSettingsSheet(
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("vidmax_settings", Context.MODE_PRIVATE)
    val activity = context as? Activity
    val currentEngine by viewModel.currentEngine.collectAsState()
    val subtitleSize by viewModel.subtitleSize.collectAsState()

    val primary = MaterialTheme.colorScheme.primary
    val isMpv = currentEngine == PlayerEngine.MPV

    val legacyVerticalGestures = prefs.getBoolean("gesture_vertical_enabled", true)
    var autoHideControls by remember { mutableStateOf(prefs.getBoolean("auto_hide_controls", true)) }
    var controlsHideDelayMs by remember { mutableIntStateOf(prefs.getInt("controls_hide_delay_ms", 3000)) }
    var showControlsOnPlay by remember { mutableStateOf(prefs.getBoolean("show_controls_on_play", true)) }
    var bottomControlsBelowSeekbar by remember { mutableStateOf(prefs.getBoolean("bottom_controls_below_seekbar", false)) }
    var ambientMode by remember { mutableStateOf(prefs.getBoolean("ambient_mode", false)) }
    var keepScreenOn by remember { mutableStateOf(prefs.getBoolean("keep_screen_on", true)) }
    var hideButtonBackground by remember { mutableStateOf(prefs.getBoolean("hide_button_background", false)) }
    var reduceMotion by remember { mutableStateOf(prefs.getBoolean("reduce_motion", false)) }
    var whiteSeekbar by remember { mutableStateOf(prefs.getBoolean("white_seekbar", false)) }
    var showDoubleTapIndicator by remember { mutableStateOf(prefs.getBoolean("show_double_tap_indicator", true)) }
    var brightnessGestureEnabled by remember { mutableStateOf(prefs.getBoolean("gesture_brightness_enabled", legacyVerticalGestures)) }
    var volumeGestureEnabled by remember { mutableStateOf(prefs.getBoolean("gesture_volume_enabled", legacyVerticalGestures)) }
    var pinchZoomEnabled by remember { mutableStateOf(prefs.getBoolean("pinch_to_zoom_enabled", true)) }
    var horizontalSeekEnabled by remember { mutableStateOf(prefs.getBoolean("gesture_horizontal_seek_enabled", true)) }
    var doubleTapSeekSeconds by remember { mutableIntStateOf(prefs.getInt("double_tap_seek_seconds", 10)) }
    var reverseDoubleTap by remember { mutableStateOf(prefs.getBoolean("reverse_double_tap", false)) }
    var seekGestureSensitivity by remember { mutableIntStateOf(prefs.getInt("seek_gesture_sensitivity", 60000)) }
    var singleTapAction by remember { mutableStateOf(prefs.getString("single_tap_action", "toggle_controls") ?: "toggle_controls") }
    var preventSeekbarTap by remember { mutableStateOf(prefs.getBoolean("prevent_seekbar_tap", false)) }
    var mpvVideoSync by remember { mutableStateOf(prefs.getString("mpv_video_sync", "audio") ?: "audio") }
    var mpvInterpolation by remember { mutableStateOf(prefs.getBoolean("mpv_interpolation", false)) }
    var mpvAudioPitchCorrection by remember { mutableStateOf(prefs.getBoolean("mpv_audio_pitch_correction", true)) }

    var showDecoderDialog by remember { mutableStateOf(false) }
    var currentMpvDecoder by remember { mutableStateOf("auto-copy") }

    val savePrefs: (String, Any) -> Unit = { key, value ->
        prefs.edit().apply {
            when (value) {
                is Boolean -> putBoolean(key, value)
                is Int -> putInt(key, value)
                is String -> putString(key, value)
            }
        }.apply()
    }

    LaunchedEffect(ambientMode, keepScreenOn) {
        val act = activity ?: return@LaunchedEffect
        act.window.setDimAmount(if (ambientMode) 0.85f else 0f)
        if (keepScreenOn) {
            act.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            act.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(currentEngine, mpvVideoSync, mpvInterpolation, mpvAudioPitchCorrection) {
        if (currentEngine == PlayerEngine.MPV) {
            try {
                MPVLib.setPropertyString("video-sync", mpvVideoSync)
                MPVLib.setPropertyBoolean("interpolation", mpvInterpolation)
                MPVLib.setPropertyBoolean("audio-pitch-correction", mpvAudioPitchCorrection)
            } catch (e: Exception) {}
        }
    }

    LaunchedEffect(showDecoderDialog) {
        if (showDecoderDialog && currentEngine == PlayerEngine.MPV) {
            try { currentMpvDecoder = MPVLib.getPropertyString("hwdec") ?: "auto-copy" } catch (e: Exception) {}
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // LEFT HALF: transparent so the live video stays fully visible
        Box(
            modifier = Modifier
                .weight(0.5f)
                .fillMaxHeight()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
        )

        // RIGHT HALF: the opaque settings panel
        Column(
            modifier = Modifier
                .weight(0.5f)
                .fillMaxHeight()
                .background(Color(0xFF12161A))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Player Settings",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Close, "Close", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }

            // ---------------- Controls ----------------
            SettingsSectionHeader("Controls", Icons.Outlined.Tune)
            SettingsSwitchRow(
                title = "Auto-hide Controls",
                subtitle = "Fade out controls after a delay",
                icon = Icons.Outlined.MoreVert,
                checked = autoHideControls,
                onCheckedChange = {
                    autoHideControls = it
                    savePrefs("auto_hide_controls", it)
                }
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
                controlsHideDelayMs = listOf(2000, 3000, 5000, 0)[index]
                savePrefs("controls_hide_delay_ms", controlsHideDelayMs)
            }
            SettingsSwitchRow(
                title = "Show Controls on Play",
                subtitle = "Reveal controls when playback starts",
                icon = Icons.Outlined.PlayArrow,
                checked = showControlsOnPlay,
                onCheckedChange = {
                    showControlsOnPlay = it
                    savePrefs("show_controls_on_play", it)
                }
            )
            SettingsSwitchRow(
                title = "Controls Below Seek Bar",
                subtitle = "Place the button row under the progress bar",
                icon = Icons.Outlined.FitScreen,
                checked = bottomControlsBelowSeekbar,
                onCheckedChange = {
                    bottomControlsBelowSeekbar = it
                    savePrefs("bottom_controls_below_seekbar", it)
                }
            )
            SettingsSwitchRow(
                title = "Ambient Mode",
                subtitle = "Dim the screen to reduce eye strain",
                icon = Icons.Outlined.BrightnessHigh,
                checked = ambientMode,
                onCheckedChange = {
                    ambientMode = it
                    savePrefs("ambient_mode", it)
                }
            )
            SettingsSwitchRow(
                title = "Keep Screen On",
                subtitle = "Prevent the screen from sleeping",
                icon = Icons.Outlined.LockOpen,
                checked = keepScreenOn,
                onCheckedChange = {
                    keepScreenOn = it
                    savePrefs("keep_screen_on", it)
                }
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
                hideButtonBackground = index == 1
                savePrefs("hide_button_background", hideButtonBackground)
            }
            SettingsSwitchRow(
                title = "Reduce Motion",
                subtitle = "Use simpler animations for the controls",
                icon = Icons.Outlined.Fullscreen,
                checked = reduceMotion,
                onCheckedChange = {
                    reduceMotion = it
                    savePrefs("reduce_motion", it)
                }
            )
            SettingsSwitchRow(
                title = "White Progress Bar",
                subtitle = "Render the progress bar in white",
                icon = Icons.Outlined.Speed,
                checked = whiteSeekbar,
                onCheckedChange = {
                    whiteSeekbar = it
                    savePrefs("white_seekbar", it)
                }
            )
            SettingsSwitchRow(
                title = "Double-tap Seek Indicator",
                subtitle = "Show the ripple when seeking by double-tap",
                icon = Icons.Outlined.AspectRatio,
                checked = showDoubleTapIndicator,
                onCheckedChange = {
                    showDoubleTapIndicator = it
                    savePrefs("show_double_tap_indicator", it)
                }
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

            // ---------------- Gestures ----------------
            SettingsSectionHeader("Gestures", Icons.Default.TouchApp)
            SettingsSwitchRow(
                title = "Brightness Gesture",
                subtitle = "Swipe up / down on the left edge",
                icon = Icons.Outlined.BrightnessHigh,
                checked = brightnessGestureEnabled,
                onCheckedChange = {
                    brightnessGestureEnabled = it
                    savePrefs("gesture_brightness_enabled", it)
                }
            )
            SettingsSwitchRow(
                title = "Volume Gesture",
                subtitle = "Swipe up / down on the right edge",
                icon = Icons.Outlined.VolumeUp,
                checked = volumeGestureEnabled,
                onCheckedChange = {
                    volumeGestureEnabled = it
                    savePrefs("gesture_volume_enabled", it)
                }
            )
            SettingsSwitchRow(
                title = "Pinch to Zoom",
                subtitle = "Pinch to zoom the video in / out",
                icon = Icons.Outlined.ZoomIn,
                checked = pinchZoomEnabled,
                onCheckedChange = {
                    pinchZoomEnabled = it
                    savePrefs("pinch_to_zoom_enabled", it)
                }
            )
            SettingsSwitchRow(
                title = "Horizontal Swipe Seek",
                subtitle = "Drag across the screen to seek",
                icon = Icons.Default.FastForward,
                checked = horizontalSeekEnabled,
                onCheckedChange = {
                    horizontalSeekEnabled = it
                    savePrefs("gesture_horizontal_seek_enabled", it)
                }
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
                doubleTapSeekSeconds = listOf(10, 30, 60)[index]
                savePrefs("double_tap_seek_seconds", doubleTapSeekSeconds)
            }
            SettingsSwitchRow(
                title = "Reverse Double-tap",
                subtitle = "Swap the left and right seek directions",
                icon = Icons.Outlined.SwapHoriz,
                checked = reverseDoubleTap,
                onCheckedChange = {
                    reverseDoubleTap = it
                    savePrefs("reverse_double_tap", it)
                }
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
                seekGestureSensitivity = listOf(30000, 60000, 120000)[index]
                savePrefs("seek_gesture_sensitivity", seekGestureSensitivity)
            }
            SettingsChipRow(
                title = "Single-tap Action",
                icon = Icons.Default.TouchApp,
                options = listOf("Toggle Controls", "Play / Pause"),
                selectedIndex = if (singleTapAction == "play_pause") 1 else 0
            ) { index ->
                singleTapAction = listOf("toggle_controls", "play_pause")[index]
                savePrefs("single_tap_action", singleTapAction)
            }
            SettingsSwitchRow(
                title = "Prevent Seek Bar Tap",
                subtitle = "Require dragging the seek bar to seek",
                icon = Icons.Default.Lock,
                checked = preventSeekbarTap,
                onCheckedChange = {
                    preventSeekbarTap = it
                    savePrefs("prevent_seekbar_tap", it)
                }
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
                onClick = { showDecoderDialog = true }
            )
            if (isMpv) {
                SettingsChipRow(
                    title = "Video Sync",
                    icon = Icons.Outlined.Repeat,
                    options = listOf("Audio", "Display Resample"),
                    selectedIndex = if (mpvVideoSync == "display-resample") 1 else 0
                ) { index ->
                    mpvVideoSync = listOf("audio", "display-resample")[index]
                    savePrefs("mpv_video_sync", mpvVideoSync)
                }
                SettingsSwitchRow(
                    title = "Interpolation",
                    subtitle = "Smooth motion by frame blending",
                    icon = Icons.Outlined.Movie,
                    checked = mpvInterpolation,
                    onCheckedChange = {
                        mpvInterpolation = it
                        savePrefs("mpv_interpolation", it)
                    }
                )
                SettingsSwitchRow(
                    title = "Audio Pitch Correction",
                    subtitle = "Keep pitch stable when changing speed",
                    icon = Icons.Outlined.Audiotrack,
                    checked = mpvAudioPitchCorrection,
                    onCheckedChange = {
                        mpvAudioPitchCorrection = it
                        savePrefs("mpv_audio_pitch_correction", it)
                    }
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

    if (showDecoderDialog) {
        AlertDialog(
            onDismissRequest = { showDecoderDialog = false },
            containerColor = Color(0xFF1E1E1E),
            title = { Text("Hardware Decoder", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    val decoderOptions = listOf(
                        Pair("auto-copy", "Auto (auto-copy)"),
                        Pair("no", "SW (no)"),
                        Pair("mediacodec-copy", "HW (mediacodec-copy)"),
                        Pair("mediacodec", "HW+ (mediacodec)")
                    )
                    decoderOptions.forEach { (value, label) ->
                        val isSelected = currentMpvDecoder == value
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                try {
                                    MPVLib.setPropertyString("hwdec", value)
                                    currentMpvDecoder = value
                                } catch (e: Exception) {}
                                showDecoderDialog = false
                            }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(id = if (isSelected) R.drawable.ic_radio_checked else R.drawable.ic_radio_unchecked),
                                contentDescription = null,
                                tint = if (isSelected) primary else Color.Gray,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                label,
                                color = if (isSelected) primary else Color.White,
                                fontSize = 16.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDecoderDialog = false }) { Text("OK") }
            }
        )
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
