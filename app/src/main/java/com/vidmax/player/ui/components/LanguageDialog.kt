package com.vidmax.player.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidmax.player.R
import com.vidmax.player.ui.theme.AppFonts
import com.vidmax.player.viewmodel.DarkMode

/**
 * Shared language picker used by both the onboarding setup screen and
 * Settings — same prefs, same ViewModel setter, same list.
 */
@Composable
fun LanguageSelectionDialog(
    currentTag: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.language_title)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                items(com.vidmax.player.utils.AppLocale.supported) { locale ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onSelect(locale.tag)
                                onDismiss()
                            }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentTag == locale.tag,
                            onClick = {
                                onSelect(locale.tag)
                                onDismiss()
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
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.action_done)) }
        }
    )
}

/**
 * Localized display name for a font id. Bundled/imported names are proper
 * nouns or file names and stay as-is; only "System Default" is translated.
 */
@Composable
fun fontDisplayName(fontId: String): String {
    return if (fontId == AppFonts.SYSTEM_DEFAULT) {
        stringResource(R.string.font_system_default)
    } else {
        AppFonts.displayNameFor(fontId)
    }
}

/** Localized Dark/Light/System mode name. */
@Composable
fun darkModeDisplayName(mode: DarkMode): String {
    return when (mode) {
        DarkMode.Dark -> stringResource(R.string.dark_mode_dark)
        DarkMode.Light -> stringResource(R.string.dark_mode_light)
        DarkMode.System -> stringResource(R.string.dark_mode_system)
    }
}
