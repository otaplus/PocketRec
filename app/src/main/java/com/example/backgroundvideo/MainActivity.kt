package com.example.backgroundvideo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.backgroundvideo.ui.AppNavigation
import com.example.backgroundvideo.ui.theme.BackgroundVideoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BackgroundVideoTheme {
                AppNavigation()
            }
        }
    }
}
