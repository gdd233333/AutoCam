package com.autocam.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.autocam.ui.AutoCamRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as AutoCamApp
        applyFlagSecure(app)
        setContent {
            AutoCamRoot(
                engine = app.engine,
                bus = app.bus,
                flags = app.flags,
                debugBundle = app.debugBundle,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        applyFlagSecure(application as AutoCamApp)
    }

    private fun applyFlagSecure(app: AutoCamApp) {
        if (app.flags.debugFlagSecure()) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
