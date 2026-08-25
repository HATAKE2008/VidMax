package com.vidmax.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge // 🔥 Edge to Edge ইম্পোর্ট
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.vidmax.player.ui.permission.PermissionScreen
import com.vidmax.player.ui.player.PlayerActivity
import com.vidmax.player.ui.screen.MainScreen
import com.vidmax.player.ui.screen.SplashScreen
import com.vidmax.player.ui.theme.AppFonts
import com.vidmax.player.ui.theme.VidMaxTheme
import com.vidmax.player.viewmodel.DarkMode
import com.vidmax.player.viewmodel.LibraryViewModel
import dagger.hilt.android.AndroidEntryPoint // 🔥 এই ইমপোর্টটি যুক্ত করা হলো

@AndroidEntryPoint // 🔥 Hilt-কে কাজ করানোর জন্য এই লাইনটি অত্যন্ত জরুরি
class MainActivity : ComponentActivity() {

  private val libraryViewModel: LibraryViewModel by viewModels()
  private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>

  override fun onCreate(savedInstanceState: Bundle?) {
    setTheme(R.style.Theme_VidMax_NoActionBar)

    enableEdgeToEdge()

    super.onCreate(savedInstanceState)

    permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            permissions ->
          val allGranted = permissions.entries.all { it.value }
          libraryViewModel.setPermissionGranted(allGranted)
        }

    val hasPermission: Boolean = checkStoragePermissions()
    libraryViewModel.setPermissionGranted(hasPermission)

    setContent {
      val currentTheme by libraryViewModel.appTheme.collectAsState()
      val permission by libraryViewModel.hasPermission.collectAsState()
      
      // 🔥 রিয়েল-টাইম ডার্ক মোড এবং অ্যামোলেড মোডের স্টেট
      val darkMode by libraryViewModel.darkMode.collectAsState()
      val amoledMode by libraryViewModel.amoledMode.collectAsState()

      // Font changer — resolves the selected (built-in or imported) font family
      val appFontId by libraryViewModel.appFontId.collectAsState()
      val appFontFamily = remember(appFontId) {
        AppFonts.resolveFontFamily(this@MainActivity, appFontId)
      }

      // ডার্ক মোড লজিক ক্যালকুলেট করা
      val isSystemDark = isSystemInDarkTheme()
      val useDarkTheme = when (darkMode) {
          DarkMode.Dark -> true
          DarkMode.Light -> false
          DarkMode.System -> isSystemDark
      }

      // 🔥 রিয়েল-টাইম থিম ভ্যালু পাস করা হচ্ছে
      VidMaxTheme(
          appTheme = currentTheme,
          useDarkTheme = useDarkTheme,
          amoledMode = amoledMode,
          appFontFamily = appFontFamily
      ) {
        // 🔥 স্প্ল্যাশ স্ক্রিন স্টেট
        var showSplash by remember { mutableStateOf(true) }

        if (showSplash) {
          SplashScreen(onSplashFinished = { showSplash = false })
        } else {
          // স্প্ল্যাশ শেষ হলে পারমিশন চেক করে মেইন অ্যাপে যাবে
          if (permission) {
            MainScreen(
                viewModel = libraryViewModel,
                onVideoClick = { videos, index ->
                  libraryViewModel.setRecentlyPlayedVideo(videos[index].title, videos[index].path)
                  PlayerActivity.start(this@MainActivity, videos.map { it.path }, index)
                })
          } else {
            PermissionScreen(onRequestPermission = { requestStoragePermissions() })
          }
        }
      }
    }
  }

  private fun checkStoragePermissions(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) ==
          PackageManager.PERMISSION_GRANTED &&
          ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) ==
              PackageManager.PERMISSION_GRANTED
    } else {
      ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) ==
          PackageManager.PERMISSION_GRANTED
    }
  }

  private fun requestStoragePermissions() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      permissionLauncher.launch(
          arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO))
    } else {
      permissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
    }
  }

  override fun onResume() {
    super.onResume()
    if (checkStoragePermissions()) {
      libraryViewModel.setPermissionGranted(true)
    }
  }
}
