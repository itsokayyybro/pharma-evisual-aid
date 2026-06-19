package com.pharma.evisualaid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.pharma.evisualaid.ui.theme.EVisualAidTheme
import com.pharma.evisualaid.ui.viewer.ViewerScreen

/**
 * Single-screen host. There is no navigation graph: the app launches directly into the
 * immersive [ViewerScreen], which shows either a "+" upload button or the loaded catalog.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EVisualAidTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    ViewerScreen()
                }
            }
        }
    }
}
