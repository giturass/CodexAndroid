package com.termuxcodex.client

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.edit
import com.termuxcodex.client.ui.CodexApp
import com.termuxcodex.client.ui.theme.TermuxCodexTheme

class MainActivity : ComponentActivity() {
    private val codexViewModel: CodexViewModel by viewModels()
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TermuxCodexTheme(themeMode = codexViewModel.uiState.themeMode) {
                CodexApp(codexViewModel)
            }
        }
        codexViewModel.handleNotificationIntent(intent)
        requestNotificationPermissionOnFirstLaunch()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        codexViewModel.handleNotificationIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        codexViewModel.setAppInForeground(true)
    }

    override fun onStop() {
        codexViewModel.setAppInForeground(false)
        super.onStop()
    }

    private fun requestNotificationPermissionOnFirstLaunch() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val onboarding = getSharedPreferences("onboarding", MODE_PRIVATE)
        if (onboarding.getBoolean("notification_permission_requested", false)) return
        onboarding.edit { putBoolean("notification_permission_requested", true) }
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
