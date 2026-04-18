package com.yayo.sshtunneling

import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.core.net.toUri
import com.yayo.sshtunneling.ui.SshTunnelingApp
import com.yayo.sshtunneling.ui.TunnelViewModel
import com.yayo.sshtunneling.ui.theme.SshTunnelingTheme

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
class MainActivity : ComponentActivity() {
    private val viewModel: TunnelViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel.checkForAppUpdate()

        setContent {
            SshTunnelingTheme {
                val windowSizeClass = calculateWindowSizeClass(this)
                SshTunnelingApp(
                    viewModel = viewModel,
                    isExpanded = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact,
                    onInstallUpdate = ::installUpdate,
                )
            }
        }
    }

    private fun installUpdate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            Toast.makeText(this, getString(R.string.update_install_permission_required), Toast.LENGTH_LONG).show()
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    "package:$packageName".toUri(),
                )
            )
            return
        }

        viewModel.downloadAndInstallUpdate()
    }
}
