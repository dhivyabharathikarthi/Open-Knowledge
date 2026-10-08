package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.security.KeystoreBiometricManager
import com.example.security.PinKeyDerivation
import com.example.security.SecretTriggerManager
import com.example.ui.SetupStep

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirstRunSetupScreen(
    currentStep: SetupStep,
    activity: FragmentActivity,
    onBack: () -> Unit,
    onStartPhraseSetup: () -> Unit,
    onProceedFromPhrase: (phrase: String) -> Boolean,
    onProceedFromBiometrics: () -> Unit,
    onFinalizeSetup: (pin: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Security Setup",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("setup_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (currentStep) {
                SetupStep.WELCOME -> {
                    SetupWelcomeStep(onStart = onStartPhraseSetup)
                }
                SetupStep.TRIGGER_PHRASE -> {
                    SetupTriggerPhraseStep(
                        activity = activity,
                        onSubmitPhrase = onProceedFromPhrase
                    )
                }
                SetupStep.BIOMETRIC_CHECK -> {
                    SetupBiometricCheckStep(
                        activity = activity,
                        onProceed = onProceedFromBiometrics
                    )
                }
                SetupStep.PIN_CREATE -> {
                    SetupPinStep(
                        onFinalize = onFinalizeSetup
                    )
                }
                SetupStep.FINALIZING -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Initializing cryptographic envelope...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SetupWelcomeStep(onStart: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Welcome to Open Knowledge Setup",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Configure your private local authentication trigger and two-factor cryptographic protection.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Security Architecture:\n" +
                        "1. Secret Trigger: An unlisted search phrase triggers the authentication flow.\n" +
                        "2. Biometric Verification: Hardware-backed Android Keystore key envelope.\n" +
                        "3. Dedicated PIN: Memory-hard Argon2id key derivation.\n" +
                        "4. Authenticated Encryption: AES-256-GCM envelope encryption.",
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 22.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("setup_welcome_continue_button"),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Begin Configuration")
            }
        }
    }
}

@Composable
fun SetupTriggerPhraseStep(
    activity: FragmentActivity,
    onSubmitPhrase: (String) -> Boolean
) {
    val hasBiometrics = remember {
        KeystoreBiometricManager.canAuthenticate(activity) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
    }
    var phrase by remember { mutableStateOf("") }
    var confirmPhrase by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Choose your private search phrase",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Choose a phrase you will remember. This phrase is used to open the private photo authentication flow.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = phrase,
                onValueChange = {
                    phrase = it
                    errorMsg = null
                },
                label = { Text("Secret phrase") },
                placeholder = { Text("e.g. quantum notes, linux, kernel") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("setup_secret_phrase_input"),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrect = false
                ),
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Quick suggestion presets (especially helpful in web emulator)
            Text(
                text = "Quick Presets (Tap to use):",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("quantum notes", "kernel debug", "open doc").forEach { preset ->
                    SuggestionChip(
                        onClick = {
                            phrase = preset
                            confirmPhrase = preset
                            errorMsg = null
                        },
                        label = { Text(preset, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = confirmPhrase,
                onValueChange = {
                    confirmPhrase = it
                    errorMsg = null
                },
                label = { Text("Confirm secret phrase") },
                placeholder = { Text("Re-enter exact phrase") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("setup_confirm_phrase_input"),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrect = false
                ),
                shape = RoundedCornerShape(8.dp)
            )

            if (phrase.isNotBlank() && confirmPhrase != phrase) {
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(
                    onClick = {
                        confirmPhrase = phrase
                        errorMsg = null
                    },
                    modifier = Modifier.align(Alignment.End),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Copy from above", style = MaterialTheme.typography.labelSmall)
                }
            }

            if (errorMsg != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMsg!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "• Any letters, numbers, spaces, or symbols supported\n" +
                        "• Minimum 3 characters (not case-sensitive)\n" +
                        "• Never stored in plaintext; protected with local HMAC verifier",
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = {
                    val normPhrase = com.example.util.Normalization.normalizePhrase(phrase)
                    val normConfirm = com.example.util.Normalization.normalizePhrase(confirmPhrase)

                    if (normPhrase.isEmpty()) {
                        errorMsg = "Please enter a secret search phrase."
                        return@Button
                    }

                    if (normPhrase != normConfirm) {
                        errorMsg = "Phrases do not match. Check spelling or use 'Copy from above'."
                        return@Button
                    }

                    val valid = SecretTriggerManager.validatePhrase(phrase)
                    if (valid.isFailure) {
                        errorMsg = valid.exceptionOrNull()?.message ?: "Invalid phrase."
                        return@Button
                    }

                    val success = onSubmitPhrase(phrase)
                    if (!success) {
                        errorMsg = "Phrase validation error."
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("setup_phrase_continue_button"),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (hasBiometrics) "Continue to Biometrics" else "Continue to PIN Setup")
            }
        }
    }
}

@Composable
fun SetupBiometricCheckStep(
    activity: FragmentActivity,
    onProceed: () -> Unit
) {
    val biometricStatus = remember {
        KeystoreBiometricManager.canAuthenticate(activity)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Icon(
                imageVector = Icons.Default.Fingerprint,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Biometric Verification Requirement",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "The application requires Android's system BiometricPrompt with strong biometric authentication (BIOMETRIC_STRONG). Device credential and PIN substitutes inside the biometric prompt are strictly prohibited.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            val (statusText, isAvailable) = when (biometricStatus) {
                androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS -> {
                    Pair("Strong biometric sensor is available and enrolled.", true)
                }
                androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                    Pair("No biometric enrolled on device. Please enroll a fingerprint or face in device settings.", false)
                }
                else -> {
                    Pair("Biometric sensor status: $biometricStatus. Biometrics will be requested during key initialization.", true)
                }
            }

            Surface(
                color = if (isAvailable) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isAvailable) Icons.Default.Check else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (isAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isAvailable) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onProceed,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("setup_biometric_continue_button"),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Continue to PIN Configuration")
            }
        }
    }
}

