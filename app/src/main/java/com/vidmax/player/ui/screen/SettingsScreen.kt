package com.vidmax.player.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidmax.player.BuildConfig
import com.vidmax.player.R
import com.vidmax.player.data.repository.SettingsBackup
import com.vidmax.player.ui.components.UpdateResultDialog
import com.vidmax.player.ui.theme.AppFonts
import com.vidmax.player.ui.theme.AppTheme
import com.vidmax.player.utils.UpdateChecker
import com.vidmax.player.viewmodel.DarkMode
import com.vidmax.player.viewmodel.LibraryViewModel
import com.vidmax.player.viewmodel.PlayerEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    viewModel: LibraryViewModel,
    onBack: () -> Unit
) {
    val resumePlayback by viewModel.resumePlayback.collectAsState()
    val autoRotate by viewModel.autoRotate.collectAsState()
    val localMode by viewModel.localMode.collectAsState()
    val musicPlayerEnabled by viewModel.musicPlayerEnabled.collectAsState()
    val minimalistPlayer by viewModel.minimalistPlayer.collectAsState()
    val audioBoost by viewModel.audioBoost.collectAsState()
    val currentEngine by viewModel.playerEngine.collectAsState()
    val currentTheme by viewModel.appTheme.collectAsState()
    val darkMode by viewModel.darkMode.collectAsState()
    val amoledMode by viewModel.amoledMode.collectAsState()
    val context = LocalContext.current

    val appPrefs = remember { context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE) }
    val vidmaxPrefs = remember { context.getSharedPreferences("vidmax_settings", Context.MODE_PRIVATE) }
    // Bumped after a Settings Import so remember{} reads below reload from disk.
    var backupTick by remember { mutableStateOf(0) }
    var showIntro by remember(backupTick) { mutableStateOf(vidmaxPrefs.getBoolean("show_startup_intro", true)) }
    var showSpeedButton by remember(backupTick) { mutableStateOf(vidmaxPrefs.getBoolean("show_speed_button", true)) }
    var showLoopButton by remember(backupTick) { mutableStateOf(vidmaxPrefs.getBoolean("show_loop_button", true)) }
    var showZoomButtons by remember(backupTick) { mutableStateOf(vidmaxPrefs.getBoolean("show_zoom_buttons", true)) }
    var showExtraButtons by remember(backupTick) { mutableStateOf(vidmaxPrefs.getBoolean("show_extra_buttons", true)) }
    var updateNotifications by remember {
        mutableStateOf(appPrefs.getBoolean("update_notifications", true))
    }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateChecker.CheckResult?>(null) }
    val scope = rememberCoroutineScope()

    val checkForUpdates: () -> Unit = {
        if (!isCheckingUpdate) {
            isCheckingUpdate = true
            scope.launch {
                val result = UpdateChecker.checkForUpdate()
                isCheckingUpdate = false
                updateResult = result
            }
        }
    }

    val isSystemDark = isSystemInDarkTheme()
    val isCurrentlyDark = when (darkMode) {
        DarkMode.Dark -> true
        DarkMode.Light -> false
        DarkMode.System -> isSystemDark
    }

    // --- Font changer state ---
    val currentFontId by viewModel.appFontId.collectAsState()
    val importedFonts by viewModel.importedFonts.collectAsState()
    var pendingDeleteFont by remember { mutableStateOf<String?>(null) }
    var isImportingFont by remember { mutableStateOf(false) }

    val fontPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            isImportingFont = true
            scope.launch {
                val result = viewModel.importCustomFont(uri)
                isImportingFont = false
                val message = result.fold(
                    onSuccess = { fileName -> "Font \"${fileName.substringBeforeLast('.')}\" imported" },
                    onFailure = { it.message ?: "Could not import font" }
                )
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── P4b: Backup & Restore (SAF, no storage permission) ──────────────
    var isBackingUp by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (isBackingUp) return@rememberLauncherForActivityResult
        isBackingUp = true
        scope.launch {
            val ok = runCatching {
                val json = SettingsBackup.buildBackupJson(vidmaxPrefs)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException("write")
                }
                true
            }.getOrDefault(false)
            isBackingUp = false
            Toast.makeText(
                context,
                if (ok) "Settings exported successfully" else "Could not export settings",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result: SettingsBackup.ImportResult = runCatching {
                val raw = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val buf = ByteArray(SettingsBackup.MAX_IMPORT_BYTES + 1)
                        var total = 0
                        while (true) {
                            val read = input.read(buf, total, buf.size - total)
                            if (read < 0) break
                            total += read
                            if (total > SettingsBackup.MAX_IMPORT_BYTES) {
                                throw IllegalStateException("too large")
                            }
                        }
                        buf.copyOf(total).toString(Charsets.UTF_8)
                    } ?: throw IllegalStateException("read")
                }
                SettingsBackup.applyBackupJson(vidmaxPrefs, raw)
            }.getOrElse { SettingsBackup.ImportResult.Invalid("read") }
            when (result) {
                is SettingsBackup.ImportResult.Applied -> {
                    viewModel.reloadSettingsFromDisk()
                    backupTick++
                    Toast.makeText(context, "Settings imported successfully", Toast.LENGTH_SHORT).show()
                }
                is SettingsBackup.ImportResult.Invalid -> {
                    Toast.makeText(context, "Invalid or unsupported settings backup", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                // Clean Back Button without circle background
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_arrow_back),
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Text(
                    text = "Settings",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // Subtle separator under top bar
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                thickness = 0.5.dp
            )

            // ── Content ──────────────────────────────────────────────────────────
            LazyColumn(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .fillMaxSize()
                    .widthIn(max = 1100.dp)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp)
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
                                            modifier = Modifier.size(18.dp),
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

                // ── App Font (font changer + importer) ────────────────────────
                item {
                    SettingsDivider()
                    SettingsSectionHeader(title = "App Font", paddingTop = 4.dp)
                }
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            FontPreviewCard(
                                sampleText = "Aa Bb",
                                displayName = "System Default",
                                fontFamily = FontFamily.Default,
                                isSelected = currentFontId == AppFonts.SYSTEM_DEFAULT,
                                onClick = { viewModel.setAppFont(AppFonts.SYSTEM_DEFAULT) }
                            )
                        }
                        items(AppFonts.builtInFonts) { font ->
                            FontPreviewCard(
                                sampleText = "VidMax Aa",
                                displayName = font.displayName,
                                fontFamily = AppFonts.resolveFontFamily(context, font.id),
                                isSelected = currentFontId == font.id,
                                onClick = { viewModel.setAppFont(font.id) }
                            )
                        }
                        items(importedFonts, key = { it }) { fontId ->
                            FontPreviewCard(
                                sampleText = "VidMax Aa",
                                displayName = AppFonts.displayNameFor(fontId),
                                fontFamily = AppFonts.resolveFontFamily(context, fontId),
                                isSelected = currentFontId == fontId,
                                onClick = { viewModel.setAppFont(fontId) },
                                onDelete = { pendingDeleteFont = fontId }
                            )
                        }
                        item {
                            ImportFontCard(
                                isBusy = isImportingFont,
                                onClick = {
                                    fontPickerLauncher.launch(
                                        arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-otf", "application/octet-stream")
                                    )
                                }
                            )
                        }
                    }
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
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsToggleRow(
                        title = "Show Startup Intro",
                        subtitle = "Show logo splash when app opens",
                        iconId = R.drawable.ic_video_library,
                        checked = showIntro,
                        onCheckedChange = { on ->
                            showIntro = on
                            vidmaxPrefs.edit().putBoolean("show_startup_intro", on).apply()
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsToggleRow(
                        title = "Minimalist Player",
                        subtitle = "Use a cleaner player interface with reduced controls",
                        iconId = R.drawable.ic_view_list_custom,
                        checked = minimalistPlayer,
                        onCheckedChange = { viewModel.setMinimalistPlayer(it) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsToggleRow(
                        title = "Music Player",
                        subtitle = "Enable music player features",
                        iconId = R.drawable.ic_music_note,
                        checked = musicPlayerEnabled,
                        onCheckedChange = { viewModel.setMusicPlayerEnabled(it) }
                    )
                }

                // ── Player Buttons ────────────────────────────────────────────
                item {
                    SettingsDivider()
                    SettingsSectionHeader(title = "Player Buttons", paddingTop = 4.dp)
                }
                item {
                    SettingsToggleRow(
                        title = "Speed Button",
                        subtitle = "Show the playback speed button",
                        iconId = R.drawable.ic_play_arrow,
                        checked = showSpeedButton,
                        onCheckedChange = { on ->
                            showSpeedButton = on
                            vidmaxPrefs.edit().putBoolean("show_speed_button", on).apply()
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsToggleRow(
                        title = "Loop Button",
                        subtitle = "Show the repeat/loop button",
                        iconId = R.drawable.ic_rotate,
                        checked = showLoopButton,
                        onCheckedChange = { on ->
                            showLoopButton = on
                            vidmaxPrefs.edit().putBoolean("show_loop_button", on).apply()
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsToggleRow(
                        title = "Zoom Buttons",
                        subtitle = "Show the zoom and aspect-ratio buttons",
                        iconId = R.drawable.ic_view_list_custom,
                        checked = showZoomButtons,
                        onCheckedChange = { on ->
                            showZoomButtons = on
                            vidmaxPrefs.edit().putBoolean("show_zoom_buttons", on).apply()
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsToggleRow(
                        title = "Extra Buttons",
                        subtitle = "Show background play, timer, boost and fullscreen buttons",
                        iconId = R.drawable.ic_gear,
                        checked = showExtraButtons,
                        onCheckedChange = { on ->
                            showExtraButtons = on
                            vidmaxPrefs.edit().putBoolean("show_extra_buttons", on).apply()
                        }
                    )
                }

                // ── Library / Content ─────────────────────────────────────────
                item {
                    SettingsDivider()
                    SettingsSectionHeader(title = "Library / Content", paddingTop = 4.dp)
                }
                item {
                    SettingsToggleRow(
                        title = "Local Mode",
                        subtitle = "Show only local media features and hide streaming-related options",
                        iconId = R.drawable.ic_folder,
                        checked = localMode,
                        onCheckedChange = { viewModel.setLocalMode(it) }
                    )
                }

                // ── Updates ───────────────────────────────────────────────────
                item {
                    SettingsDivider()
                    SettingsSectionHeader(title = "Updates", paddingTop = 4.dp)
                }
                item {
                    SettingsToggleRow(
                        title = "Update Notifications",
                        subtitle = "Notify me when a new version is released",
                        iconId = R.drawable.ic_github,
                        checked = updateNotifications,
                        onCheckedChange = { on ->
                            updateNotifications = on
                            appPrefs.edit().putBoolean("update_notifications", on).apply()
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsItemPill(
                        title = "Check for Updates",
                        subtitle = if (isCheckingUpdate)
                            "Checking GitHub…"
                        else
                            "VidMax v${BuildConfig.VERSION_NAME} · Latest release",
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_github),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp), // Standardized Size
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailing = {
                            if (isCheckingUpdate) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = checkForUpdates
                    )
                }

                // ── Backup & Restore (P4b) ────────────────────────────────────
                item {
                    SettingsDivider()
                    SettingsSectionHeader(title = "Backup & Restore", paddingTop = 4.dp)
                }
                item {
                    SettingsItemPill(
                        title = "Export Settings",
                        subtitle = "Save settings to a JSON backup file",
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_share_custom),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp), // Standardized Size
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailing = {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        enabled = !isBackingUp,
                        onClick = { exportLauncher.launch("VidMax-settings-backup.json") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsItemPill(
                        title = "Import Settings",
                        subtitle = "Restore settings from a backup file",
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_folder_open),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp), // Standardized Size
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailing = {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        onClick = { importLauncher.launch(arrayOf("application/json")) }
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
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

            updateResult?.let { result ->
                UpdateResultDialog(
                    result = result,
                    onDismiss = { updateResult = null },
                    onOpenUrl = { url ->
                        updateResult = null
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    }
                )
            }

            pendingDeleteFont?.let { fontId ->
                AlertDialog(
                    onDismissRequest = { pendingDeleteFont = null },
                    title = { Text(text = "Remove Font?") },
                    text = {
                        Text(
                            text = "\"${AppFonts.displayNameFor(fontId)}\" will be removed from your imported fonts. " +
                                    "The app will switch back to the system default if it was active.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.deleteCustomFont(fontId)
                            pendingDeleteFont = null
                        }) {
                            Text(text = "Remove", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingDeleteFont = null }) {
                            Text(text = "Cancel")
                        }
                    }
                )
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

// ── Font Preview Card ─────────────────────────────────────────────────────────

@Composable
private fun FontPreviewCard(
    sampleText: String,
    displayName: String,
    fontFamily: FontFamily,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .width(104.dp)
                .height(92.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(
                    width = if (isSelected) 2.dp else 0.8.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(14.dp)
                )
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = sampleText,
                fontFamily = fontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 19.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            if (onDelete != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .clickable { onDelete() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove font",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = displayName,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

// ── Import Font Card ──────────────────────────────────────────────────────────

@Composable
private fun ImportFontCard(
    isBusy: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .width(104.dp)
                .height(92.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(14.dp)
                )
                .clickable(enabled = !isBusy) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            if (isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp) // Standardized Size
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Import\n.ttf / .otf",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        lineHeight = 12.sp
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Add Font",
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
            .padding(horizontal = 16.dp, vertical = 14.dp), // Polished Padding
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp, // Slightly larger for better readability
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp, // Slightly larger
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
                modifier = Modifier.size(24.dp), // Standardized Size
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
                modifier = Modifier.size(24.dp), // Standardized Size
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
            fontSize = 13.sp,
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
        fontSize = 12.sp, // Slightly larger
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 12.dp, top = paddingTop) // Polished Padding
    )
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        thickness = 0.5.dp,
        modifier = Modifier.padding(vertical = 20.dp)
    )
}
