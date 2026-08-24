package dev.raiseexception.odin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.raiseexception.odin.accounts.presentation.login.LoginScreen
import dev.raiseexception.odin.accounts.presentation.login.LoginViewModel
import dev.raiseexception.odin.accounts.presentation.registration.RegistrationScreen
import dev.raiseexception.odin.accounts.presentation.registration.RegistrationViewModel
import dev.raiseexception.odin.accounts.presentation.startup.StartupState
import dev.raiseexception.odin.home.presentation.home.HomeScreen
import dev.raiseexception.odin.shared.presentation.Routes
import dev.raiseexception.odin.ui.theme.OdinTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        val appContainer = (application as OdinApplication).appContainer
        val startupViewModel = appContainer.startupViewModel()
        val registrationViewModel = appContainer.registrationViewModel()
        val loginViewModel = appContainer.loginViewModel()
        splashScreen.setKeepOnScreenCondition { startupViewModel.state.value is StartupState.Deciding }
        enableEdgeToEdge()
        setContent {
            OdinTheme {
                val startupState by startupViewModel.state.collectAsStateWithLifecycle()
                when (val decided = startupState) {
                    is StartupState.Deciding -> Unit
                    is StartupState.Decided -> AppNavHost(
                        startRoute = decided.startRoute,
                        registrationViewModel = registrationViewModel,
                        loginViewModel = loginViewModel
                    )
                }
            }
        }
    }
}

@Composable
private fun AppNavHost(
    startRoute: String,
    registrationViewModel: RegistrationViewModel,
    loginViewModel: LoginViewModel
) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        val navController = rememberNavController()
        NavHost(
            navController = navController,
            startDestination = startRoute,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.REGISTRATION) {
                RegistrationDestination(registrationViewModel, navController)
            }
            composable(Routes.LOGIN) {
                LoginDestination(loginViewModel, navController)
            }
            composable(Routes.HOME) {
                HomeScreen()
            }
        }
    }
}

@Composable
private fun RegistrationDestination(
    registrationViewModel: RegistrationViewModel,
    navController: NavHostController
) {
    val uiState by registrationViewModel.uiState.collectAsStateWithLifecycle()
    RegistrationScreen(
        uiState = uiState,
        onRegister = registrationViewModel::register,
        navigationEvent = registrationViewModel.navigationEvent,
        onRegistrationSuccess = {
            navController.navigate(Routes.HOME) {
                popUpTo(Routes.REGISTRATION) { inclusive = true }
            }
        }
    )
}

@Composable
private fun LoginDestination(
    loginViewModel: LoginViewModel,
    navController: NavHostController
) {
    val uiState by loginViewModel.uiState.collectAsStateWithLifecycle()
    LoginScreen(
        uiState = uiState,
        onLogin = loginViewModel::login,
        navigationEvent = loginViewModel.navigationEvent,
        onLoginSuccess = {
            navController.navigate(Routes.HOME) {
                popUpTo(Routes.LOGIN) { inclusive = true }
            }
        }
    )
}
