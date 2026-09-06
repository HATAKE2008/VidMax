package com.vidmax.player.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Full-storage-access helper (All files access).
 *
 * VidMax is NOT distributed via Google Play, so MANAGE_EXTERNAL_STORAGE is
 * used to behave like a proper local video/file manager: direct filesystem
 * moves/renames/deletes without MediaStore consent dialogs, plus
 * MediaStore sync afterwards.
 *
 * - Android 11+ (R): Environment.isExternalStorageManager()
 * - Older: WRITE_EXTERNAL_STORAGE grant counts as full access.
 */
object StorageAccess {

  fun hasFullStorageAccess(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      Environment.isExternalStorageManager()
    } else {
      ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
      ) == PackageManager.PERMISSION_GRANTED
    }
  }

  fun statusText(context: Context): String {
    return if (hasFullStorageAccess(context)) {
      "Full storage access enabled"
    } else {
      "Full storage access disabled"
    }
  }

  /**
   * Opens the system "All files access" page scoped to VidMax when possible,
   * falling back to the generic list. Never crashes.
   */
  fun openAllFilesAccessSettings(context: Context) {
    runCatching {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        runCatching {
          val scoped = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}")
          ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
          context.startActivity(scoped)
        }.onFailure {
          val fallback = Intent(
            Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
          ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
          context.startActivity(fallback)
        }
      } else {
        // Pre-R has no all-files page; nothing to open.
      }
    }
  }
}
