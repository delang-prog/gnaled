package com.gnaled.swing.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.gnaled.swing.SwingApp
import com.gnaled.swing.data.AppContainer

@Composable
fun appContainer(): AppContainer {
    val app = LocalContext.current.applicationContext as SwingApp
    return app.container
}
