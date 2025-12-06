package com.example.joystick

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.joystick.ui.ShortcutList
import java.io.File

@RequiresApi(Build.VERSION_CODES.N_MR1)
@Composable
fun MainScreen(context: Context) {
    Column(modifier = Modifier.fillMaxSize()) {
        GameList(context)
        Spacer(modifier = Modifier.height(24.dp))
        ShortcutList(context)
    }
}

@Composable
fun GameList(context: Context) {
    val gameFiles by remember { mutableStateOf(loadGameFiles()) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (gameFiles.isEmpty()) {
            Text("No games found")
        } else {
            gameFiles.forEach { file ->
                val label = file.nameWithoutExtension
                Text(
                    text = file.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clickable {
                            val uri = Uri.fromFile(file)
                            createGameShortcut(context, label, uri)
                            Toast.makeText(context, "Shortcut created for $label", Toast.LENGTH_SHORT).show()
                        }
                )
            }
        }
    }
}

private fun loadGameFiles(): List<File> {
    val gameDir = Environment.getExternalStorageDirectory()
    return gameDir.listFiles()?.filter {
        it.isFile && (it.extension == "game" || it.extension == "zip" || it.extension == "apk")
    } ?: emptyList()
}