@Composable
fun SetupPinStep(
    onFinalize: (pin: String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var pinVisible by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var focusedField by remember { mutableStateOf(0) } // 0 = pin, 1 = confirmPin

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Create Numeric PIN",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Configure a 6 to 12 digit numeric PIN. The PIN is processed through memory-hard Argon2id key derivation to protect the inner envelope.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = pin,
                onValueChange = {
                    if (it.length <= 12 && (it.isEmpty() || it.all { char -> char.isDigit() })) {
                        pin = it
                        errorMsg = null
                    }
                },
                label = { Text("Numeric PIN (6-12 digits)" + if (focusedField == 0) " (Active)" else "") },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { focusedField = 0 }
                    .testTag("setup_pin_input"),
                singleLine = true,
                visualTransformation = if (pinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                trailingIcon = {
                    IconButton(onClick = { pinVisible = !pinVisible }) {
                        Icon(
                            imageVector = if (pinVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle PIN visibility"
                        )
                    }
                },
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = confirmPin,
                onValueChange = {
                    if (it.length <= 12 && (it.isEmpty() || it.all { char -> char.isDigit() })) {
                        confirmPin = it
                        errorMsg = null
                    }
                },
                label = { Text("Confirm Numeric PIN" + if (focusedField == 1) " (Active)" else "") },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { focusedField = 1 }
                    .testTag("setup_confirm_pin_input"),
                singleLine = true,
                visualTransformation = if (pinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                shape = RoundedCornerShape(8.dp)
            )

            if (errorMsg != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMsg!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // On-screen keypad for emulator input
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val numRows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("C", "0", "⌫")
                )

                for (row in numRows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
                    ) {
                        for (key in row) {
                            val isClear = key == "C"
                            val isBackspace = key == "⌫"

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = when {
                                    isClear -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                    isBackspace -> MaterialTheme.colorScheme.surfaceVariant
                                    else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                },
                                modifier = Modifier
                                    .size(width = 76.dp, height = 48.dp)
                                    .clickable {
                                        errorMsg = null
                                        when {
                                            isClear -> {
                                                if (focusedField == 0) pin = "" else confirmPin = ""
                                            }
                                            isBackspace -> {
                                                if (focusedField == 0) {
                                                    if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                                } else {
                                                    if (confirmPin.isNotEmpty()) confirmPin = confirmPin.dropLast(1)
                                                }
                                            }
                                            else -> {
                                                if (focusedField == 0) {
                                                    if (pin.length < 12) pin += key
                                                } else {
                                                    if (confirmPin.length < 12) confirmPin += key
                                                }
                                            }
                                        }
                                    }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    if (isBackspace) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                                            contentDescription = "Backspace",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        Text(
                                            text = key,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isClear) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "• 6 to 12 numeric digits required\n" +
                        "• Unique 32-byte salt and Argon2id memory-hard derivation\n" +
                        "• Never stored in plaintext or placed into clipboard",
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = {
                    if (pin != confirmPin) {
                        errorMsg = "PINs do not match."
                        return@Button
                    }
                    val valid = PinKeyDerivation.validatePin(pin)
                    if (valid.isFailure) {
                        errorMsg = valid.exceptionOrNull()?.message ?: "Invalid PIN."
                        return@Button
                    }
                    onFinalize(pin)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("setup_pin_finalize_button"),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Complete & Initialize Vault")
            }
        }
    }
}
