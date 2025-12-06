package com.example.joystick

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

@Composable
fun SettingsScreen(onBack: () -> Unit, navController: NavHostController) {
    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.padding(top = 12.dp),
                title = { Text("Settings", color = Color.White) },
                backgroundColor = Color(0xFF0A1128),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                contentColor = Color.White,
                elevation = 4.dp
            )
        },
        backgroundColor = Color(0xFF0A1128)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text("General", style = MaterialTheme.typography.h6, color = Color.White)

            // Dark Mode Toggle
            var darkModeEnabled by remember { mutableStateOf(false) }
            SettingItem(
                title = "Dark Mode",
                description = "Enable dark theme for the app",
                control = {
                    Switch(
                        checked = darkModeEnabled,
                        onCheckedChange = { darkModeEnabled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White)
                    )
                }
            )

            var notificationsEnabled by remember { mutableStateOf(true) }
            SettingItem(
                title = "Placeholder",
                description = "Placeholder",
                control = {
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = { notificationsEnabled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White)
                    )
                }
            )

            Divider(color = Color.Gray)

            Text("About", style = MaterialTheme.typography.h6, color = Color.White)

            SettingItem(
                title = "DevBuild",
                description = "0.8.2",
                control = {}
            )

            SettingItem(
                title = "Check For Updates",
                description = "Look At Github Repo.",
                control = {}
            )
        }
    }
}

@Composable
fun SettingItem(title: String, description: String, control: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.body1, color = Color.White)
            Text(description, style = MaterialTheme.typography.caption, color = Color.LightGray)
        }
        control()
    }
}
