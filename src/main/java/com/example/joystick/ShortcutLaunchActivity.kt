package io.github.nathantendo.joystick

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity

class ShortcutLaunchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri: Uri? = intent?.data
        if (uri != null) {
            val openIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(openIntent, "Open game with...")
            try {
                startActivity(chooser)
            } catch (e: Exception) {
                Toast.makeText(this, "No app found to open this file", Toast.LENGTH_SHORT).show()
            }
        }

        finish()
    }
}
