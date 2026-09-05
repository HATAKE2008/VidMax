@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.ui.CaptionStyleCompat
import com.vidmax.player.viewmodel.PlayerEngine
import com.vidmax.player.viewmodel.PlayerViewModel
import com.vidmax.player.viewmodel.SubtitleAudioTab
import `is`.xyz.mpv.MPVLib
import java.util.Locale
import kotlin.math.roundToInt

private data class ExoTrackInfo(
    val group: Tracks.Group,
    val trackIndex: Int,
    val label: String,
    val secondary: String,
    val selected: Boolean
)

// ---------------------------------------------------------------------------
// EXO track helpers
// ---------------------------------------------------------------------------
private fun exoLabel(format: Format, index: Int): String =
    format.label?.takeIf { it.isNotBlank() }
        ?: format.language?.takeIf { it.isNotBlank() }
        ?: "Track #${index + 1}"

private fun exoSecondary(format: Format, channelCount: Int?): String {
    val parts = mutableListOf<String>()
    format.language?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
    format.sampleMimeType?.let { parts.add(exoMimeShortName(it)) }
    if (channelCount != null && channelCount > 0) parts.add("$channelCount.0ch")
    return parts.joinToString(", ")
}

private fun exoMimeShortName(mime: String): String =
    when (mime) {
        "audio/mp4a-latm" -> "AAC"
        "audio/mpeg" -> "MP3"
        "audio/opus" -> "Opus"
        "audio/ac3" -> "AC-3"
        "audio/eac3" -> "E-AC-3"
        "audio/vorbis" -> "Vorbis"
        "audio/flac" -> "FLAC"
        "audio/raw" -> "PCM"
        "audio/true-hd" -> "TrueHD"
        "audio/dts" -> "DTS"
        "text/ssa" -> "SSA"
        "text/x-ssa" -> "SSA"
        "text/x-ass" -> "ASS"
        "text/vtt" -> "VTT"
        "application/ttml" -> "TTML"
        "application/x-subrip" -> "SRT"
        "text/x-microdvd" -> "MicroDVD"
        else -> mime.substringAfter('/').uppercase(Locale.US)
    }

fun applyMpvStereoMode(mode: String) {
    try {
        when (mode) {
            "Mono" -> {
                MPVLib.command(arrayOf("af", "clr"))
                MPVLib.setPropertyString("audio-channels", "mono")
            }
            "Stereo" -> {
                MPVLib.command(arrayOf("af", "clr"))
                MPVLib.setPropertyString("audio-channels", "stereo")
            }
            "Reverse" -> {
                MPVLib.setPropertyString("audio-channels", "auto")
                val channels = MPVLib.getPropertyInt("audio-params/channel-count") ?: 2
                if (channels >= 2) {
                    MPVLib.command(arrayOf("af", "set", "lavfi=[pan=stereo|c0=c1|c1=c0]"))
                } else {
                    MPVLib.command(arrayOf("af", "clr"))
                }
            }
            else -> {
                MPVLib.command(arrayOf("af", "clr"))
                MPVLib.setPropertyString("audio-channels", "auto")
            }
        }
    } catch (e: Exception) {}
}

