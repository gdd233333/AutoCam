package com.autocam.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.autocam.ui.AutoCamRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as AutoCamApp
        setContent {
            AutoCamRoot(
                engine = app.engine,
                bus = app.bus,
                mock = app.isMock,
            )
        }
    }
}
