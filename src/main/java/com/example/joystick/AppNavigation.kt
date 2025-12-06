package io.github.nathantendo.joystick

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.nathantendo.joystick.fileimport.AppSelectionScreen

@Composable
fun JoystickNavigation(context: Context) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            MainScreenView(context = context, navController = navController)

        }

        composable("fileImport/{fileUri}") { backStackEntry ->
            val rawUri = backStackEntry.arguments?.getString("fileUri")
            val fileUri = if (rawUri == "none") null else Uri.parse(rawUri)

            AppSelectionScreen(
                fileUri = fileUri,
                onAppChosen = { navController.popBackStack() }
            )
        }
    }

}