// ---------------------------------------------------------------------------
// Right-side Compact Overlay: Subtitle / Audio Track panel
// ---------------------------------------------------------------------------
@Composable
fun SubtitleAudioPanel(
    initialTab: SubtitleAudioTab,
    currentEngine: PlayerEngine,
    viewModel: PlayerViewModel,
    exoPlayer: Player?,
    onClose: () -> Unit,
    onPickSubtitle: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSync: () -> Unit,
    onStereoModeChange: (String) -> Unit = {},
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

    // ---- exo track lists ----
    var exoSubTracks by remember { mutableStateOf<List<ExoTrackInfo>>(emptyList()) }
    var exoAudioTracks by remember { mutableStateOf<List<ExoTrackInfo>>(emptyList()) }
    var initialExoSubIndex by remember { mutableIntStateOf(-1) }
    var initialExoAudioIndex by remember { mutableIntStateOf(-1) }

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

    fun refreshExoTracks() {
        val player = exoPlayer ?: return
        val tracks = try { player.currentTracks } catch (e: Exception) { return }
        val subs = mutableListOf<ExoTrackInfo>()
        val auds = mutableListOf<ExoTrackInfo>()
        for (group in tracks.groups) {
            when (group.type) {
                C.TRACK_TYPE_TEXT -> {
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        subs.add(
                            ExoTrackInfo(
                                group = group,
                                trackIndex = i,
                                label = exoLabel(format, i),
                                secondary = exoSecondary(format, null),
                                selected = group.isTrackSelected(i)
                            )
                        )
                    }
                }
                C.TRACK_TYPE_AUDIO -> {
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        auds.add(
                            ExoTrackInfo(
                                group = group,
                                trackIndex = i,
                                label = exoLabel(format, i),
                                secondary = exoSecondary(format, format.channelCount),
                                selected = group.isTrackSelected(i)
                            )
                        )
                    }
                }
            }
        }
        exoSubTracks = subs
        exoAudioTracks = auds
        if (initialExoSubIndex == -1) {
            initialExoSubIndex = subs.indexOfFirst { it.selected }
        }
        if (initialExoAudioIndex == -1) {
            initialExoAudioIndex = auds.indexOfFirst { it.selected }
        }
    }

    fun selectExoTrack(track: ExoTrackInfo, trackType: Int) {
        val player = exoPlayer ?: return
        player.trackSelectionParameters =
            player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(trackType, false)
                .setOverrideForType(
                    TrackSelectionOverride(track.group.mediaTrackGroup, listOf(track.trackIndex))
                )
                .build()
    }

    fun disableExoSubtitles() {
        val player = exoPlayer ?: return
        player.trackSelectionParameters =
            player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .build()
    }

    fun disableExoAudio() {
        val player = exoPlayer ?: return
        player.trackSelectionParameters =
            player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .build()
    }

    fun applyExoSubtitleView() {
        val view = viewModel.exoSubtitleView ?: return
        val bgColorOrTransparent =
            if (subBgEnabled) subBgColor else android.graphics.Color.TRANSPARENT
        view.setStyle(
            CaptionStyleCompat(
                subColor,
                android.graphics.Color.WHITE,
                bgColorOrTransparent,
                CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                android.graphics.Color.BLACK,
                null
            )
        )
        view.setFractionalTextSize(0.0533f * textSizePercent / 100f)
        view.setBottomPaddingFraction(subMarginPercent / 100f)
    }

    LaunchedEffect(Unit) {
        if (isMpv) {
            refreshSubtitleTracks()
            refreshAudioTracks()
            subtitleDelaySec = (MPVLib.getPropertyDouble("sub-delay") ?: 0.0).toFloat()
            audioDelaySec = (MPVLib.getPropertyDouble("audio-delay") ?: 0.0).toFloat()
        }
    }

    DisposableEffect(exoPlayer) {
        val player = exoPlayer ?: return@DisposableEffect onDispose {}
        val listener =
            object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) {
                    refreshExoTracks()
                }
            }
        player.addListener(listener)
        refreshExoTracks()
        onDispose {
            player.removeListener(listener)
        }
    }

    fun applyTextSize(percent: Int) {
        val p = percent.coerceIn(50, 200)
        textSizePercent = p
        prefs.edit().putInt("sub_text_size_percent", p).apply()
        val size = p / 100f * 16f
        viewModel.setSubtitleSize(size)
        if (isMpv) {
            try { MPVLib.setPropertyDouble("sub-scale", p / 100f.toDouble()) } catch (e: Exception) {}
        } else {
            applyExoSubtitleView()
        }
    }

    fun applySubColor(color: Color) {
        subColor = color.toArgb()
        prefs.edit().putInt("sub_color", subColor).apply()
        if (isMpv) {
            try { MPVLib.setPropertyString("sub-color", mpvColorString(subColor)) } catch (e: Exception) {}
        } else {
            applyExoSubtitleView()
        }
    }

    fun applySubOutline(value: Float) {
        subOutline = value.coerceIn(0f, 4f)
        prefs.edit().putFloat("sub_outline", subOutline).apply()
        if (isMpv) {
            try { MPVLib.setPropertyDouble("sub-outline", subOutline.toDouble()) } catch (e: Exception) {}
        } else {
            applyExoSubtitleView()
        }
    }

    fun pushSubBg() {
        if (!isMpv) return
        val argb = if (subBgEnabled) subBgColor else Color.Transparent.toArgb()
        try { MPVLib.setPropertyString("sub-back-color", mpvColorString(argb)) } catch (e: Exception) {}
    }

    fun applySubBgEnabled(enabled: Boolean) {
        subBgEnabled = enabled
        prefs.edit().putBoolean("sub_bg_enabled", enabled).apply()
        if (isMpv) pushSubBg() else applyExoSubtitleView()
    }

    fun applySubBgColor(color: Color) {
        subBgColor = color.toArgb()
        prefs.edit().putInt("sub_bg_color", subBgColor).apply()
        if (isMpv) pushSubBg() else applyExoSubtitleView()
    }

    fun applySubMargin(value: Float) {
        subMarginPercent = value.coerceIn(0f, 20f)
        prefs.edit().putFloat("sub_margin", subMarginPercent).apply()
        if (isMpv) {
            try { MPVLib.setPropertyString("sub-margin-y", "${subMarginPercent.roundToInt()}%") } catch (e: Exception) {}
        } else {
            applyExoSubtitleView()
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

    val configuration = LocalConfiguration.current
    val isLandscape =
        configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // Shared tab content — rendered by the landscape side panel or the
    // portrait bottom sheet below.
    val tabsContent: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
                    if (tab == SubtitleAudioTab.SUBTITLE) {
                        SubtitleTab(
                            isMpv = isMpv,
                            mpvSubTracks = mpvSubTracks,
                            currentMpvSubId = currentMpvSubId,
                            exoSubTracks = exoSubTracks,
                            onPickSubtitle = onPickSubtitle,
                            onSelectSubtitle = { id ->
                                try { MPVLib.setPropertyInt("sid", id) } catch (e: Exception) {}
                                currentMpvSubId = id.toString()
                            },
                            onOffSubtitle = {
                                try { MPVLib.setPropertyString("sid", "no") } catch (e: Exception) {}
                                currentMpvSubId = "no"
                            },
                            onSelectExoSubtitle = { track -> selectExoTrack(track, C.TRACK_TYPE_TEXT) },
                            onOffExoSubtitle = { disableExoSubtitles() },
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
                            exoAudioTracks = exoAudioTracks,
                            initialExoAudioIndex = initialExoAudioIndex,
                            onSelectAudio = { id ->
                                try { MPVLib.setPropertyInt("aid", id) } catch (e: Exception) {}
                                currentMpvAudioId = id.toString()
                            },
                            onDisableAudio = {
                                try { MPVLib.setPropertyString("aid", "no") } catch (e: Exception) {}
                                currentMpvAudioId = "no"
                            },
                            onSelectExoAudio = { track -> selectExoTrack(track, C.TRACK_TYPE_AUDIO) },
                            onDisableExoAudio = { disableExoAudio() },
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

        // ==================== LANDSCAPE: right-side panel over video ====================
        if (isLandscape) {
            Row(modifier = modifier.fillMaxSize()) {
                // LEFT HALF: transparent so the video stays fully visible underneath.
                // Tapping it closes the panel.
                Box(
                    modifier = Modifier
                        .weight(0.5f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onClose() }
                )

                // RIGHT HALF: the opaque panel
                Column(
                    modifier = Modifier
                        .weight(0.5f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Row(Modifier.fillMaxSize()) {
                        // ==================== LEFT ICON RAIL ====================
                        Column(
                            modifier = Modifier
                                .width(52.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .padding(top = 60.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            RailButton(Icons.Outlined.FolderOpen, selected = false) { onPickSubtitle() }
                            RailButton(Icons.Outlined.Settings, selected = false) { onClose(); onOpenSettings() }
                            RailButton(Icons.Outlined.Subtitles, tab == SubtitleAudioTab.SUBTITLE) { tab = SubtitleAudioTab.SUBTITLE }
                            RailButton(Icons.Outlined.MusicNote, tab == SubtitleAudioTab.AUDIO) { tab = SubtitleAudioTab.AUDIO }
                            RailButton(Icons.Outlined.Speed, selected = false) { onClose(); onOpenSync() }
                        }

                        // ==================== MAIN AREA ====================
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            PanelTopBar(
                                title = if (tab == SubtitleAudioTab.SUBTITLE) "Subtitle" else "Audio Track",
                                onClose = onClose
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                tabsContent()
                            }
                        }
                    }
                }
            }
        } else {
            // ==================== PORTRAIT: bottom sheet, capped height ====================
            Box(modifier = modifier.fillMaxSize()) {
                // Tap anywhere above the sheet to close.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onClose() }
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .heightIn(max = (configuration.screenHeightDp * 0.75f).dp)
                        .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    PanelTopBar(
                        title = if (tab == SubtitleAudioTab.SUBTITLE) "Subtitle" else "Audio Track",
                        onClose = onClose
                    )
                    // ==================== HORIZONTAL ICON RAIL ====================
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RailButton(Icons.Outlined.FolderOpen, selected = false) { onPickSubtitle() }
                        RailButton(Icons.Outlined.Settings, selected = false) { onClose(); onOpenSettings() }
                        RailButton(Icons.Outlined.Subtitles, tab == SubtitleAudioTab.SUBTITLE) { tab = SubtitleAudioTab.SUBTITLE }
                        RailButton(Icons.Outlined.MusicNote, tab == SubtitleAudioTab.AUDIO) { tab = SubtitleAudioTab.AUDIO }
                        RailButton(Icons.Outlined.Speed, selected = false) { onClose(); onOpenSync() }
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = (configuration.screenHeightDp * 0.55f).dp)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        tabsContent()
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
                if (isMpv) applyMpvStereoMode(it) else onStereoModeChange(it)
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
    exoSubTracks: List<ExoTrackInfo>,
    onPickSubtitle: () -> Unit,
    onSelectSubtitle: (Int) -> Unit,
    onOffSubtitle: () -> Unit,
    onSelectExoSubtitle: (ExoTrackInfo) -> Unit,
    onOffExoSubtitle: () -> Unit,
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
    } else if (!isMpv && exoSubTracks.isNotEmpty()) {
        PanelCard("Subtitle tracks") {
            PanelRow(
                leading = { PanelRadio(exoSubTracks.none { it.selected }) },
                label = "Off",
                secondary = "Disable subtitles",
                onClick = onOffExoSubtitle
            )
            exoSubTracks.forEachIndexed { _, track ->
                CardDivider()
                PanelRow(
                    leading = { PanelRadio(track.selected) },
                    label = track.label,
                    secondary = track.secondary,
                    onClick = { onSelectExoSubtitle(track) }
                )
            }
        }
    } else if (!isMpv) {
        PanelCard("Subtitle tracks") {
            PanelRow(
                leading = { LeadingIcon(Icons.Outlined.Subtitles) },
                label = "No subtitle tracks available",
                secondary = "No subtitle tracks found in the current video"
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
        Box(if (isMpv) Modifier else Modifier.alpha(0.4f)) {
            PanelRow(
                leading = { LeadingIcon(Icons.Outlined.Schedule) },
                label = "Subtitle delay",
                secondary = if (isMpv) null else "MPV engine only",
                trailing = {
                    PanelStepper(
                        valueText = String.format(Locale.US, "%.2fs", subtitleDelaySec),
                        onDecrease = { if (isMpv) onSubDelayChange(subtitleDelaySec - 0.1f) },
                        onIncrease = { if (isMpv) onSubDelayChange(subtitleDelaySec + 0.1f) },
                        enabled = isMpv
                    )
                }
            )
        }
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
    exoAudioTracks: List<ExoTrackInfo>,
    initialExoAudioIndex: Int,
    onSelectAudio: (Int) -> Unit,
    onDisableAudio: () -> Unit,
    onSelectExoAudio: (ExoTrackInfo) -> Unit,
    onDisableExoAudio: () -> Unit,
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
        } else if (exoAudioTracks.isNotEmpty()) {
            exoAudioTracks.forEachIndexed { index, track ->
                PanelRow(
                    leading = { PanelRadio(track.selected) },
                    label = track.label,
                    secondary = track.secondary,
                    trailing = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (index == initialExoAudioIndex) DefaultBadge()
                            KebabIcon()
                        }
                    },
                    onClick = { onSelectExoAudio(track) }
                )
                if (index < exoAudioTracks.size - 1) CardDivider()
            }
            CardDivider()
            val isDisabled = exoAudioTracks.none { it.selected }
            PanelRow(
                leading = { PanelRadio(isDisabled) },
                label = "Disable",
                secondary = "Disable audio",
                trailing = { KebabIcon() },
                onClick = onDisableExoAudio
            )
        } else {
            PanelRow(
                leading = { LeadingIcon(Icons.Outlined.Audiotrack) },
                label = "No audio tracks available",
                secondary = "No audio tracks found in the current video"
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
        Box(if (isMpv) Modifier else Modifier.alpha(0.4f)) {
            PanelRow(
                leading = { LeadingIcon(Icons.Outlined.Sync) },
                label = "Audio synchronization (AV sync)",
                secondary = if (isMpv) null else "MPV engine only",
                trailing = {
                    ValueChevron(
                        String.format(Locale.US, "%.2fs", avSyncSec),
                        accent = isMpv && kotlin.math.abs(avSyncSec) > 0.001f,
                        onAvSyncClick,
                        enabled = isMpv
                    )
                }
            )
        }
        CardDivider()
        Box(if (isMpv) Modifier else Modifier.alpha(0.4f)) {
            PanelRow(
                leading = { LeadingIcon(Icons.Outlined.Schedule) },
                label = "Audio delay",
                secondary = if (isMpv) null else "MPV engine only",
                trailing = {
                    ValueChevron(
                        String.format(Locale.US, "%.2fs", audioDelaySec),
                        accent = isMpv && kotlin.math.abs(audioDelaySec) > 0.001f,
                        onAudioDelayClick,
                        enabled = isMpv
                    )
                }
            )
        }
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
        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null,
            tint = if (selected) MaterialTheme.colorScheme.onPrimary
                   else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun PanelTopBar(title: String, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f))
        CircleIconButton(Icons.Outlined.Close, "Close", onClose)
    }
}

@Composable
private fun CircleIconButton(icon: ImageVector, contentDescription: String?, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(36.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp)) }
}

@Composable
private fun PanelCard(header: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Text(header, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 6.dp))
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
    // FIX: Compact spacing (min height 44.dp instead of 50.dp)
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (leading != null) leading()
        Column(Modifier.weight(1f)) {
            Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (secondary != null)
                Text(secondary, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) trailing()
    }
}

@Composable
private fun LeadingIcon(icon: ImageVector) {
    Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
}

@Composable
private fun CardDivider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp),
        thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}

