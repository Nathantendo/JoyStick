package io.github.nathantendo.joystick

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

fun createGameShortcut(context: Context, label: String, uri: Uri) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
        val shortcutManager = context.getSystemService(android.content.pm.ShortcutManager::class.java)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = uri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val shortcut = android.content.pm.ShortcutInfo.Builder(context, label.replace(" ", "_"))
            .setShortLabel(label)
            .setIntent(intent)
            .build()
        shortcutManager.addDynamicShortcuts(listOf(shortcut))
    }
}
