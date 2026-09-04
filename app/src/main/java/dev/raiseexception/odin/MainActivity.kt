@file:Suppress("TooManyFunctions")

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
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.raiseexception.odin.accounting.presentation.accountcreation.CreateAccountScreen
import dev.raiseexception.odin.accounting.presentation.accountcreation.CreateAccountViewModel
import dev.raiseexception.odin.accounting.presentation.accountdetail.AccountDetailScreen
import dev.raiseexception.odin.accounting.presentation.accountdetail.AccountDetailViewModel
import dev.raiseexception.odin.accounting.presentation.accountslist.AccountsListScreen
import dev.raiseexception.odin.accounting.presentation.accountslist.AccountsListViewModel
import dev.raiseexception.odin.accounting.presentation.categorieslist.CategoriesListScreen
import dev.raiseexception.odin.accounting.presentation.categorieslist.CategoriesListViewModel
import dev.raiseexception.odin.accounting.presentation.categorycreation.CreateCategoryScreen
import dev.raiseexception.odin.accounting.presentation.categorycreation.CreateCategoryViewModel
import dev.raiseexception.odin.accounting.presentation.categorydetail.CategoryDetailScreen
import dev.raiseexception.odin.accounting.presentation.expensecreation.CreateExpenseScreen
import dev.raiseexception.odin.accounting.presentation.expensecreation.CreateExpenseViewModel
import dev.raiseexception.odin.accounting.presentation.incomecreation.CreateIncomeScreen
import dev.raiseexception.odin.accounting.presentation.incomecreation.CreateIncomeViewModel
import dev.raiseexception.odin.accounting.presentation.transactiondetail.TransactionDetailScreen
import dev.raiseexception.odin.accounts.presentation.login.LoginScreen
import dev.raiseexception.odin.accounts.presentation.login.LoginViewModel
import dev.raiseexception.odin.accounts.presentation.registration.RegistrationScreen
import dev.raiseexception.odin.accounts.presentation.registration.RegistrationViewModel
import dev.raiseexception.odin.accounts.presentation.startup.StartupState
import dev.raiseexception.odin.accounts.presentation.startup.StartupViewModel
import dev.raiseexception.odin.home.presentation.home.HomeScreen
import dev.raiseexception.odin.home.presentation.home.HomeViewModel
import dev.raiseexception.odin.shared.presentation.Routes
import dev.raiseexception.odin.ui.theme.OdinTheme

class MainActivity : ComponentActivity() {

    private val startupViewModel: StartupViewModel by viewModels {
        viewModelFactory {
            initializer { (application as OdinApplication).appContainer.startupViewModel() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { this.startupViewModel.state.value is StartupState.Deciding }
        enableEdgeToEdge()
        setContent {
            OdinTheme {
                val startupState by startupViewModel.state.collectAsStateWithLifecycle()
                when (val decided = startupState) {
                    is StartupState.Deciding -> Unit
                    is StartupState.Decided -> AppNavHost(startRoute = decided.startRoute)
                }
            }
        }
    }
}

@Composable
private fun AppNavHost(startRoute: String) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        val navController = rememberNavController()
        NavHost(
            navController = navController,
            startDestination = startRoute,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.REGISTRATION) {
                RegistrationDestination(navController)
            }
            composable(Routes.LOGIN) {
                LoginDestination(navController)
            }
            composable(Routes.HOME) {
                HomeDestination(navController)
            }
            composable(Routes.TRANSACTION_DETAIL) { backStackEntry ->
                val transactionId = backStackEntry.arguments?.getString("transactionId") ?: ""
                TransactionDetailScreen(transactionId = transactionId)
            }
            composable(Routes.ACCOUNTS) {
                AccountsListDestination(navController)
            }
            composable(Routes.ACCOUNT_CREATE) {
                CreateAccountDestination(navController)
            }
            composable(Routes.ACCOUNT_DETAIL) { backStackEntry ->
                val accountId = backStackEntry.arguments?.getString("accountId") ?: ""
                AccountDetailDestination(accountId, navController)
            }
            composable(Routes.INCOME_CREATE) { backStackEntry ->
                val accountId = backStackEntry.arguments?.getString("accountId") ?: ""
                CreateIncomeDestination(accountId, navController)
            }
            composable(Routes.EXPENSE_CREATE) { backStackEntry ->
                val accountId = backStackEntry.arguments?.getString("accountId") ?: ""
                CreateExpenseDestination(accountId, navController)
            }
            composable(Routes.CATEGORIES) {
                CategoriesListDestination(navController)
            }
            composable(Routes.CATEGORY_CREATE) {
                CreateCategoryDestination(navController)
            }
            composable(Routes.CATEGORY_DETAIL) { backStackEntry ->
                val categoryId = backStackEntry.arguments?.getString("categoryId") ?: ""
                CategoryDetailScreen(categoryId = categoryId)
            }
        }
    }
}

