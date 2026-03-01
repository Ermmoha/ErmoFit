package com.ermofit.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ermofit.app.ui.auth.LoginScreen
import com.ermofit.app.ui.auth.RegisterScreen
import com.ermofit.app.ui.auth.WelcomeScreen
import com.ermofit.app.ui.main.MainScreen

@Composable
fun ErmoFitNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = RootRoutes.Welcome
    ) {
        composable(RootRoutes.Welcome) {
            WelcomeScreen(
                onLoginClick = { navController.navigate(RootRoutes.Login) },
                onRegisterClick = { navController.navigate(RootRoutes.Register) },
                onAuthenticated = {
                    navController.navigate(RootRoutes.Main) {
                        popUpTo(RootRoutes.Welcome) { inclusive = true }
                    }
                }
            )
        }
        composable(RootRoutes.Login) {
            LoginScreen(
                onLoggedIn = {
                    navController.navigate(RootRoutes.Main) {
                        popUpTo(RootRoutes.Welcome) { inclusive = true }
                    }
                }
            )
        }
        composable(RootRoutes.Register) {
            RegisterScreen(
                onRegistered = {
                    navController.navigate(RootRoutes.Main) {
                        popUpTo(RootRoutes.Welcome) { inclusive = true }
                    }
                }
            )
        }
        composable(RootRoutes.Main) {
            MainScreen(
                onLogoutToWelcome = {
                    navController.navigate(RootRoutes.Welcome) {
                        popUpTo(RootRoutes.Main) { inclusive = true }
                    }
                }
            )
        }
    }
}
