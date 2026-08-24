package dev.raiseexception.odin.accounts.presentation.login

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow

@Composable
fun LoginScreen(
    uiState: LoginUiState,
    onLogin: (String) -> Unit,
    navigationEvent: Flow<NavigationTarget>,
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    ScreenshotProtection()
    LaunchedEffect(Unit) {
        navigationEvent.collect { onLoginSuccess() }
    }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Iniciar sesión",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(24.dp))
        PasswordField(
            value = password,
            onValueChange = { password = it },
            passwordVisible = passwordVisible,
            onToggleVisibility = { passwordVisible = !passwordVisible },
            errorMessage = extractPasswordError(uiState)
        )
        Spacer(modifier = Modifier.height(24.dp))
        LoginAction(
            uiState = uiState,
            onLogin = { onLogin(password) }
        )
        GeneralMessage(uiState)
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    passwordVisible: Boolean,
    onToggleVisibility: () -> Unit,
    errorMessage: String?
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("Contraseña") },
            visualTransformation = if (passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            isError = errorMessage != null,
            trailingIcon = { RevealToggle(passwordVisible = passwordVisible, onToggle = onToggleVisibility) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("password_field")
        )
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("password_field_error")
            )
        }
    }
}

@Composable
private fun RevealToggle(passwordVisible: Boolean, onToggle: () -> Unit) {
    val label = if (passwordVisible) "Ocultar" else "Mostrar"
    val description = if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña"
    TextButton(
        onClick = onToggle,
        modifier = Modifier
            .testTag("reveal_toggle")
            .semantics { contentDescription = description }
    ) {
        Text(label)
    }
}

@Composable
private fun LoginAction(uiState: LoginUiState, onLogin: () -> Unit) {
    val isLoading = uiState is LoginUiState.Loading
    if (isLoading) {
        CircularProgressIndicator(modifier = Modifier.testTag("loading_indicator"))
    }
    Button(
        onClick = onLogin,
        enabled = !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("login_button")
    ) {
        Text("Ingresar")
    }
}

@Composable
private fun GeneralMessage(uiState: LoginUiState) {
    when (uiState) {
        is LoginUiState.Error -> {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = uiState.message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("error_message")
            )
        }
        else -> Unit
    }
}

@Composable
private fun ScreenshotProtection() {
    val context = LocalContext.current
    DisposableEffect(context) {
        val window = context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun extractPasswordError(uiState: LoginUiState): String? = when (uiState) {
    is LoginUiState.ValidationError -> uiState.passwordError
    else -> null
}
