@file:Suppress("LongMethod")

package dev.raiseexception.odin.accounts.presentation.registration

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.raiseexception.odin.R
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
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var passwordConfirmation by rememberSaveable { mutableStateOf("") }
    var passwordConfirmationVisible by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(modifier = Modifier.height(100.dp))
        Image(
            painter = painterResource(R.drawable.logo),
            contentDescription = "Odin",
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(14.dp)),
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Crear usuario",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Elija una contraseña larga y única. Esta contraseña protege toda su información financiera.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("recommendation_message"),
        )
        Spacer(modifier = Modifier.height(24.dp))
        PasswordField(
            value = password,
            onValueChange = { password = it },
            config = FieldConfig("Contraseña", "password_field"),
            revealState = RevealState(passwordVisible) { passwordVisible = !passwordVisible },
            errorMessage = extractPasswordError(uiState),
        )
        Spacer(modifier = Modifier.height(16.dp))
        PasswordField(
            value = passwordConfirmation,
            onValueChange = { passwordConfirmation = it },
            config = FieldConfig("Confirmar contraseña", "password_confirmation_field"),
            revealState = RevealState(passwordConfirmationVisible) {
                passwordConfirmationVisible = !passwordConfirmationVisible
            },
            errorMessage = extractPasswordConfirmationError(uiState),
        )
        Spacer(modifier = Modifier.height(24.dp))
        RegistrationAction(
            uiState = uiState,
            onRegister = { onRegister(password, passwordConfirmation) },
        )
        GeneralMessage(uiState)
    }
}

private data class RevealState(val visible: Boolean, val onToggle: () -> Unit)
private data class FieldConfig(val label: String, val testTag: String)

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    config: FieldConfig,
    revealState: RevealState,
    errorMessage: String?
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = config.label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            visualTransformation = if (revealState.visible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            isError = errorMessage != null,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                errorBorderColor = MaterialTheme.colorScheme.error,
                errorContainerColor = MaterialTheme.colorScheme.surface,
            ),
            textStyle = MaterialTheme.typography.bodyLarge,
            trailingIcon = {
                RevealToggle(
                    passwordVisible = revealState.visible,
                    onToggle = revealState.onToggle,
                    testTag = "${config.testTag}_reveal_toggle",
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(config.testTag),
        )
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .testTag("${config.testTag}_error"),
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
        is RegistrationUiState.Loading -> Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("loading_indicator"),
            )
        }
        else -> Button(
            onClick = onRegister,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("register_button"),
        ) {
            Text(
                text = "Registrarse",
                style = MaterialTheme.typography.titleMedium,
            )
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
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("error_message"),
            )
        }
        else -> Unit
    }
}

@Composable
private fun RevealToggle(passwordVisible: Boolean, onToggle: () -> Unit, testTag: String) {
    val label = if (passwordVisible) "Ocultar" else "Mostrar"
    val description = if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña"
    TextButton(
        onClick = onToggle,
        modifier = Modifier
            .testTag(testTag)
            .semantics { contentDescription = description },
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.labelLarge,
        )
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
