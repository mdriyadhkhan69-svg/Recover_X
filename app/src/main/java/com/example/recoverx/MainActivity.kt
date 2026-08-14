package com.example.recoverx

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.recoverx.ui.navigation.MainScreen
import com.example.recoverx.ui.splash.SplashScreen
import com.example.recoverx.ui.theme.RecoverXTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode = com.example.recoverx.model.AppSettings.themeMode.value
            val useDarkTheme = when (themeMode) {
                com.example.recoverx.model.ThemeMode.DARK -> true
                com.example.recoverx.model.ThemeMode.LIGHT -> false
                com.example.recoverx.model.ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            RecoverXTheme(darkTheme = useDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RecoverXApp()
                }
            }
        }
    }
}

@Composable
fun RecoverXApp() {
    var showSplash by remember { mutableStateOf(true) }

    if (showSplash) {
        SplashScreen(onFinished = { showSplash = false })
    } else {
        MainScreen()
    }
}