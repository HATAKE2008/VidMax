package com.vidmax.player.ui.permission

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.vidmax.player.R
import com.vidmax.player.ui.theme.AppFonts
import com.vidmax.player.ui.theme.AppTheme
import com.vidmax.player.utils.AppLocale
import com.vidmax.player.utils.StorageAccess
import com.vidmax.player.viewmodel.DarkMode
import com.vidmax.player.viewmodel.LibraryViewModel

/**
 * VidMax first-launch setup / onboarding screen (mpvRex-style polish,
 * VidMax branding + VidMax theme system).
 *
 * Covers: Language + Storage Access + Font + Theme → Get Started.
 *
 * - Storage state is REAL: media runtime permission (via [mediaGranted],
 *   owned by MainActivity) + All-files access re-checked on every ON_RESUME.
 * - No intermediate "Allow Now" dialog: [onStorageAction] opens the correct
 *   direct Android flow (runtime permission request or system All-files page).
 * - Language / Font / Theme reuse the exact same prefs + ViewModel setters as
 *   Settings, so both screens always agree.
 */
@Composable
fun OnboardingSetupScreen(
    viewModel: LibraryViewModel,
    mediaGranted: Boolean,
    onStorageAction: () -> Unit,
    onGetStarted: () -> Unit
) {
    val context = LocalContext.current

    // Re-check All-files access every time we return from system settings,
    // so the card flips to granted immediately with no manual refresh.
    val activity = remember(context) { context as? androidx.activity.ComponentActivity }
    var resumeTick by remember { mutableStateOf(0) }
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose { activity?.lifecycle?.removeObserver(observer) }
    }
    val hasFullAccess = remember(context, resumeTick) {
        StorageAccess.hasFullStorageAccess(context)
    }

    val needsFullAccess = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    val storageReady = mediaGranted && (!needsFullAccess || hasFullAccess)

    val appLocale by viewModel.appLocale.collectAsState()
    val currentFontId by viewModel.appFontId.collectAsState()
    val importedFonts by viewModel.importedFonts.collectAsState()
    val currentTheme by viewModel.appTheme.collectAsState()
    val darkMode by viewModel.darkMode.collectAsState()
    val amoledMode by viewModel.amoledMode.collectAsState()

    var showLanguageDialog by remember { mutableStateOf(false) }
    var showFontDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }

    val isSystemDark = isSystemInDarkTheme()
    val isCurrentlyDark = when (darkMode) {
        DarkMode.Dark -> true
        DarkMode.Light -> false
        DarkMode.System -> isSystemDark
    }
    val themeSubtitle = buildString {
        append(currentTheme.name)
        append(" · ")
        append(darkMode.name)
        if (amoledMode && isCurrentlyDark) append(" · AMOLED")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = 520.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ── Branding ──────────────────────────────────────────
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "VidMax logo",
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(24.dp))
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Welcome to VidMax",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Your modern, powerful and\ncustomizable media player.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(24.dp))

                // ── Language ──────────────────────────────────────────
                SetupCard(
                    icon = Icons.Rounded.Language,
                    title = "Preferred Language",
                    subtitle = AppLocale.displayNameFor(appLocale),
                    actionLabel = "Change",
                    onAction = { showLanguageDialog = true }
                )
                Spacer(modifier = Modifier.height(12.dp))

                // ── Storage Access (real state, inline action) ────────
                if (storageReady) {
                    SetupCard(
                        icon = Icons.Rounded.CheckCircle,
                        title = "Storage Access Granted",
                        subtitle = "Permission is granted. VidMax is ready to discover and play your media.",
                        iconTint = MaterialTheme.colorScheme.primary,
                        onCardClick = null
                    )
                } else {
                    val subtitle = when {
                        !mediaGranted ->
                            "Permission is required to discover and play your media."
                        needsFullAccess && !hasFullAccess ->
                            "Media access granted. Enable All files access to finish setup."
                        else ->
                            "Permission is required to discover and play your media."
                    }
                    val buttonText = when {
                        !mediaGranted -> "Grant Storage Access"
                        needsFullAccess && !hasFullAccess -> "Open Settings"
                        else -> "Grant Storage Access"
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(16.dp)
                    ) {
                        SetupCardHeader(
                            icon = Icons.Rounded.FolderOpen,
                            title = "Storage Access",
                            subtitle = subtitle
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onStorageAction,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                text = buttonText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                // ── Font (only real, resolvable fonts) ────────────────
                SetupCard(
                    icon = Icons.Rounded.TextFields,
                    title = "Font",
                    subtitle = AppFonts.displayNameFor(currentFontId),
                    actionLabel = "Change",
                    onAction = { showFontDialog = true }
                )
                Spacer(modifier = Modifier.height(12.dp))

                // ── Theme (existing VidMax themes) ────────────────────
                SetupCard(
                    icon = Icons.Rounded.Palette,
                    title = "Theme",
                    subtitle = themeSubtitle,
                    actionLabel = "Change",
                    onAction = { showThemeDialog = true }
                )
                Spacer(modifier = Modifier.height(24.dp))

                // ── Get Started ───────────────────────────────────────
                Button(
                    onClick = {
                        if (storageReady) onGetStarted() else onStorageAction()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Get Started",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                if (!storageReady) {
                    Text(
                        text = "Storage access is required to continue.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    // ── Dialogs (same prefs as Settings) ──────────────────────────────
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(text = "Preferred Language") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(AppLocale.supported) { locale ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.setAppLocale(locale.tag)
                                    showLanguageDialog = false
                                }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = appLocale == locale.tag,
                                onClick = {
                                    viewModel.setAppLocale(locale.tag)
                                    showLanguageDialog = false
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = locale.displayName,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) { Text(text = "Done") }
            }
        )
    }

    if (showFontDialog) {
        val options: List<Pair<String, String>> = buildList {
            add(AppFonts.SYSTEM_DEFAULT to "System Default")
            AppFonts.builtInFonts.forEach { add(it.id to it.displayName) }
            importedFonts.forEach { add(it to AppFonts.displayNameFor(it)) }
        }
        AlertDialog(
            onDismissRequest = { showFontDialog = false },
            title = { Text(text = "Font") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                    items(options, key = { it.first }) { (id, name) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.setAppFont(id)
                                    showFontDialog = false
                                }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentFontId == id,
                                onClick = {
                                    viewModel.setAppFont(id)
                                    showFontDialog = false
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = name,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "VidMax Aa",
                                    fontFamily = AppFonts.resolveFontFamily(context, id),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFontDialog = false }) { Text(text = "Done") }
            }
        )
    }

    if (showThemeDialog) {
        val availableThemes = remember {
            AppTheme.values().filter { theme ->
                !theme.isDynamic || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            }
        }
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text(text = "Theme") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                    // Dark / Light / System
                    item {
                        Text(
                            text = "Appearance",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    items(listOf(DarkMode.Dark, DarkMode.Light, DarkMode.System)) { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { viewModel.setDarkMode(mode) }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = darkMode == mode,
                                onClick = { viewModel.setDarkMode(mode) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = mode.name,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp
                            )
                        }
                    }
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "AMOLED Black",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = amoledMode,
                                enabled = isCurrentlyDark,
                                onCheckedChange = { viewModel.setAmoledMode(it) }
                            )
                        }
                    }
                    item {
                        Text(
                            text = "Color theme",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                    items(availableThemes) { theme ->
                        val preview = if (isCurrentlyDark) theme.primaryDark else theme.primaryLight
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { viewModel.setAppTheme(theme) }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(preview)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = theme.name,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp,
                                modifier = Modifier.weight(1f)
                            )
                            if (currentTheme == theme) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text(text = "Done") }
            }
        )
    }
}

@Composable
private fun SetupCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    iconTint: androidx.compose.ui.graphics.Color? = null,
    onAction: (() -> Unit)? = null,
    onCardClick: (() -> Unit)? = onAction
) {
    val cardModifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(20.dp))
        .background(MaterialTheme.colorScheme.surfaceContainer)
        .then(if (onCardClick != null) Modifier.clickable { onCardClick() } else Modifier)
        .padding(16.dp)
    Column(modifier = cardModifier) {
        SetupCardHeader(
            icon = icon,
            title = title,
            subtitle = subtitle,
            iconTint = iconTint,
            actionLabel = actionLabel,
            onAction = onAction
        )
    }
}

@Composable
private fun SetupCardHeader(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: androidx.compose.ui.graphics.Color? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint ?: MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp),
                lineHeight = 17.sp
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = onAction) {
                Text(
                    text = actionLabel,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
