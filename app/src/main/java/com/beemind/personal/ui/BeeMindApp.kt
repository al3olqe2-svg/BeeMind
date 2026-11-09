package com.beemind.personal.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.beemind.personal.ui.welcome.WelcomeScreen

private const val WELCOME = "welcome"

@Composable
fun BeeMindApp() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = WELCOME) {
        composable(WELCOME) { WelcomeScreen() }
    }
}
