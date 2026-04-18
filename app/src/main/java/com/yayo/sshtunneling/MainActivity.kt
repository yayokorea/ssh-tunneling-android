package com.yayo.sshtunneling

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import com.yayo.sshtunneling.ui.SshTunnelingApp
import com.yayo.sshtunneling.ui.TunnelViewModel
import com.yayo.sshtunneling.ui.theme.SshTunnelingTheme

class MainActivity : ComponentActivity() {
    private val viewModel: TunnelViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SshTunnelingTheme {
                val windowSizeClass = calculateWindowSizeClass(this)
                SshTunnelingApp(
                    viewModel = viewModel,
                    isExpanded = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact,
                )
            }
        }
    }
}
