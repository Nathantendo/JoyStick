package com.example.joystick.fileimport

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import com.example.joystick.R
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap


// Extended ShortcutItem to support customImageUri and types "app","file","web"
data class ShortcutItem(
    val id: String,
    val label: String,
    val packageName: String?, // null for web shortcuts
    val fileUri: String? = null, // file Uri (for file shortcuts)
    val type: String = "app", // "app" | "file" | "web"
    val webUrl: String? = null, // for web shortcuts
    val customImageUri: String? = null // optional user-provided thumbnail (persisted as URI string)
)

private const val PREFS_LAUNCHER_DATA = "launcher_data"
const val KEY_SHORTCUTS_JSON = "shortcuts_json"

/** Persist the list of shortcuts as a JSON array under launcher_data/shortcuts_json */
fun saveShortcutsList(context: Context, shortcuts: List<ShortcutItem>) {
    try {
        val arr = JSONArray()
        for (s in shortcuts) {
            val obj = JSONObject()
            obj.put("id", s.id)
            obj.put("label", s.label)
            if (s.packageName != null) obj.put("package", s.packageName) else obj.put("package", JSONObject.NULL)
            if (s.fileUri != null) obj.put("fileUri", s.fileUri) else obj.put("fileUri", JSONObject.NULL)
            obj.put("type", s.type)
            if (s.webUrl != null) obj.put("webUrl", s.webUrl) else obj.put("webUrl", JSONObject.NULL)
            if (s.customImageUri != null) obj.put("customImageUri", s.customImageUri) else obj.put("customImageUri", JSONObject.NULL)
            arr.put(obj)
        }
        context.getSharedPreferences(PREFS_LAUNCHER_DATA, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SHORTCUTS_JSON, arr.toString())
            .apply()
    } catch (_: Exception) {
        // Avoid crashing if prefs can't be written
    }
}

fun loadShortcutsList(context: Context): List<ShortcutItem> {
    val json = context.getSharedPreferences(PREFS_LAUNCHER_DATA, Context.MODE_PRIVATE)
        .getString(KEY_SHORTCUTS_JSON, "[]") ?: "[]"
    val arr = try { JSONArray(json) } catch (_: Exception) { JSONArray() }
    val out = mutableListOf<ShortcutItem>()
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val id = o.optString("id", UUID.randomUUID().toString())
        val label = o.optString("label", "Unnamed")
        val pkg = if (o.has("package") && !o.isNull("package")) o.optString("package") else null
        val fileUri = if (o.has("fileUri") && !o.isNull("fileUri")) o.optString("fileUri") else null
        val type = if (o.has("type")) o.optString("type", "app") else "app"
        val webUrl = if (o.has("webUrl") && !o.isNull("webUrl")) o.optString("webUrl") else null
        val customImageUri = if (o.has("customImageUri") && !o.isNull("customImageUri")) o.optString("customImageUri") else null

        out.add(
            ShortcutItem(
                id = id,
                label = label,
                packageName = pkg,
                fileUri = fileUri,
                type = type,
                webUrl = webUrl,
                customImageUri = customImageUri
            )
        )
    }
    return out
}

fun addShortcutItem(context: Context, item: ShortcutItem) {
    val list = loadShortcutsList(context).toMutableList()
    list.add(item)
    saveShortcutsList(context, list)
}

fun removeShortcutItem(context: Context, id: String) {
    val list = loadShortcutsList(context).filter { it.id != id }
    saveShortcutsList(context, list)
}

fun updateShortcutLabel(context: Context, id: String, newLabel: String) {
    val list = loadShortcutsList(context).map {
        if (it.id == id) it.copy(label = newLabel) else it
    }
    saveShortcutsList(context, list)
}

/** Set a custom image URI for a specific shortcut (persisted). Caller should take persistable permission. */
fun setShortcutCustomImage(context: Context, id: String, imageUri: Uri) {
    val list = loadShortcutsList(context).map {
        if (it.id == id) it.copy(customImageUri = imageUri.toString()) else it
    }
    saveShortcutsList(context, list)
}

/** Convenience to add a web shortcut from code */
fun addWebShortcut(context: Context, label: String, url: String) {
    val id = UUID.randomUUID().toString()
    val safeLabel = if (label.isBlank()) {
        try { Uri.parse(url).host ?: url } catch (_: Exception) { url }
    } else label
    val item = ShortcutItem(id = id, label = safeLabel, packageName = null, fileUri = null, type = "web", webUrl = url, customImageUri = null)
    addShortcutItem(context, item)
}

