package dev.raiseexception.odin.accounts.presentation.registration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow

@Composable
fun RegistrationScreen(
    uiState: RegistrationUiState,
    onRegister: (String, String) -> Unit,
    navigationEvent: Flow<NavigationTarget>,
    onRegistrationSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) {
        navigationEvent.collect { onRegistrationSuccess() }
    }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordConfirmation by rememberSaveable { mutableStateOf("") }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Crear usuario",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Elija una contraseña larga y única. Esta contraseña protege toda su información financiera.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("recommendation_message")
        )
        Spacer(modifier = Modifier.height(24.dp))
        PasswordField(
            value = password,
            onValueChange = { password = it },
            label = "Contraseña",
            testTag = "password_field",
            errorMessage = extractPasswordError(uiState)
        )
        Spacer(modifier = Modifier.height(16.dp))
        PasswordField(
            value = passwordConfirmation,
            onValueChange = { passwordConfirmation = it },
            label = "Confirmar contraseña",
            testTag = "password_confirmation_field",
            errorMessage = extractPasswordConfirmationError(uiState)
        )
        Spacer(modifier = Modifier.height(24.dp))
        RegistrationAction(
            uiState = uiState,
            onRegister = { onRegister(password, passwordConfirmation) }
        )
        GeneralMessage(uiState)
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    testTag: String,
    errorMessage: String?
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            isError = errorMessage != null,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
        )
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("${testTag}_error")
            )
        }
    }
}

@Composable
private fun RegistrationAction(
    uiState: RegistrationUiState,
    onRegister: () -> Unit
) {
    when (uiState) {
        is RegistrationUiState.Loading -> CircularProgressIndicator(
            modifier = Modifier.testTag("loading_indicator")
        )
        else -> Button(
            onClick = onRegister,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("register_button")
        ) {
            Text("Registrarse")
        }
    }
}

@Composable
private fun GeneralMessage(uiState: RegistrationUiState) {
    when (uiState) {
        is RegistrationUiState.Error -> {
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

private fun extractPasswordError(uiState: RegistrationUiState): String? = when (uiState) {
    is RegistrationUiState.ValidationError -> uiState.passwordError
    else -> null
}

private fun extractPasswordConfirmationError(uiState: RegistrationUiState): String? = when (uiState) {
    is RegistrationUiState.ValidationError -> uiState.passwordConfirmationError
    else -> null
}
