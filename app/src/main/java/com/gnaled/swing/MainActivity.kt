package com.gnaled.swing

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.gnaled.swing.ui.SwingApp
import com.gnaled.swing.ui.theme.SwingTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SwingTheme {
                SwingApp()
            }
        }
    }
}
