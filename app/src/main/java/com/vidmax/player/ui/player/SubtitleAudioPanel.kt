package com.vidmax.player.ui.player

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TextFormat
import androidx.compose.material.icons.outlined.VerticalAlignBottom
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidmax.player.viewmodel.PlayerEngine
import com.vidmax.player.viewmodel.PlayerViewModel
import `is`.xyz.mpv.MPVLib
import java.util.Locale
import kotlin.math.roundToInt

enum class SubtitleAudioTab { SUBTITLE, AUDIO }

// ---------------------------------------------------------------------------
// Colors from the design spec
// ---------------------------------------------------------------------------
private val RailUnselectedIcon = Color(0xFF9AA0A6)
private val RailSelectedBg = Color(0xFF3D8FD8)
private val LeadingIconTint = Color(0xFFC7CCD1)
private val SecondaryText = Color(0xFF9AA0A6)
private val ValueText = Color(0xFF58A6F0)
private val CardBackground = Color(0xFF202428)
private val CardDividerColor = Color.White.copy(alpha = 0.08f)

// ---------------------------------------------------------------------------
// Full-screen overlay: Subtitle / Audio Track panel
// ---------------------------------------------------------------------------
@Composable
fun SubtitleAudioPanel(
    initialTab: SubtitleAudioTab,
    currentEngine: PlayerEngine,
    viewModel: PlayerViewModel,
    onClose: () -> Unit,
    onPickSubtitle: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("vidmax_settings", Context.MODE_PRIVATE)
    val isMpv = currentEngine == PlayerEngine.MPV

    var tab by remember { mutableStateOf(initialTab) }

    // ---- mpv track lists ----
    var mpvSubTracks by remember { mutableStateOf<List<MpvTrackInfo>>(emptyList()) }
    var currentMpvSubId by remember { mutableStateOf("no") }
    var mpvAudioTracks by remember { mutableStateOf<List<MpvTrackInfo>>(emptyList()) }
    var currentMpvAudioId by remember { mutableStateOf("1") }

    // ---- Subtitle appearance ----
    val subtitleSize by viewModel.subtitleSize.collectAsState()
    var textSizePercent by remember { mutableIntStateOf((subtitleSize / 16f * 100f).roundToInt()) }
    var subColor by remember { mutableIntStateOf(prefs.getInt("sub_color", Color.White.toArgb())) }
    var subOutline by remember { mutableFloatStateOf(prefs.getFloat("sub_outline", 1f)) }
    var subBgEnabled by remember { mutableStateOf(prefs.getBoolean("sub_bg_enabled", true)) }
    var subBgColor by remember { mutableIntStateOf(prefs.getInt("sub_bg_color", Color.Transparent.toArgb())) }
    var subMarginPercent by remember { mutableFloatStateOf(prefs.getFloat("sub_margin", 4f)) }
    var subtitleDelaySec by remember { mutableFloatStateOf(prefs.getFloat("sub_delay", 0f)) }

    // ---- Audio options ----
    var swAudioDecoder by remember { mutableStateOf(prefs.getBoolean("audio_sw_decoder", true)) }
    var stereoMode by remember { mutableStateOf(prefs.getString("audio_stereo_mode", "Normal") ?: "Normal") }
    var avSyncSec by remember { mutableFloatStateOf(prefs.getFloat("audio_avsync", 0f)) }
    var audioDelaySec by remember { mutableFloatStateOf(prefs.getFloat("audio_delay", 0f)) }
    var audioOutput by remember { mutableStateOf(prefs.getString("audio_output", "Device default") ?: "Device default") }
    var normalizeVolume by remember { mutableStateOf(prefs.getBoolean("audio_normalize", true)) }
    var audioRenderer by remember {
        mutableStateOf(prefs.getString("audio_renderer", "Auto (Best quality)") ?: "Auto (Best quality)")
    }

    // ---- Dialog visibility ----
    var showTextColorDialog by remember { mutableStateOf(false) }
    var showBgColorDialog by remember { mutableStateOf(false) }
    var showStereoDialog by remember { mutableStateOf(false) }
    var showAvSyncDialog by remember { mutableStateOf(false) }
    var showAudioDelayDialog by remember { mutableStateOf(false) }
    var showAudioOutputDialog by remember { mutableStateOf(false) }
    var showAudioRendererDialog by remember { mutableStateOf(false) }

    fun refreshSubtitleTracks() {
        try {
            val tracks = mutableListOf<MpvTrackInfo>()
            val count = MPVLib.getPropertyInt("track-list/count") ?: 0
            for (i in 0 until count) {
                val type = MPVLib.getPropertyString("track-list/$i/type")
                if (type == "sub") {
                    val id = MPVLib.getPropertyInt("track-list/$i/id") ?: -1
                    val title = MPVLib.getPropertyString("track-list/$i/title") ?: ""
                    val lang = MPVLib.getPropertyString("track-list/$i/lang") ?: ""
                    val name =
                        if (title.isNotEmpty()) title
                        else if (lang.isNotEmpty()) lang
                        else "Subtitle Track $id"
                    if (id != -1) tracks.add(MpvTrackInfo(id, name))
                }
            }
            mpvSubTracks = tracks
            currentMpvSubId = MPVLib.getPropertyString("sid") ?: "no"
        } catch (e: Exception) {}
    }

    fun refreshAudioTracks() {
        try {
            val tracks = mutableListOf<MpvTrackInfo>()
            val count = MPVLib.getPropertyInt("track-list/count") ?: 0
            for (i in 0 until count) {
                val type = MPVLib.getPropertyString("track-list/$i/type")
                if (type == "audio") {
                    val id = MPVLib.getPropertyInt("track-list/$i/id") ?: -1
                    val title = MPVLib.getPropertyString("track-list/$i/title") ?: ""
                    val lang = MPVLib.getPropertyString("track-list/$i/lang") ?: ""
                    val name =
                        if (title.isNotEmpty()) title
                        else if (lang.isNotEmpty()) lang
                        else "Audio Track $id"
                    if (id != -1) tracks.add(MpvTrackInfo(id, name))
                }
            }
            mpvAudioTracks = tracks
            currentMpvAudioId = MPVLib.getPropertyString("aid") ?: "1"
        } catch (e: Exception) {}
    }

    LaunchedEffect(Unit) {
        if (isMpv) {
            refreshSubtitleTracks()
            refreshAudioTracks()
            subtitleDelaySec = (MPVLib.getPropertyDouble("sub-delay") ?: 0.0).toFloat()
            audioDelaySec = (MPVLib.getPropertyDouble("audio-delay") ?: 0.0).toFloat()
        }
    }

    fun applyTextSize(percent: Int) {
        val p = percent.coerceIn(50, 200)
        textSizePercent = p
        prefs.edit().putInt("sub_text_size_percent", p).apply()
        val size = p / 100f * 16f
        viewModel.setSubtitleSize(size)
        if (isMpv) {
            try { MPVLib.setPropertyDouble("sub-scale", p / 100f) } catch (e: Exception) {}
        }
    }

    fun applySubColor(color: Color) {
        subColor = color.toArgb()
        prefs.edit().putInt("sub_color", subColor).apply()
        if (isMpv) {
            try { MPVLib.setPropertyString("sub-color", mpvColorString(subColor)) } catch (e: Exception) {}
        }
    }

    fun applySubOutline(value: Float) {
        subOutline = value.coerceIn(0f, 4f)
        prefs.edit().putFloat("sub_outline", subOutline).apply()
        if (isMpv) {
            try { MPVLib.setPropertyDouble("sub-outline", subOutline) } catch (e: Exception) {}
        }
    }

    fun applySubBgEnabled(enabled: Boolean) {
        subBgEnabled = enabled
        prefs.edit().putBoolean("sub_bg_enabled", enabled).apply()
        pushSubBg()
    }

    fun applySubBgColor(color: Color) {
        subBgColor = color.toArgb()
        prefs.edit().putInt("sub_bg_color", subBgColor).apply()
        pushSubBg()
    }

    fun pushSubBg() {
        if (!isMpv) return
        val argb = if (subBgEnabled) subBgColor else Color.Transparent.toArgb()
        try { MPVLib.setPropertyString("sub-back-color", mpvColorString(argb)) } catch (e: Exception) {}
    }

    fun applySubMargin(value: Float) {
        subMarginPercent = value.coerceIn(0f, 20f)
        prefs.edit().putFloat("sub_margin", subMarginPercent).apply()
        if (isMpv) {
            try { MPVLib.setPropertyString("sub-margin-y", "${subMarginPercent.roundToInt()}%") } catch (e: Exception) {}
        }
    }

    fun applySubDelay(value: Float) {
        subtitleDelaySec = value.coerceIn(-5f, 5f)
        prefs.edit().putFloat("sub_delay", subtitleDelaySec).apply()
        if (isMpv) {
            try { MPVLib.setPropertyDouble("sub-delay", subtitleDelaySec.toDouble()) } catch (e: Exception) {}
        }
    }

    fun applyAudioDelay(value: Float) {
        audioDelaySec = value.coerceIn(-5f, 5f)
        prefs.edit().putFloat("audio_delay", audioDelaySec).apply()
        if (isMpv) {
            try { MPVLib.setPropertyDouble("audio-delay", audioDelaySec.toDouble()) } catch (e: Exception) {}
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {}
    ) {
        Row(Modifier.fillMaxSize()) {
            // ==================== LEFT ICON RAIL ====================
            Column(
                modifier = Modifier
                    .width(76.dp)
                    .fillMaxHeight()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(top = 96.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                RailButton(Icons.Outlined.FolderOpen, selected = false) { onPickSubtitle() }
                RailButton(Icons.Outlined.Settings, selected = false) { onClose(); onOpenSettings() }
                RailButton(Icons.Outlined.Subtitles, tab == SubtitleAudioTab.SUBTITLE) { tab = SubtitleAudioTab.SUBTITLE }
                RailButton(Icons.Outlined.MusicNote, tab == SubtitleAudioTab.AUDIO) { tab = SubtitleAudioTab.AUDIO }
                // Sliders icon omitted: no equalizer sheet exists in the app.
                RailButton(Icons.Outlined.Speed, selected = false) { onClose(); onOpenSync() }
            }

            // ==================== MAIN AREA ====================
            Column(Modifier.fillMaxSize()) {
                PanelTopBar(
                    title = if (tab == SubtitleAudioTab.SUBTITLE) "Subtitle" else "Audio Track",
                    onBack = onClose,
                    onClose = onClose
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .widthIn(max = 520.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    if (tab == SubtitleAudioTab.SUBTITLE) {
                        SubtitleTab(
                            isMpv = isMpv,
                            mpvSubTracks = mpvSubTracks,
                            currentMpvSubId = currentMpvSubId,
                            onPickSubtitle = onPickSubtitle,
                            onSelectSubtitle = { id ->
                                try { MPVLib.setPropertyInt("sid", id) } catch (e: Exception) {}
                                currentMpvSubId = id.toString()
                            },
                            onOffSubtitle = {
                                try { MPVLib.setPropertyString("sid", "no") } catch (e: Exception) {}
                                currentMpvSubId = "no"
                            },
                            textSizePercent = textSizePercent,
                            subColor = subColor,
                            subOutline = subOutline,
                            subBgEnabled = subBgEnabled,
                            subBgColor = subBgColor,
                            subMarginPercent = subMarginPercent,
                            subtitleDelaySec = subtitleDelaySec,
                            onTextSizeChange = ::applyTextSize,
                            onTextColorClick = { showTextColorDialog = true },
                            onBgColorClick = { showBgColorDialog = true },
                            onOutlineChange = ::applySubOutline,
                            onBgEnabledChange = ::applySubBgEnabled,
                            onMarginChange = ::applySubMargin,
                            onSubDelayChange = ::applySubDelay
                        )
                    } else {
                        AudioTab(
                            isMpv = isMpv,
                            mpvAudioTracks = mpvAudioTracks,
                            currentMpvAudioId = currentMpvAudioId,
                            onSelectAudio = { id ->
                                try { MPVLib.setPropertyInt("aid", id) } catch (e: Exception) {}
                                currentMpvAudioId = id.toString()
                            },
                            onDisableAudio = {
                                try { MPVLib.setPropertyString("aid", "no") } catch (e: Exception) {}
                                currentMpvAudioId = "no"
                            },
                            swAudioDecoder = swAudioDecoder,
                            onSwAudioDecoderChange = {
                                swAudioDecoder = it
                                prefs.edit().putBoolean("audio_sw_decoder", it).apply()
                            },
                            stereoMode = stereoMode,
                            avSyncSec = avSyncSec,
                            audioDelaySec = audioDelaySec,
                            audioOutput = audioOutput,
                            normalizeVolume = normalizeVolume,
                            audioRenderer = audioRenderer,
                            onStereoClick = { showStereoDialog = true },
                            onAvSyncClick = { showAvSyncDialog = true },
                            onAudioDelayClick = { showAudioDelayDialog = true },
                            onAudioOutputClick = { showAudioOutputDialog = true },
                            onNormalizeChange = {
                                normalizeVolume = it
                                prefs.edit().putBoolean("audio_normalize", it).apply()
                            },
                            onAudioRendererClick = { showAudioRendererDialog = true }
                        )
                    }
                }
            }
        }
    }

    // ==================== DIALOGS ====================
    if (showTextColorDialog) {
        ColorPickerDialog(
            title = "Text color",
            current = Color(subColor),
            onPick = {
                applySubColor(it)
                showTextColorDialog = false
            },
            onDismiss = { showTextColorDialog = false }
        )
    }

    if (showBgColorDialog) {
        ColorPickerDialog(
            title = "Background color",
            current = Color(subBgColor),
            onPick = {
                applySubBgColor(it)
                showBgColorDialog = false
            },
            onDismiss = { showBgColorDialog = false }
        )
    }

    if (showStereoDialog) {
        OptionListDialog(
            title = "Stereo mode",
            options = listOf("Normal", "Mono", "Stereo", "Reverse"),
            selected = stereoMode,
            onSelect = {
                stereoMode = it
                prefs.edit().putString("audio_stereo_mode", it).apply()
                showStereoDialog = false
            },
            onDismiss = { showStereoDialog = false }
        )
    }

    if (showAvSyncDialog) {
        StepperDialog(
            title = "Audio synchronization (AV sync)",
            valueText = String.format(Locale.US, "%.2fs", avSyncSec),
            onDecrease = {
                avSyncSec = (avSyncSec - 0.1f).coerceAtLeast(-5f)
                prefs.edit().putFloat("audio_avsync", avSyncSec).apply()
            },
            onIncrease = {
                avSyncSec = (avSyncSec + 0.1f).coerceAtMost(5f)
                prefs.edit().putFloat("audio_avsync", avSyncSec).apply()
            },
            onDismiss = { showAvSyncDialog = false }
        )
    }

    if (showAudioDelayDialog) {
        StepperDialog(
            title = "Audio delay",
            valueText = String.format(Locale.US, "%.2fs", audioDelaySec),
            onDecrease = { applyAudioDelay(audioDelaySec - 0.1f) },
            onIncrease = { applyAudioDelay(audioDelaySec + 0.1f) },
            onDismiss = { showAudioDelayDialog = false }
        )
    }

    if (showAudioOutputDialog) {
        OptionListDialog(
            title = "Audio output",
            options = listOf("Device default", "Speaker", "Bluetooth"),
            selected = audioOutput,
            onSelect = {
                audioOutput = it
                prefs.edit().putString("audio_output", it).apply()
                showAudioOutputDialog = false
            },
            onDismiss = { showAudioOutputDialog = false }
        )
    }

    if (showAudioRendererDialog) {
        OptionListDialog(
            title = "Audio renderer",
            options = listOf("Auto (Best quality)", "Software (Compatibility)", "Hardware (Low latency)"),
            selected = audioRenderer,
            onSelect = {
                audioRenderer = it
                prefs.edit().putString("audio_renderer", it).apply()
                showAudioRendererDialog = false
            },
            onDismiss = { showAudioRendererDialog = false }
        )
    }
}

// ---------------------------------------------------------------------------
// Subtitle tab content
// ---------------------------------------------------------------------------
@Composable
private fun SubtitleTab(
    isMpv: Boolean,
    mpvSubTracks: List<MpvTrackInfo>,
    currentMpvSubId: String,
    onPickSubtitle: () -> Unit,
    onSelectSubtitle: (Int) -> Unit,
    onOffSubtitle: () -> Unit,
    textSizePercent: Int,
    subColor: Int,
    subOutline: Float,
    subBgEnabled: Boolean,
    subBgColor: Int,
    subMarginPercent: Float,
    subtitleDelaySec: Float,
    onTextSizeChange: (Int) -> Unit,
    onTextColorClick: () -> Unit,
    onBgColorClick: () -> Unit,
    onOutlineChange: (Float) -> Unit,
    onBgEnabledChange: (Boolean) -> Unit,
    onMarginChange: (Float) -> Unit,
    onSubDelayChange: (Float) -> Unit
) {
    PanelCard("Subtitle files") {
        PanelRow(
            leading = { LeadingIcon(Icons.Outlined.FolderOpen) },
            label = "Open subtitle file",
            secondary = "Load subtitle from device",
            onClick = onPickSubtitle
        )
    }

    if (isMpv && mpvSubTracks.isNotEmpty()) {
        PanelCard("Subtitle tracks") {
            PanelRow(
                leading = { PanelRadio(currentMpvSubId == "no") },
                label = "Off",
                secondary = "Disable subtitles",
                onClick = onOffSubtitle
            )
            mpvSubTracks.forEachIndexed { index, track ->
                CardDivider()
                PanelRow(
                    leading = { PanelRadio(currentMpvSubId == track.id.toString()) },
                    label = track.name,
                    secondary = "Subtitle track ${track.id}",
                    onClick = { onSelectSubtitle(track.id) }
                )
            }
        }
    } else if (!isMpv) {
        PanelCard("Subtitle tracks") {
            PanelRow(
                leading = { LeadingIcon(Icons.Outlined.Subtitles) },
                label = "Track selection not available",
                secondary = "Switch to the MPV (HW) engine to change subtitle tracks"
            )
        }
    }

    PanelCard("Appearance") {
        PanelRow(
            leading = { LeadingIcon(Icons.Outlined.FormatSize) },
            label = "Text size",
            trailing = {
                PanelStepper(
                    valueText = "$textSizePercent%",
                    onDecrease = { if (textSizePercent > 50) onTextSizeChange(textSizePercent - 10) },
                    onIncrease = { if (textSizePercent < 200) onTextSizeChange(textSizePercent + 10) }
                )
            }
        )
        CardDivider()
        PanelRow(
            leading = { LeadingIcon(Icons.Outlined.Palette) },
            label = "Text color",
            trailing = { ColorCircle(Color(subColor), onTextColorClick) }
        )
        CardDivider()
        PanelRow(
            leading = { LeadingIcon(Icons.Outlined.TextFormat) },
            label = "Outline",
            trailing = {
                PanelStepper(
                    valueText = String.format(Locale.US, "%.1fpx", subOutline),
                    onDecrease = { if (subOutline > 0f) onOutlineChange(subOutline - 0.5f) },
                    onIncrease = { if (subOutline < 4f) onOutlineChange(subOutline + 0.5f) }
                )
            }
        )
        CardDivider()
        PanelRow(
            leading = { LeadingIcon(Icons.Outlined.CropSquare) },
            label = "Background",
            trailing = { PanelSwitch(subBgEnabled, onBgEnabledChange) }
        )
        CardDivider()
        PanelRow(
            leading = { LeadingIcon(Icons.Outlined.Colorize) },
            label = "Background color",
            trailing = { ColorCircle(Color(subBgColor), onBgColorClick) }
        )
        CardDivider()
        PanelRow(
            leading = { LeadingIcon(Icons.Outlined.VerticalAlignBottom) },
            label = "Bottom margin",
            trailing = {
                PanelStepper(
                    valueText = "${subMarginPercent.roundToInt()}%",
                    onDecrease = { if (subMarginPercent > 0f) onMarginChange(subMarginPercent - 1f) },
                    onIncrease = { if (subMarginPercent < 20f) onMarginChange(subMarginPercent + 1f) }
                )
            }
        )
    }

    PanelCard("Synchronization") {
        PanelRow(
            leading = { LeadingIcon(Icons.Outlined.Schedule) },
            label = "Subtitle delay",
            trailing = {
                PanelStepper(
                    valueText = String.format(Locale.US, "%.2fs", subtitleDelaySec),
                    onDecrease = { onSubDelayChange(subtitleDelaySec - 0.1f) },
                    onIncrease = { onSubDelayChange(subtitleDelaySec + 0.1f) }
                )
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Audio tab content
// ---------------------------------------------------------------------------
@Composable
private fun AudioTab(
    isMpv: Boolean,
    mpvAudioTracks: List<MpvTrackInfo>,
    currentMpvAudioId: String,
    onSelectAudio: (Int) -> Unit,
    onDisableAudio: () -> Unit,
    swAudioDecoder: Boolean,
    onSwAudioDecoderChange: (Boolean) -> Unit,
    stereoMode: String,
    avSyncSec: Float,
    audioDelaySec: Float,
    audioOutput: String,
    normalizeVolume: Boolean,
    audioRenderer: String,
    onStereoClick: () -> Unit,
    onAvSyncClick: () -> Unit,
    onAudioDelayClick: () -> Unit,
    onAudioOutputClick: () -> Unit,
    onNormalizeChange: (Boolean) -> Unit,
    onAudioRendererClick: () -> Unit
) {
    PanelCard("Audio tracks") {
        if (isMpv) {
            mpvAudioTracks.forEachIndexed { index, track ->
                val isSelected = currentMpvAudioId == track.id.toString()
                PanelRow(
                    leading = { PanelRadio(isSelected) },
                    label = "Audio track #${index + 1}",
                    secondary = track.name,
                    trailing = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (index == 0) DefaultBadge()
                            KebabIcon()
                        }
                    },
                    onClick = { onSelectAudio(track.id) }
                )
                if (index < mpvAudioTracks.size - 1) CardDivider()
            }
            if (mpvAudioTracks.isNotEmpty()) CardDivider()
            val isDisabled =
                currentMpvAudioId == "no" || currentMpvAudioId == "0" || currentMpvAudioId == "false"
            PanelRow(
                leading = { PanelRadio(isDisabled) },
                label = "Disable",
                secondary = "Disable audio",
                trailing = { KebabIcon() },
                onClick = onDisableAudio
            )
        } else {
            PanelRow(
                leading = { LeadingIcon(Icons.Outlined.Audiotrack) },
                label = "Track selection not available",
                secondary = "Switch to the MPV (HW) engine to change audio tracks"
            )
        }
    }

    PanelCard("Audio options") {
        PanelRow(
            leading = { LeadingIcon(Icons.Outlined.Memory) },
            label = "Use SW audio decoder",
            secondary = "Use software decoder instead of hardware",
            trailing = { PanelSwitch(swAudioDecoder, onSwAudioDecoderChange) }
        )
        CardDivider()
        PanelRow(
            leading = { LeadingIcon(Icons.Outlined.GraphicEq) },
            label = "Stereo mode",
            trailing = { ValueChevron(stereoMode, accent = stereoMode != "Normal", onStereoClick) }
        )
        CardDivider()
        PanelRow(
            leading = { LeadingIcon(Icons.Outlined.Sync) },
            label = "Audio synchronization (AV sync)",
            trailing = {
                ValueChevron(
                    String.format(Locale.US, "%.2fs", avSyncSec),
                    accent = kotlin.math.abs(avSyncSec) > 0.001f,
                    onAvSyncClick
                )
            }
        )
        CardDivider()
        PanelRow(
            leading = { LeadingIcon(Icons.Outlined.Schedule) },
            label = "Audio delay",
            trailing = {
                ValueChevron(
                    String.format(Locale.US, "%.2fs", audioDelaySec),
                    accent = kotlin.math.abs(audioDelaySec) > 0.001f,
                    onAudioDelayClick
                )
            }
        )
        CardDivider()
        PanelRow(
            leading = { LeadingIcon(Icons.Outlined.VolumeUp) },
            label = "Audio output",
            trailing = { ValueChevron(audioOutput, accent = audioOutput != "Device default", onAudioOutputClick) }
        )
        CardDivider()
        PanelRow(
            leading = { LeadingIcon(Icons.Outlined.Equalizer) },
            label = "Normalize volume",
            secondary = "Balance audio volume",
            trailing = { PanelSwitch(normalizeVolume, onNormalizeChange) }
        )
        CardDivider()
        PanelRow(
            leading = { LeadingIcon(Icons.Outlined.Audiotrack) },
            label = "Audio renderer",
            trailing = {
                ValueChevron(audioRenderer, accent = audioRenderer != "Auto (Best quality)", onAudioRendererClick)
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Common UI pieces
// ---------------------------------------------------------------------------
@Composable
private fun RailButton(icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) RailSelectedBg else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) Color.White else RailUnselectedIcon, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun PanelTopBar(title: String, onBack: () -> Unit, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .padding(start = 24.dp, end = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircleIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack)
        Spacer(Modifier.width(24.dp))
        Text(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        CircleIconButton(Icons.Outlined.Close, "Close", onClose)
    }
}

@Composable
private fun CircleIconButton(icon: ImageVector, contentDescription: String?, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun PanelCard(header: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBackground)
    ) {
        Text(
            header,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(16.dp)
        )
        content()
    }
}

@Composable
private fun PanelRow(
    leading: (@Composable () -> Unit)? = null,
    label: String,
    secondary: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .padding(horizontal = 16.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (leading != null) {
            leading()
        }
        Column(Modifier.weight(1f)) {
            Text(label, color = Color.White, fontSize = 16.sp)
            if (secondary != null) {
                Text(secondary, color = SecondaryText, fontSize = 13.sp)
            }
        }
        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
private fun LeadingIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, tint = LeadingIconTint, modifier = Modifier.size(24.dp))
}

@Composable
private fun CardDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp),
        thickness = 1.dp,
        color = CardDividerColor
    )
}

@Composable
private fun PanelStepper(
    valueText: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StepButton(Icons.Default.Remove, onDecrease)
        Text(
            valueText,
            color = ValueText,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(64.dp)
        )
        StepButton(Icons.Default.Add, onIncrease)
    }
}

@Composable
private fun StepButton(icon: ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun PanelSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedTrackColor = RailSelectedBg,
            uncheckedTrackColor = Color.Gray,
            checkedThumbColor = Color.White,
            uncheckedThumbColor = Color.White
        )
    )
}

@Composable
private fun ColorCircle(color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .border(2.dp, Color.White, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (color.alpha < 0.01f) {
            Checkerboard()
        } else {
            Box(Modifier.fillMaxSize().clip(CircleShape).background(color))
        }
    }
}

@Composable
private fun Checkerboard() {
    Canvas(Modifier.fillMaxSize()) {
        val cell = size.width / 4f
        for (r in 0 until 4) {
            for (c in 0 until 4) {
                if ((r + c) % 2 == 0) {
                    drawRect(
                        color = Color(0xFF9E9E9E).copy(alpha = 0.55f),
                        topLeft = Offset(c * cell, r * cell),
                        size = Size(cell, cell)
                    )
                }
            }
        }
    }
}

@Composable
private fun PanelRadio(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .border(2.dp, if (selected) ValueText else Color(0xFF9AA0A6), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(ValueText))
        }
    }
}

