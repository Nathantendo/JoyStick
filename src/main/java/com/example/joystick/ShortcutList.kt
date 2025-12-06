package io.github.nathantendo.joystick.ui

import android.content.Context
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@RequiresApi(Build.VERSION_CODES.N_MR1)
@Composable
fun ShortcutList(context: Context) {
    val shortcuts by remember { mutableStateOf(loadDynamicShortcuts(context)) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (shortcuts.isEmpty()) {
            item {
                Text("No shortcuts created yet.")
            }
        } else {
            items(shortcuts) { shortcut ->
                val label = getShortcutLabel(shortcut)
                Text(
                    text = label,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clickable {
                            shortcut.intent?.let { intent ->
                                context.startActivity(intent)
                            }
                        }
                )
            }
        }
    }
}

private fun loadDynamicShortcuts(context: Context): List<ShortcutInfo> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
        val shortcutManager = context.getSystemService(ShortcutManager::class.java)
        shortcutManager?.dynamicShortcuts ?: emptyList()
    } else {
        emptyList()
    }
}

private fun getShortcutLabel(shortcut: ShortcutInfo): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
        shortcut.shortLabel?.toString() ?: "Unnamed Shortcut"
    } else {
        "Unsupported Shortcut"
    }
}
