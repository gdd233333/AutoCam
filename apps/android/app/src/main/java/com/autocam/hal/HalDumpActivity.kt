package com.autocam.hal

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

/**
 * Debug-only: request CAMERA then write hal_dump.json. No preview.
 */
class HalDumpActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            runDump()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQ)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            runDump()
        } else {
            Toast.makeText(this, "CAMERA denied; dump skipped combos", Toast.LENGTH_LONG).show()
            runDump()
        }
    }

    private fun runDump() {
        Executors.newSingleThreadExecutor().execute {
            val file = HalDumper(applicationContext).dumpToFile()
            runOnUiThread {
                Toast.makeText(this, "HAL dump: ${file.absolutePath}", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    companion object {
        private const val REQ = 47
    }
}