@Composable
private fun ValueChevron(value: String, accent: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(value, color = if (accent) ValueText else Color(0xFF9AA0A6), fontSize = 14.sp)
        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = Color(0xFF9AA0A6), modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun DefaultBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(RailSelectedBg.copy(alpha = 0.2f))
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Text("Default", color = ValueText, fontSize = 12.sp)
    }
}

@Composable
private fun KebabIcon() {
    Icon(Icons.Outlined.MoreVert, contentDescription = null, tint = Color(0xFF9AA0A6), modifier = Modifier.size(20.dp))
}

// ---------------------------------------------------------------------------
// Dialogs
// ---------------------------------------------------------------------------
@Composable
private fun OptionListDialog(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBackground,
        title = { Text(title, color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                options.forEach { option ->
                    val isSelected = option == selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PanelRadio(isSelected)
                        Spacer(Modifier.width(16.dp))
                        Text(option, color = if (isSelected) ValueText else Color.White, fontSize = 16.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK", color = ValueText) }
        }
    )
}

@Composable
private fun StepperDialog(
    title: String,
    valueText: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBackground,
        title = { Text(title, color = Color.White, fontWeight = FontWeight.Bold) },
        text = { PanelStepper(valueText, onDecrease, onIncrease) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done", color = ValueText) }
        }
    )
}

private val PanelColors = listOf(
    Color.White to "White",
    Color.Black to "Black",
    Color(0xFFFFEB3B) to "Yellow",
    Color(0xFF00E5FF) to "Cyan",
    Color(0xFF69F0AE) to "Green",
    Color(0xFFFF5252) to "Red",
    Color(0xFF448AFF) to "Blue",
    Color(0xFFFFAB40) to "Orange",
    Color(0xFFE040FB) to "Purple",
    Color.Transparent to "Transparent"
)

@Composable
private fun ColorPickerDialog(
    title: String,
    current: Color,
    onPick: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBackground,
        title = { Text(title, color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                PanelColors.chunked(5).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        row.forEach { (color, _) ->
                            ColorCircle(color) { onPick(color) }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = ValueText) }
        }
    )
}

private fun mpvColorString(argb: Int): String {
    return String.format(Locale.US, "#%08X", argb)
}
