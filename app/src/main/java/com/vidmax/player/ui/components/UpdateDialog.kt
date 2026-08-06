package com.vidmax.player.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidmax.player.BuildConfig
import com.vidmax.player.utils.UpdateChecker

/**
 * Shared dialog that renders the result of an update check.
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
                    title = {
                        Text(
                            text = "Update available",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                        )
                    },
                    text = {
                        Column {
                            Text(
                                text = "VidMax ${info.versionName} is available. You are on ${BuildConfig.VERSION_NAME}.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (info.releaseNotes.isNotEmpty()) {
                                Text(
                                    text = "What's new:",
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 12.dp),
                                )
                                Text(
                                    text = info.releaseNotes,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .heightIn(max = 180.dp)
                                        .verticalScroll(rememberScrollState()),
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                onOpenUrl(info.downloadUrl.ifEmpty { info.releasePageUrl })
                            }
                        ) {
                            Text("Update Now")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = onDismiss) { Text("Later") }
                    },
                )
            } else {
                AlertDialog(
                    onDismissRequest = onDismiss,
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
