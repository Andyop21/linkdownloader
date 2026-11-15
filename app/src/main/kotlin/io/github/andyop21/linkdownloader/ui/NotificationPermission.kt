package io.github.andyop21.linkdownloader.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.edit

/**
 * On Android 13 and later, asks once for `POST_NOTIFICATIONS` the first time a download starts
 * ([downloadRunning] turns true). The answer does not matter to the download: a refusal only hides
 * the notifications, and the app never asks again (docs/architecture.md § Permissions).
 */
@Composable
fun AskForNotificationsOnce(downloadRunning: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(downloadRunning) {
        if (!downloadRunning) return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (granted || prefs.getBoolean(KEY_ASKED, false)) return@LaunchedEffect
        prefs.edit { putBoolean(KEY_ASKED, true) }
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

private const val PREFS = "screen"
private const val KEY_ASKED = "notifications_asked"