@Composable
private fun PanelStepper(
    valueText: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    enabled: Boolean = true
) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        StepButton(Icons.Default.Remove, onDecrease, enabled)
        // FIX: Width increased to 56.dp to avoid text clipping
        Text(valueText, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, textAlign = TextAlign.Center,
            maxLines = 1, modifier = Modifier.width(56.dp))
        StepButton(Icons.Default.Add, onIncrease, enabled)
    }
}

@Composable
private fun StepButton(icon: ImageVector, onClick: () -> Unit, enabled: Boolean = true) {
    Box(modifier = Modifier.size(26.dp).clip(CircleShape)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (enabled) 1f else 0.5f), CircleShape)
        .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.5f), modifier = Modifier.size(12.dp))
    }
}

@Composable
private fun PanelSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

@Composable
private fun ColorCircle(color: Color, onClick: () -> Unit) {
    Box(modifier = Modifier.size(30.dp).clip(CircleShape)
        .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) {
        if (color.alpha < 0.01f) Checkerboard()
        else Box(Modifier.fillMaxSize().clip(CircleShape).background(color))
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
    Box(modifier = Modifier.size(20.dp).clip(CircleShape)
        .border(2.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
        contentAlignment = Alignment.Center) {
        if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
    }
}

@Composable
private fun ValueChevron(
    value: String,
    accent: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Row(modifier = Modifier.then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 110.dp))
        Icon(Icons.Default.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun DefaultBadge() {
    Box(modifier = Modifier.clip(RoundedCornerShape(6.dp))
        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
        .padding(horizontal = 5.dp, vertical = 2.dp)) {
        Text("Default", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
    }
}

@Composable
private fun KebabIcon() {
    Icon(Icons.Outlined.MoreVert, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
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
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
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
                        Text(option, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                     }
                 }
             }
         },
         confirmButton = {
             TextButton(onClick = onDismiss) { Text("OK", color = MaterialTheme.colorScheme.primary) }
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
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
        text = { PanelStepper(valueText, onDecrease, onIncrease) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done", color = MaterialTheme.colorScheme.primary) }
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
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
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
            TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.primary) }
        }
    )
}

private fun mpvColorString(argb: Int): String {
    return String.format(Locale.US, "#%08X", argb)
}
