package com.kARThik.vote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.kARThik.vote.settings.AppSettings
import com.kARThik.vote.ui.AppNav
import com.kARThik.vote.ui.MatamTheme
import com.kARThik.vote.ui.container

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setContent {
            val settings by container().settings.flow.collectAsState(initial = AppSettings())
            MatamTheme(settings.theme) { AppNav() }
        }
    }
}