/**
 * Migrate old preferences (if present) into the new JSON-based shortcuts list.
 * - old "shortcuts" pref: label -> package
 * - old "game_prefs": package -> fileUri
 * - old "joystick_launcher" selected_apps: set of package names
 *
 * This is safe to call at app start and will only run once.
 */
fun migrateOldPrefsIfNeeded(context: Context) {
    val dataPrefs = context.getSharedPreferences(PREFS_LAUNCHER_DATA, Context.MODE_PRIVATE)
    val already = dataPrefs.contains(KEY_SHORTCUTS_JSON)
    if (already) return

    val oldShortcuts = context.getSharedPreferences("shortcuts", Context.MODE_PRIVATE)
    val gamePrefs = context.getSharedPreferences("game_prefs", Context.MODE_PRIVATE)
    val jlPrefs = context.getSharedPreferences("joystick_launcher", Context.MODE_PRIVATE)
    val savedPackages = jlPrefs.getStringSet("selected_apps", emptySet()) ?: emptySet()

    val newList = mutableListOf<ShortcutItem>()

    // Convert explicit label->package shortcuts (these were previously saved by older FileImport)
    oldShortcuts.all.forEach { (label, pkgAny) ->
        val pkg = pkgAny as? String ?: return@forEach
        val fileUriForPkg = gamePrefs.getString(pkg, null) // may be null
        val id = UUID.randomUUID().toString()
        newList.add(ShortcutItem(id = id, label = label, packageName = pkg, fileUri = fileUriForPkg, type = if (fileUriForPkg != null) "file" else "app", webUrl = null, customImageUri = null))
    }

    // For packages that are in selected_apps but had no explicit "shortcuts" entry,
    // create a default app-only shortcut using the app label.
    val pm = context.packageManager
    for (pkg in savedPackages) {
        // If already added above for this package as app-only, skip
        if (newList.any { it.packageName == pkg && it.fileUri == null }) continue
        try {
            val ai = pm.getApplicationInfo(pkg, 0)
            val label = pm.getApplicationLabel(ai).toString()
            val id = UUID.randomUUID().toString()
            newList.add(ShortcutItem(id = id, label = label, packageName = pkg, fileUri = null, type = "app", webUrl = null, customImageUri = null))
        } catch (_: Exception) {
            // ignore missing apps
        }
    }

    // Save migrated list (may be empty)
    saveShortcutsList(context, newList)
}
@Composable
fun AppSelectionScreen(
    fileUri: Uri?, // nullable; fileUri==null means "add app" flow, otherwise "associate file with app"
    onAppChosen: (String) -> Unit
) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    val isAppImport = fileUri == null

    Text(
        text = if (isAppImport) "Choose an app to add to your launcher"
        else "",
    )

    val apps = remember {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .map { it.activityInfo.applicationInfo }
            .filter { it.enabled }
            .sortedBy { it.loadLabel(packageManager).toString() }
    }

    Column(modifier = Modifier.padding(16.dp)) {

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 30.dp, horizontal = 20.dp)
        ) {
            Text(
                text = "Open File In...",
                color = Color.White,
                style = MaterialTheme.typography.h6
            )
        }



        Divider(color = Color.Gray, thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            contentPadding = PaddingValues(top = 32.dp)
        ) { items(apps) { app ->
                val appLabel = app.loadLabel(packageManager).toString()
                val packageName = app.packageName
                val icon = app.loadIcon(packageManager).toBitmap().asImageBitmap()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            // Create a unique shortcut entry for this selection (allows multiple shortcuts for same app)
                            val id = UUID.randomUUID().toString()
                            if (fileUri == null) {
                                // App-only shortcut
                                val item = ShortcutItem(id = id, label = appLabel, packageName = packageName, fileUri = null)
                                addShortcutItem(context, item)
                            } else {
                                // File -> app association:
                                try {
                                    // Persist read permission so the launcher (different process lifecycle) can open it later
                                    context.contentResolver.takePersistableUriPermission(
                                        fileUri,
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    )
                                } catch (_: Exception) {
                                }

                                val fileName = DocumentFile.fromSingleUri(context, fileUri)?.name
                                    ?: fileUri.lastPathSegment
                                    ?: "Game"

                                // Save file-specific shortcut using filename as label
                                val item = ShortcutItem(id = id, label = fileName, packageName = packageName, fileUri = fileUri.toString())
                                addShortcutItem(context, item)
                            }

                            // Notify caller with chosen package (or use id if you prefer)
                            onAppChosen(packageName)
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = appLabel,
                        color = Color.White,
                        style = MaterialTheme.typography.body1
                    )

                }
            }
        }
    }
}
