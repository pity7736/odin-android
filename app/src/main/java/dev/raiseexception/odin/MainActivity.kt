package dev.raiseexception.odin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.raiseexception.odin.accounting.presentation.accountcreation.CreateAccountScreen
import dev.raiseexception.odin.accounting.presentation.accountcreation.CreateAccountViewModel
import dev.raiseexception.odin.accounting.presentation.accountslist.AccountsListScreen
import dev.raiseexception.odin.accounts.presentation.login.LoginScreen
import dev.raiseexception.odin.accounts.presentation.login.LoginViewModel
import dev.raiseexception.odin.accounts.presentation.registration.RegistrationScreen
import dev.raiseexception.odin.accounts.presentation.registration.RegistrationViewModel
import dev.raiseexception.odin.accounts.presentation.startup.StartupState
import dev.raiseexception.odin.home.presentation.home.HomeScreen
import dev.raiseexception.odin.shared.presentation.Routes
import dev.raiseexception.odin.ui.theme.OdinTheme

class MainActivity : ComponentActivity() {

    private val loginViewModel: LoginViewModel by viewModels {
        viewModelFactory {
            initializer { (application as OdinApplication).appContainer.loginViewModel() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        val appContainer = (application as OdinApplication).appContainer
        val startupViewModel = appContainer.startupViewModel()
        val registrationViewModel = appContainer.registrationViewModel()
        val createAccountViewModel = appContainer.createAccountViewModel()
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
                        loginViewModel = loginViewModel,
                        createAccountViewModel = createAccountViewModel
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
    loginViewModel: LoginViewModel,
    createAccountViewModel: CreateAccountViewModel
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
                HomeScreen(onOpenAccounts = { navController.navigate(Routes.ACCOUNTS) })
            }
            composable(Routes.ACCOUNTS) {
                AccountsListScreen(onCreateAccount = { navController.navigate(Routes.ACCOUNT_CREATE) })
            }
            composable(Routes.ACCOUNT_CREATE) {
                CreateAccountDestination(createAccountViewModel, navController)
            }
        }
    }
}

@Composable
private fun CreateAccountDestination(
    createAccountViewModel: CreateAccountViewModel,
    navController: NavHostController
) {
    val uiState by createAccountViewModel.uiState.collectAsStateWithLifecycle()
    CreateAccountScreen(
        uiState = uiState,
        onCreate = createAccountViewModel::create,
        navigationEvent = createAccountViewModel.navigationEvent,
        onCreateSuccess = {
            navController.navigate(Routes.ACCOUNTS) {
                popUpTo(Routes.ACCOUNT_CREATE) { inclusive = true }
            }
        }
    )
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
