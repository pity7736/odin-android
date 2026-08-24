package dev.raiseexception.odin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.raiseexception.odin.accounts.presentation.registration.RegistrationScreen
import dev.raiseexception.odin.home.presentation.home.HomeScreen
import dev.raiseexception.odin.shared.presentation.Routes
import dev.raiseexception.odin.ui.theme.OdinTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel = (application as OdinApplication).appContainer.registrationViewModel()
        enableEdgeToEdge()
        setContent {
            OdinTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val navController = rememberNavController()
                    NavHost(
                        navController = navController,
                        startDestination = Routes.REGISTRATION,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable(Routes.REGISTRATION) {
                            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                            RegistrationScreen(
                                uiState = uiState,
                                onRegister = viewModel::register,
                                navigationEvent = viewModel.navigationEvent,
                                onRegistrationSuccess = {
                                    navController.navigate(Routes.HOME) {
                                        popUpTo(Routes.REGISTRATION) { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable(Routes.HOME) {
                            HomeScreen()
                        }
                    }
                }
            }
        }
    }
}