@Composable
private fun RegistrationDestination(navController: NavHostController) {
    val application = LocalContext.current.applicationContext as OdinApplication
    val registrationViewModel: RegistrationViewModel = viewModel {
        application.appContainer.registrationViewModel()
    }
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
private fun LoginDestination(navController: NavHostController) {
    val application = LocalContext.current.applicationContext as OdinApplication
    val loginViewModel: LoginViewModel = viewModel {
        application.appContainer.loginViewModel()
    }
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

@Composable
private fun AccountsListDestination(navController: NavHostController) {
    val application = LocalContext.current.applicationContext as OdinApplication
    val accountsListViewModel: AccountsListViewModel = viewModel {
        application.appContainer.accountsListViewModel()
    }
    val uiState by accountsListViewModel.uiState.collectAsStateWithLifecycle()
    AccountsListScreen(
        uiState = uiState,
        navigationEvent = accountsListViewModel.navigationEvent,
        onCreateAccount = { navController.navigate(Routes.ACCOUNT_CREATE) },
        onAccountSelected = accountsListViewModel::onAccountSelected,
        onNavigateToAccountDetail = { accountId ->
            navController.navigate(Routes.accountDetail(accountId))
        }
    )
}

@Composable
private fun CreateAccountDestination(navController: NavHostController) {
    val application = LocalContext.current.applicationContext as OdinApplication
    val createAccountViewModel: CreateAccountViewModel = viewModel {
        application.appContainer.createAccountViewModel()
    }
    val uiState by createAccountViewModel.uiState.collectAsStateWithLifecycle()
    CreateAccountScreen(
        uiState = uiState,
        onCreate = createAccountViewModel::create,
        navigationEvent = createAccountViewModel.navigationEvent,
        onCreateSuccess = {
            navController.navigate(Routes.ACCOUNTS) {
                popUpTo(Routes.ACCOUNTS) { inclusive = true }
            }
        }
    )
}

@Composable
private fun AccountDetailDestination(accountId: String, navController: NavHostController) {
    val application = LocalContext.current.applicationContext as OdinApplication
    val accountDetailViewModel: AccountDetailViewModel = viewModel(
        factory = application.appContainer.accountDetailViewModelFactory(accountId)
    )
    val uiState by accountDetailViewModel.uiState.collectAsStateWithLifecycle()
    AccountDetailScreen(
        uiState = uiState,
        navigationEvent = accountDetailViewModel.navigationEvent,
        onCreateIncome = {
            navController.navigate(Routes.incomeCreate(accountId))
        },
        onCreateExpense = {
            navController.navigate(Routes.expenseCreate(accountId))
        },
        onFilterChanged = accountDetailViewModel::onFilterChanged,
        onResume = accountDetailViewModel::reload
    )
}

@Composable
private fun CreateIncomeDestination(accountId: String, navController: NavHostController) {
    val application = LocalContext.current.applicationContext as OdinApplication
    val createIncomeViewModel: CreateIncomeViewModel = viewModel(
        factory = application.appContainer.createIncomeViewModelFactory(accountId)
    )
    val uiState by createIncomeViewModel.uiState.collectAsStateWithLifecycle()
    CreateIncomeScreen(
        uiState = uiState,
        onSave = createIncomeViewModel::save,
        navigationEvent = createIncomeViewModel.navigationEvent,
        onNavigateBack = { navController.popBackStack() }
    )
}

@Composable
private fun CreateExpenseDestination(accountId: String, navController: NavHostController) {
    val application = LocalContext.current.applicationContext as OdinApplication
    val createExpenseViewModel: CreateExpenseViewModel = viewModel(
        factory = application.appContainer.createExpenseViewModelFactory(accountId)
    )
    val uiState by createExpenseViewModel.uiState.collectAsStateWithLifecycle()
    CreateExpenseScreen(
        uiState = uiState,
        onSave = createExpenseViewModel::save,
        navigationEvent = createExpenseViewModel.navigationEvent,
        onNavigateBack = { navController.popBackStack() }
    )
}

@Composable
private fun CreateCategoryDestination(navController: NavHostController) {
    val application = LocalContext.current.applicationContext as OdinApplication
    val createCategoryViewModel: CreateCategoryViewModel = viewModel {
        application.appContainer.createCategoryViewModel()
    }
    val uiState by createCategoryViewModel.uiState.collectAsStateWithLifecycle()
    CreateCategoryScreen(
        uiState = uiState,
        onCreate = createCategoryViewModel::create,
        navigationEvent = createCategoryViewModel.navigationEvent,
        onCreateSuccess = {
            navController.navigate(Routes.CATEGORIES) {
                popUpTo(Routes.CATEGORIES) { inclusive = true }
            }
        }
    )
}

@Composable
private fun HomeDestination(navController: NavHostController) {
    val application = LocalContext.current.applicationContext as OdinApplication
    val homeViewModel: HomeViewModel = viewModel {
        application.appContainer.homeViewModel()
    }
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        uiState = uiState,
        navigationEvent = homeViewModel.navigationEvent,
        onAccountSelected = homeViewModel::onAccountSelected,
        onTransactionSelected = homeViewModel::onTransactionSelected,
        onCreateAccountSelected = homeViewModel::onCreateAccountSelected,
        onNavigateToAccountDetail = { accountId ->
            navController.navigate(Routes.accountDetail(accountId))
        },
        onNavigateToTransactionDetail = { transactionId ->
            navController.navigate(Routes.transactionDetail(transactionId))
        },
        onNavigateToAccountCreate = { navController.navigate(Routes.ACCOUNT_CREATE) },
        onNavigateToAccounts = { navController.navigate(Routes.ACCOUNTS) },
        onNavigateToCategories = { navController.navigate(Routes.CATEGORIES) },
    )
}

@Composable
private fun CategoriesListDestination(navController: NavHostController) {
    val application = LocalContext.current.applicationContext as OdinApplication
    val categoriesListViewModel: CategoriesListViewModel = viewModel {
        application.appContainer.categoriesListViewModel()
    }
    val uiState by categoriesListViewModel.uiState.collectAsStateWithLifecycle()
    CategoriesListScreen(
        uiState = uiState,
        navigationEvent = categoriesListViewModel.navigationEvent,
        onCreateCategory = { navController.navigate(Routes.CATEGORY_CREATE) },
        onFilterChanged = categoriesListViewModel::onFilterChanged,
        onSearchQueryChanged = categoriesListViewModel::onSearchQueryChanged,
        onCategorySelected = categoriesListViewModel::onCategorySelected,
        onNavigateToCategoryDetail = { categoryId ->
            navController.navigate(Routes.categoryDetail(categoryId))
        }
    )
}
