package io.github.nathantendo.joystick

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.util.Patterns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.joystick.fileimport.*
import java.net.URI
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit

private const val GRID_COLUMNS = 3

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("QueryPermissionsNeeded")
@Composable
fun MainScreenView(context: Context, navController: NavHostController) {
    // Run migration once
    LaunchedEffect(Unit) { migrateOldPrefsIfNeeded(context) }
    // Shared UI state
    var showDialog by remember { mutableStateOf(false) }
    var showAppList by remember { mutableStateOf(false) }
    var showOptionsDialog by remember { mutableStateOf(false) }
    var longPressedShortcut by remember { mutableStateOf<ShortcutItem?>(null) }
    var renameText by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }

    // Pick-and-place move
    var pickedUpId by remember { mutableStateOf<String?>(null) }

    // Web-add dialog
    var showAddWebDialog by remember { mutableStateOf(false) }
    var webUrlInput by remember { mutableStateOf("") }
    var webLabelInput by remember { mutableStateOf("") }

    // Persistence-backed shortcuts
    val prefs = context.getSharedPreferences("launcher_data", Context.MODE_PRIVATE)
    var shortcuts by remember { mutableStateOf(loadShortcutsList(context)) }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_SHORTCUTS_JSON) shortcuts = loadShortcutsList(context)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    // Inline app picker data
    val allApps = remember { getLaunchableApps(context) }

    val gamePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let { navController.navigate("fileImport/${Uri.encode(it.toString())}") } }
    )


    // Image picker for custom thumbnails
    var imageTargetShortcutId by remember { mutableStateOf<String?>(null) }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                try { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
                imageTargetShortcutId?.let { id ->
                    setShortcutCustomImage(context, id, it)
                    shortcuts = loadShortcutsList(context)
                }
                imageTargetShortcutId = null
            }
        }
    )

    // Focused shortcut id for landscape preview animations
    var focusedShortcutId by remember { mutableStateOf<String?>(null) }

    // Orientation
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    // IMPORTANT: do NOT resize icons — keep icon size constant for both orientations.
    val iconSize = 85.dp

    // Keep portrait text size unchanged; use same base size for landscape labels (we will give more container space instead)
    val textSize = 14.sp

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("JoyStick Launcher") },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "SettingsScreen")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add")}
        }
    ) { padding ->
        if (isLandscape) {
            // LANDSCAPE: Console-style UI with animated focus & preview
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {

                Column(
                    modifier = Modifier
                        .width(0.dp) // make controller column take zero layout width so tiles start at the absolute left
                        .fillMaxHeight()
                        .padding(start = 0.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Keep controller visuals if you want them visible (they won't consume layout width)
                    Surface(modifier = Modifier.size(72.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer, tonalElevation = 8.dp) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Home, contentDescription = "Home", tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                    }
                    Surface(modifier = Modifier.size(72.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 6.dp) {
                        Box(contentAlignment = Alignment.Center) { Text("D-PAD", color = Color.White, fontSize = 12.sp) }
                    }
                }
                // Center: title, lazy row of big tiles, preview crossfade
                Column(
                    modifier = Modifier
                        .weight(0.68f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.Start
                ) {
                    Column {
                        Text("JoyStick Launcher", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
                        Spacer(Modifier.height(6.dp))
                        Text("Shortcuts: ${shortcuts.size}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        // Increase the row height in landscape so the label area can occupy more vertical space.
                        Row(
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .wrapContentWidth()
                                .height(iconSize + 95.dp), // give extra vertical space for wrapped labels
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            shortcuts.forEach { sc ->
                                // For landscape we allocate a wider tile (tileWidth) but keep iconSize unchanged.
                                val tileWidth = iconSize + 85.dp // tile will be wider than the icon, giving space for wrapped label
                                LandscapeAnimatedShortcut(
                                    context = context,
                                    sc = sc,
                                    iconSize = iconSize,
                                    tileWidth = tileWidth,
                                    textSize = textSize,
                                    onClick = { launchShortcut(context, sc) },
                                    onLongPress = {
                                        longPressedShortcut = sc
                                        renameText = sc.label
                                        showOptionsDialog = true
                                    },
                                    onFocused = { isFocused ->
                                        if (isFocused) focusedShortcutId = sc.id
                                    }
                                )
                            }
                        }
                    }

                    if (showSettings) {
                        // Show your real SettingsScreen composable (full-screen or dialog)
                        // Replace this with your actual SettingsScreen interface as needed:
                        SettingsScreen(
                            onBack = { showSettings = false },
                            navController = navController // if SettingsScreen needs this
                        )
                        // Early return to only show the settings screen, or overlay in a Box
                        return@Scaffold
                    }




                    // Crossfading preview area
                    Box(modifier = Modifier.fillMaxWidth().height(140.dp).padding(top = 8.dp), contentAlignment = Alignment.CenterStart) {
                        Crossfade(targetState = focusedShortcutId, animationSpec = tween(durationMillis = 300)) { id ->
                            val sc = shortcuts.find { it.id == id }
                            if (sc != null) {
                                Surface(modifier = Modifier.fillMaxWidth().height(140.dp), shape = RoundedCornerShape(16.dp), tonalElevation = 8.dp, color = MaterialTheme.colorScheme.surfaceVariant) {
                                    Row(modifier = Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(128.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                                            if (!sc.customImageUri.isNullOrBlank()) {
                                                AsyncImage(model = sc.customImageUri, contentDescription = sc.label, modifier = Modifier.fillMaxSize())
                                            } else if (sc.type == "web") {
                                                val fav = sc.webUrl?.let { "https://www.google.com/s2/favicons?sz=256&domain_url=${Uri.encode(it)}" }
                                                if (!fav.isNullOrBlank()) AsyncImage(model = fav, contentDescription = sc.label, modifier = Modifier.fillMaxSize()) else Icon(Icons.Default.Public, contentDescription = sc.label, tint = Color.White, modifier = Modifier.size(48.dp))
                                            } else {
                                                val iconBmp = try { context.packageManager.getApplicationIcon(sc.packageName ?: "").toBitmap().asImageBitmap() } catch (_: Exception) { null }
                                                if (iconBmp != null) Image(bitmap = iconBmp, contentDescription = sc.label, modifier = Modifier.fillMaxSize()) else Icon(Icons.Default.Info, contentDescription = sc.label, tint = Color.White, modifier = Modifier.size(48.dp))
                                            }
                                        }
                                        Spacer(Modifier.width(16.dp))
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Text(sc.label, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Spacer(Modifier.height(8.dp))
                                            Text(text = if (sc.type == "web") "Website: ${sc.webUrl}" else (sc.packageName ?: "App"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }


            }
        } else {
            // PORTRAIT: preserve existing Column-based UI exactly (but fix icon/text sizes)
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                Text("Shortcuts: ${shortcuts.size}", style = MaterialTheme.typography.bodySmall)

                LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
                    gridItems(items = shortcuts, key = { it.id }) { sc ->
                        val isPickedUp = sc.id == pickedUpId
                        ShortcutTile(
                            context = context,
                            sc = sc,
                            onLaunch = {
                                if (pickedUpId != null) {
                                    val srcId = pickedUpId!!
                                    val listNow = loadShortcutsList(context).toMutableList()
                                    val srcIndex = listNow.indexOfFirst { it.id == srcId }
                                    val dstIndex = listNow.indexOfFirst { it.id == sc.id }
                                    if (srcIndex != -1 && dstIndex != -1) {
                                        val item = listNow.removeAt(srcIndex)
                                        val insertIndex = if (srcIndex < dstIndex) dstIndex else dstIndex
                                        listNow.add(insertIndex, item)
                                        saveShortcutsList(context, listNow)
                                        shortcuts = loadShortcutsList(context)
                                        Toast.makeText(context, "Moved", Toast.LENGTH_SHORT).show()
                                    }
                                    pickedUpId = null
                                } else {
                                    launchShortcut(context, sc)
                                }
                            },
                            onLongPress = {
                                longPressedShortcut = sc
                                renameText = sc.label
                                showOptionsDialog = true
                            },
                            isPickedUp = isPickedUp,
                            isPortrait = true // explicitly indicate portrait to increase icon/text sizes
                        )
                    }
                }
            }
        }

        // Reuse dialog helpers (options / add web / import) shown over either layout
        if (showOptionsDialog && longPressedShortcut != null) {
            OptionsDialog(
                context = context,
                cur = longPressedShortcut!!,
                renameText = renameText,
                onRenameChange = { renameText = it },
                pickedUpId = pickedUpId,
                onTogglePickUp = {
                    if (pickedUpId == longPressedShortcut!!.id) { pickedUpId = null; Toast.makeText(context, "Place cancelled", Toast.LENGTH_SHORT).show() }
                    else { pickedUpId = longPressedShortcut!!.id; Toast.makeText(context, "Picked up '${longPressedShortcut!!.label}'. Tap a tile to place.", Toast.LENGTH_LONG).show() }
                    showOptionsDialog = false
                },
                onSetImage = {
                    imageTargetShortcutId = longPressedShortcut!!.id
                    imagePickerLauncher.launch(arrayOf("image/*"))
                    showOptionsDialog = false
                },
                onSave = {
                    updateShortcutLabel(context, longPressedShortcut!!.id, renameText)
                    shortcuts = loadShortcutsList(context)
                    showOptionsDialog = false
                },
                onDelete = {
                    removeShortcutItem(context, longPressedShortcut!!.id)
                    shortcuts = loadShortcutsList(context)
                    showOptionsDialog = false
                },
                onClose = { showOptionsDialog = false }
            )
        }

        if (showAddWebDialog) {
            AddWebDialog(
                context = context,
                webUrlInput = webUrlInput,
                webLabelInput = webLabelInput,
                onUrlChange = { webUrlInput = it },
                onLabelChange = { webLabelInput = it },
                onAdd = {
                    val raw = webUrlInput.trim()
                    val urlToUse = when {
                        raw.isEmpty() -> ""
                        raw.startsWith("http://") || raw.startsWith("https://") -> raw
                        else -> "https://$raw"
                    }
                    val isValid = urlToUse.isNotEmpty() &&
                            Patterns.WEB_URL.matcher(urlToUse).matches() &&
                            try { URI(urlToUse); true } catch (_: Exception) { false }

                    if (!isValid) {
                        Toast.makeText(context, "Please enter a valid URL", Toast.LENGTH_SHORT).show()
                    } else {
                        addWebShortcut(context, webLabelInput.trim(), urlToUse)
                        shortcuts = loadShortcutsList(context)
                        webUrlInput = ""
                        webLabelInput = ""
                        showAddWebDialog = false
                    }
                },
                onCancel = {
                    webUrlInput = ""
                    webLabelInput = ""
                    showAddWebDialog = false
                },

                textFieldModifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                textFieldTextStyle = TextStyle(fontSize = 14.sp), // smaller text
                textFieldSingleLine = true // keep compact
            )
        }


        if (showDialog) {
            AlertDialog(onDismissRequest = { showDialog = false }, title = { Text("Import Options") }, text = {
                Column {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            Button(onClick = { showDialog = false; gamePickerLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(32.dp), colors = ButtonDefaults.buttonColors()) {
                                Row(verticalAlignment = Alignment.CenterVertically) { Icon(
                                    imageVector = Icons.Filled.Description,
                                    contentDescription = "File Icon"
                                )
                                    ; Spacer(modifier = Modifier.width(8.dp)); Text("", maxLines = 1) }
                            }
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            Button(onClick = { showAppList = !showAppList }, modifier = Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(32.dp), colors = ButtonDefaults.buttonColors()) {
                                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Home, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text("", maxLines = 1) }
                            }
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            Button(onClick = { showDialog = false; showAddWebDialog = true }, modifier = Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(32.dp), colors = ButtonDefaults.buttonColors()) {
                                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Public, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text("", maxLines = 1) }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (showAppList) {
                        LazyColumn(modifier = Modifier.height(300.dp)) {
                            items(allApps) { appResolveInfo ->
                                val pm = context.packageManager
                                val label = appResolveInfo.loadLabel(pm).toString()
                                val iconDrawable = appResolveInfo.loadIcon(pm)
                                val iconBitmap = iconDrawable.toBitmap().asImageBitmap()
                                val packageName = appResolveInfo.activityInfo.packageName

                                Row(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = {
                                    val id = java.util.UUID.randomUUID().toString()
                                    val item = ShortcutItem(id = id, label = label, packageName = packageName, fileUri = null, type = "app", webUrl = null, customImageUri = null)
                                    addShortcutItem(context, item)
                                    shortcuts = loadShortcutsList(context)
                                    Toast.makeText(context, "App shortcut added: $label", Toast.LENGTH_SHORT).show()
                                    showAppList = false; showDialog = false
                                }, onLongClick = {}).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Image(bitmap = iconBitmap, contentDescription = null, modifier = Modifier.size(32.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(label)
                                }
                            }
                        }
                    }
                }
            }, confirmButton = { Button(onClick = { showDialog = false }) { Text("Close") } })
        }
    }
}


@Composable
private fun OptionsDialog(
    context: Context,
    cur: ShortcutItem,
    renameText: String,
    onRenameChange: (String) -> Unit,
    pickedUpId: String?,
    onTogglePickUp: () -> Unit,
    onSetImage: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit
) {
    AlertDialog(onDismissRequest = onClose, title = { Text("Shortcut Options") }, text = {
        Column {
            Surface(shape = RoundedCornerShape(6.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(value = renameText, onValueChange = onRenameChange, label = { Text("Label") }, modifier = Modifier.fillMaxWidth().height(72.dp), maxLines = 2)
            }
            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onTogglePickUp, modifier = Modifier.weight(1f), shape = RoundedCornerShape(24.dp), colors = ButtonDefaults.buttonColors()) {
                    Text(if (pickedUpId == cur.id) "Cancel Move" else "Pick Up")
                }
                Button(onClick = onSetImage, modifier = Modifier.weight(1f), shape = RoundedCornerShape(24.dp), colors = ButtonDefaults.buttonColors()) { Text("Set Image") }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row {
                Button(onClick = onSave) { Text("Save") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onDelete) { Text("Delete") }
            }
        }
    }, confirmButton = {
        TextButton(onClick = onClose) { Text("Close") }
    })
}

@Composable
private fun AddWebDialog(
    context: Context,
    webUrlInput: String,
    webLabelInput: String,
    onUrlChange: (String) -> Unit,
    onLabelChange: (String) -> Unit,
    onAdd: () -> Unit,
    onCancel: () -> Unit,
    textFieldModifier: Modifier,
    textFieldTextStyle: TextStyle,
    textFieldSingleLine: Boolean
) {
    AlertDialog(onDismissRequest = onCancel, title = { Text("Add Website Shortcut") }, text = {
        Column {
            Surface(shape = RoundedCornerShape(6.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(value = webUrlInput, onValueChange = onUrlChange, label = { Text("Website URL (https://...)") }, modifier = Modifier.fillMaxWidth().height(64.dp), maxLines = 2)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Surface(shape = RoundedCornerShape(6.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(value = webLabelInput, onValueChange = onLabelChange, label = { Text("Label (optional)") }, modifier = Modifier.fillMaxWidth().height(56.dp), maxLines = 2)
            }
        }
    }, confirmButton = {
        Row {
            Button(onClick = onAdd, colors = ButtonDefaults.buttonColors()) { Text("Add") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onCancel) { Text("Cancel") }
        }
    })
}

@Composable
private fun ImportDialog(
    context: Context,
    allApps: List<ResolveInfo>,
    onPickFiles: () -> Unit,
    onShowApps: () -> Unit,
    onShowWeb: () -> Unit,
    showAppList: Boolean,
    onAppAdded: (label: String, packageName: String) -> Unit,
    onClose: () -> Unit
) {
    AlertDialog(onDismissRequest = onClose, title = { Text("Import Options") }, text = {
        Column {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    Button(onClick = onPickFiles, modifier = Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(32.dp), colors = ButtonDefaults.buttonColors()) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Description, contentDescription = "File"); Spacer(Modifier.width(8.dp)); Text("Game Files", maxLines = 1) }
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    Button(onClick = onShowApps, modifier = Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(32.dp), colors = ButtonDefaults.buttonColors()) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Home, contentDescription = "Apps"); Spacer(Modifier.width(8.dp)); Text("Apps", maxLines = 1) }
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    Button(onClick = onShowWeb, modifier = Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(32.dp), colors = ButtonDefaults.buttonColors()) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Public, contentDescription = "Web"); Spacer(Modifier.width(8.dp)); Text("Websites", maxLines = 1) }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (showAppList) {
                LazyColumn(modifier = Modifier.height(300.dp)) {
                    items(allApps) { appResolveInfo ->
                        val pm = context.packageManager
                        val label = appResolveInfo.loadLabel(pm).toString()
                        val iconDrawable = appResolveInfo.loadIcon(pm)
                        val iconBitmap = iconDrawable.toBitmap().asImageBitmap()
                        val packageName = appResolveInfo.activityInfo.packageName

                        Row(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = {
                            onAppAdded(label, packageName)
                        }, onLongClick = {}).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Image(bitmap = iconBitmap, contentDescription = null, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            }
        }
    }, confirmButton = {
        Button(onClick = onClose) { Text("Close") }
    })
}


/* ---------------------------------------------------------------------------
   Animated Landscape Shortcut (focus-aware)
   --------------------------------------------------------------------------- */
@Composable
private fun LandscapeAnimatedShortcut(
    context: Context,
    sc: ShortcutItem,
    iconSize: Dp,   // actual icon box size (kept constant)
    tileWidth: Dp,  // larger tile width in landscape to make room for wrapped label
    textSize: TextUnit,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onFocused: (Boolean) -> Unit
) {
    var focused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(targetValue = if (focused) 1.06f else 1f, animationSpec = tween(200))
    val elevationDp by animateDpAsState(targetValue = if (focused) 18.dp else 4.dp, animationSpec = tween(200))

    Surface(
        modifier = Modifier
            .width(tileWidth)
            .shadow(elevationDp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .onFocusChanged { state ->
                val isF = state.isFocused
                if (isF != focused) {
                    focused = isF
                    onFocused(isF)
                }
            }
            .focusable()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = elevationDp
    ) {
        Column(
            modifier = Modifier
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon box — keep the icon size unchanged (iconSize)
            Box(modifier = Modifier.size(iconSize).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                if (!sc.customImageUri.isNullOrBlank()) {
                    AsyncImage(model = sc.customImageUri, contentDescription = sc.label, modifier = Modifier.fillMaxSize())
                } else if (sc.type == "web") {
                    val fav = sc.webUrl?.let { "https://www.google.com/s2/favicons?sz=256&domain_url=${Uri.encode(it)}" }
                    if (!fav.isNullOrBlank()) AsyncImage(model = fav, contentDescription = sc.label, modifier = Modifier.fillMaxSize()) else Icon(Icons.Default.Public, contentDescription = sc.label, tint = Color.White, modifier = Modifier.size(48.dp))
                } else {
                    val iconBmp = try { context.packageManager.getApplicationIcon(sc.packageName ?: "").toBitmap().asImageBitmap() } catch (_: Exception) { null }
                    if (iconBmp != null) Image(bitmap = iconBmp, contentDescription = sc.label, modifier = Modifier.fillMaxSize()) else Icon(Icons.Default.Info, contentDescription = sc.label, tint = Color.White, modifier = Modifier.size(48.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Landscape-only: allow wrapping and give up to 3 lines so labels up to 23 chars are visible.
            Text(
                text = sc.label,
                fontSize = textSize,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

        }
    }
}

/* ---------------------------------------------------------------------------
   Portrait tile (adjusted sizes for portrait)
   --------------------------------------------------------------------------- */
@Composable
private fun ShortcutTile(
    context: Context,
    sc: ShortcutItem,
    onLaunch: () -> Unit,
    onLongPress: () -> Unit,
    isPickedUp: Boolean = false,
    isPortrait: Boolean = false // added flag to increase sizes in portrait
) {
    // Increase icon and text sizes for portrait mode to be more legible.
    val sizeDp = if (isPortrait) 64.dp else 50.dp
    val labelFontSize = if (isPortrait) 12.sp else 8.sp

    Column(
        modifier = Modifier
            .padding(8.dp)
            .then(if (isPickedUp) Modifier.border(BorderStroke(2.dp, Color.Cyan), shape = RoundedCornerShape(8.dp)) else Modifier)
            .combinedClickable(onClick = onLaunch, onLongClick = onLongPress),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.size(sizeDp).clip(RoundedCornerShape(8.dp)).background(Color.Transparent), contentAlignment = Alignment.Center) {
            if (!sc.customImageUri.isNullOrBlank()) {
                AsyncImage(model = sc.customImageUri, contentDescription = sc.label, modifier = Modifier.size(sizeDp).clip(RoundedCornerShape(8.dp)))
            } else if (sc.type == "web") {
                Icon(imageVector = Icons.Default.Public, contentDescription = sc.label, tint = Color(0xFF37474F), modifier = Modifier.size(sizeDp * 0.9f))
                val faviconServiceUrl = sc.webUrl?.let { "https://www.google.com/s2/favicons?sz=128&domain_url=${Uri.encode(it)}" }
                if (!faviconServiceUrl.isNullOrBlank()) {
                    AsyncImage(model = faviconServiceUrl, contentDescription = sc.label, modifier = Modifier.size(sizeDp).clip(RoundedCornerShape(8.dp)))
                }
            } else {
                val iconBitmap = try { val pkg = sc.packageName; if (pkg.isNullOrBlank()) null else context.packageManager.getApplicationIcon(pkg).toBitmap().asImageBitmap() } catch (_: Exception) { null }
                if (iconBitmap != null) Image(bitmap = iconBitmap, contentDescription = sc.label, modifier = Modifier.size(sizeDp)) else Box(modifier = Modifier.size(sizeDp))
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = sc.label,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            fontSize = labelFontSize,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/* ---------------------------------------------------------------------------
   Helpers: launchShortcut, getLaunchableApps, dynamic shortcuts
   --------------------------------------------------------------------------- */

private fun launchShortcut(context: Context, sc: ShortcutItem) {
    if (sc.type == "web" && !sc.webUrl.isNullOrEmpty()) {
        try { val uri = Uri.parse(sc.webUrl); context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) } catch (e: Exception) { Toast.makeText(context, "Unable to open link", Toast.LENGTH_SHORT).show() }
        return
    }

    if (!sc.fileUri.isNullOrEmpty()) {
        try {
            val uri = Uri.parse(sc.fileUri)
            val mime = context.contentResolver.getType(uri) ?: "*/*"
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                setPackage(sc.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newRawUri("file", uri)
            }
            context.startActivity(viewIntent)
        } catch (e: Exception) {
            context.packageManager.getLaunchIntentForPackage(sc.packageName ?: "")?.let { context.startActivity(it) }
        }
    } else {
        context.packageManager.getLaunchIntentForPackage(sc.packageName ?: "")?.let { context.startActivity(it) }
    }
}

@SuppressLint("QueryPermissionsNeeded")
fun getLaunchableApps(context: Context): List<ResolveInfo> {
    val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
    return context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
}

fun loadDynamicShortcuts(context: Context): List<android.content.pm.ShortcutInfo> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
        val shortcutManager = context.getSystemService(android.content.pm.ShortcutManager::class.java)
        shortcutManager?.dynamicShortcuts ?: emptyList()
    } else {
        emptyList()
    }
}
