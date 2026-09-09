package com.vidmax.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidmax.player.BuildConfig
import com.vidmax.player.utils.UpdateChecker

/**
 * Shared dialog that renders the result of an update check.
 *
 * UI only: the GitHub fetch, version comparison and URL opening in
 * [UpdateChecker]/callers are untouched. Release notes are sanitized into a
 * clean bulleted list (no raw `##` / `**` / link markup).
 *
 * [onOpenUrl] is invoked with the APK asset URL (when present) or the GitHub
 * releases page URL so the caller can open it in the browser.
 */
@Composable
fun UpdateResultDialog(
    result: UpdateChecker.CheckResult,
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    when (result) {
        is UpdateChecker.CheckResult.Success -> {
            val info = result.info
            val isNewer = UpdateChecker.isNewerVersion(info)
            if (isNewer) {
                AlertDialog(
                    onDismissRequest = onDismiss,
                    shape = RoundedCornerShape(28.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    icon = {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Download,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(26.dp))
                            }
                        }
                    },
                    title = {
                        Text(
                            text = "New Version Available",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                        )
                    },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                VersionPill(
                                    text = "Current: v${BuildConfig.VERSION_NAME}",
                                    highlighted = false,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                VersionPill(
                                    text = "New: v${info.versionName}",
                                    highlighted = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            val bullets: List<String> = cleanReleaseNotes(info.releaseNotes)
                            if (bullets.isNotEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                        .padding(horizontal = 14.dp, vertical = 12.dp)
                                        .heightIn(max = 220.dp)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "What's new",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    bullets.forEach { line ->
                                        Row {
                                            Text(
                                                text = "• ",
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = line,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 13.sp,
                                                lineHeight = 17.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                onOpenUrl(info.downloadUrl.ifEmpty { info.releasePageUrl })
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Update Now", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = onDismiss) { Text("Later") }
                    },
                )
            } else {
                AlertDialog(
                    onDismissRequest = onDismiss,
                    shape = RoundedCornerShape(28.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    title = {
                        Text(
                            text = "You're up to date",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                        )
                    },
                    text = {
                        Text(
                            text = "VidMax ${info.versionName} is the latest version. No update needed.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = onDismiss) { Text("OK") }
                    },
                )
            }
        }

        UpdateChecker.CheckResult.NoRelease -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                shape = RoundedCornerShape(28.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                title = {
                    Text(
                        text = "You're up to date",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                },
                text = {
                    Text(
                        text = "No releases have been published yet. Stay tuned!",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                confirmButton = {
                    TextButton(onClick = onDismiss) { Text("OK") }
                },
            )
        }

        UpdateChecker.CheckResult.Failed -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                shape = RoundedCornerShape(28.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                title = {
                    Text(
                        text = "Couldn't check for updates",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                },
                text = {
                    Text(
                        text = "Please check your internet connection and try again.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                confirmButton = {
                    TextButton(onClick = onDismiss) { Text("OK") }
                },
            )
        }
    }
}

@Composable
private fun VersionPill(text: String, highlighted: Boolean, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
        ) {
            Text(
                text = text,
                color = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

/**
 * Sanitizes raw GitHub release markdown into clean bullet lines: strips
 * headers (`##`), bold/italic markers, inline links (`[text](url)` → text)
 * and asset-upload notices, drops blanks.
 */
private fun cleanReleaseNotes(raw: String): List<String> {
    if (raw.isBlank()) return emptyList()
    return raw.lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { line ->
            var s: String = line.replace(Regex("^#{1,6}\\s*"), "")
            s = s.replace("**", "").replace("__", "")
            s = s.replace(Regex("\\[([^\\]]+)\\]\\([^\\)]+\\)"), "$1")
            s = s.replace(Regex("^[-*•]\\s*"), "").trim()
            s
        }
        .filter { it.isNotEmpty() }
        .filterNot { line ->
            val lower: String = line.lowercase()
            (lower.contains("artifact") && lower.contains("upload")) ||
                lower.startsWith("full changelog") ||
                lower.startsWith("view full changelog")
        }
}
