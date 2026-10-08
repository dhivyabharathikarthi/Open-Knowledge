package com.example.ui

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.crypto.CryptoEngine
import com.example.data.database.AppDatabase
import com.example.data.database.EncryptedPhotoEntity
import com.example.data.docs.DocumentationRepository
import com.example.data.preferences.SecurityPreferences
import com.example.data.storage.VaultStorageManager
import com.example.domain.model.DocArticle
import com.example.domain.model.PhotoMetadata
import com.example.lifecycle.AppSecurityLifecycle
import com.example.photo.PhotoImporter
import com.example.photo.PhotoRepository
import com.example.security.KeystoreBiometricManager
import com.example.security.PinKeyDerivation
import com.example.security.SecretTriggerManager
import com.example.security.SecureVaultSession
import com.example.security.VaultKeyManager
import com.example.util.SecureMemory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.crypto.Cipher

sealed class AppScreen {
    object DocHome : AppScreen()
    data class DocSearchResults(val query: String) : AppScreen()
    data class DocArticleDetail(val articleId: String) : AppScreen()
    object DocSettings : AppScreen()

    // First-run setup screens
    data class FirstRunSetup(val step: SetupStep) : AppScreen()

    // Private mode screens
    object PrivateAuthPin : AppScreen()
    object PrivateGallery : AppScreen()
    data class PrivateViewer(val photoId: String) : AppScreen()
    object PrivateSecuritySettings : AppScreen()
}

enum class SetupStep {
    WELCOME,
    TRIGGER_PHRASE,
    BIOMETRIC_CHECK,
    PIN_CREATE,
    FINALIZING
}

class MainViewModel(private val context: Context) : ViewModel() {

    val preferences = SecurityPreferences(context)
    val storageManager = VaultStorageManager(context)
    val database = AppDatabase.getInstance(context)
    val photoRepository = PhotoRepository(storageManager, database.photoDao())
    val photoImporter = PhotoImporter(context, storageManager, database.photoDao())
    val keystoreBiometricManager = KeystoreBiometricManager()

    // Start in FirstRunSetup if setup is not yet completed, otherwise DocHome
    private val _currentScreen = MutableStateFlow<AppScreen>(
        if (!preferences.isSetupCompleted) AppScreen.FirstRunSetup(SetupStep.WELCOME)
        else AppScreen.DocHome
    )
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    // Backstack for navigation
    private val backStack = mutableListOf<AppScreen>()

    // Setup temporary state
    var setupPhrase = ""
    var setupPin = ""
    private var setupVmk: ByteArray? = null
    private var setupPinKek: ByteArray? = null
    private var setupPinSalt: ByteArray? = null

    // Authentication temporary state
    private var pendingDecryptCipher: Cipher? = null
    private var pendingOuterCiphertext: ByteArray? = null

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    // Flag to prevent background lock while system Photo Picker or import is active
    var isPickerActive: Boolean = false
    var skipNextAutoLock: Boolean = false
    var lastBackgroundTimestamp: Long = 0L

    val photos: StateFlow<List<EncryptedPhotoEntity>> = photoRepository.allPhotos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun navigateTo(screen: AppScreen, addToBackstack: Boolean = true) {
        if (addToBackstack) {
            backStack.add(_currentScreen.value)
        }
        _currentScreen.value = screen
    }

    fun navigateBack(): Boolean {
        if (backStack.isNotEmpty()) {
            val prev = backStack.removeAt(backStack.size - 1)
            _currentScreen.value = prev
            return true
        }
        // In PrivateGallery, do not auto-jump back to DocHome on back press without lock
        if (_currentScreen.value is AppScreen.PrivateGallery) {
            // Stay in PrivateGallery or prompt
            return true
        }
        if (_currentScreen.value !is AppScreen.DocHome) {
            _currentScreen.value = AppScreen.DocHome
            return true
        }
        return false
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun startFirstRunSetup() {
        setupPhrase = ""
        setupPin = ""
        setupVmk = null
        setupPinKek = null
        setupPinSalt = null
        backStack.clear()
        _currentScreen.value = AppScreen.FirstRunSetup(SetupStep.WELCOME)
    }

    // ----------------------------------------------------
    // Search & Trigger Handling
    // ----------------------------------------------------
    fun onSearchSubmitted(activity: FragmentActivity, rawQuery: String) {
        val trimmed = rawQuery.trim()
        if (trimmed.isEmpty()) return

        // Check if query matches secret trigger
        if (preferences.isSetupCompleted) {
            val key = preferences.getTriggerVerifierKey()
            val verifier = preferences.getTriggerVerifierHash()
            if (key != null && verifier != null) {
                val matches = SecretTriggerManager.matchesTrigger(trimmed, key, verifier)
                if (matches) {
                    // Match found: Start Private Two-Factor Authentication!
                    startPrivateAuthFlow(activity)
                    return
                }
            }
        }

        // If not matching trigger: perform normal documentation search
        navigateTo(AppScreen.DocSearchResults(trimmed))
    }

    // ----------------------------------------------------
    // Private Mode Authentication Flow
    // ----------------------------------------------------
    private fun startPrivateAuthFlow(activity: FragmentActivity) {
        val envelopeBytes = preferences.getVmkEnvelope()
        if (envelopeBytes == null) {
            _statusMessage.value = "Authentication configuration error."
            return
        }

        try {
            val (outerNonce, outerCiphertext) = VaultKeyManager.parseOuterEnvelope(envelopeBytes)
            pendingOuterCiphertext = outerCiphertext

            val canBio = KeystoreBiometricManager.canAuthenticate(activity) == BiometricManager.BIOMETRIC_SUCCESS

            if (canBio) {
                val decryptCipher = keystoreBiometricManager.createDecryptCipher(outerNonce)
                val cryptoObject = keystoreBiometricManager.getCryptoObject(decryptCipher)

                val executor = ContextCompat.getMainExecutor(activity)
                val biometricPrompt = BiometricPrompt(
                    activity,
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            super.onAuthenticationSucceeded(result)
                            val authenticatedCipher = result.cryptoObject?.cipher ?: decryptCipher
                            pendingDecryptCipher = authenticatedCipher
                            _currentScreen.value = AppScreen.PrivateAuthPin
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            super.onAuthenticationError(errorCode, errString)
                            try {
                                pendingDecryptCipher = keystoreBiometricManager.createDecryptCipher(outerNonce)
                                _currentScreen.value = AppScreen.PrivateAuthPin
                            } catch (_: Exception) {
                                lockVault(activity)
                                _statusMessage.value = "Authentication cancelled."
                            }
                        }

                        override fun onAuthenticationFailed() {
                            super.onAuthenticationFailed()
                        }
                    }
                )

                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Confirm Identity")
                    .setSubtitle("Biometric authentication required")
                    .setNegativeButtonText("Use PIN")
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                    .build()

                biometricPrompt.authenticate(promptInfo, cryptoObject)
            } else {
                pendingDecryptCipher = keystoreBiometricManager.createDecryptCipher(outerNonce)
                _currentScreen.value = AppScreen.PrivateAuthPin
            }

        } catch (e: Exception) {
            lockVault(activity)
            _statusMessage.value = "Authentication error: ${e.message}"
        }
    }

    /**
     * Completes Step 2 of Authentication using the entered PIN.
     */
    fun submitPin(activity: Activity, pin: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val outerCiphertext = pendingOuterCiphertext
            val pinSalt = preferences.getPinSalt()

            var bioCipher = pendingDecryptCipher
            if (bioCipher == null) {
                val envelopeBytes = preferences.getVmkEnvelope()
                if (envelopeBytes != null) {
                    try {
                        val (outerNonce, _) = VaultKeyManager.parseOuterEnvelope(envelopeBytes)
                        bioCipher = keystoreBiometricManager.createDecryptCipher(outerNonce)
                        pendingDecryptCipher = bioCipher
                    } catch (_: Exception) {}
                }
            }

            if (bioCipher == null || outerCiphertext == null || pinSalt == null) {
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Authentication state invalid. Please enter trigger phrase again."
                }
                return@launch
            }

            try {
                // 1. Derive PIN KEK via Argon2id
                val pinChars = pin.toCharArray()
                val pinKek = PinKeyDerivation.deriveKek(pinChars, pinSalt)
                SecureMemory.zeroize(pinChars)

                // 2. Unwrap VMK through two layers
                val vmk = VaultKeyManager.unwrapVmk(outerCiphertext, bioCipher, pinKek)
                SecureMemory.zeroize(pinKek)

                // 3. Store VMK in memory-only session
                withContext(Dispatchers.Main) {
                    SecureVaultSession.unlock(vmk)
                    AppSecurityLifecycle.setSecureWindow(activity, true)
                    pendingDecryptCipher = null
                    pendingOuterCiphertext = null
                    backStack.clear()
                    _currentScreen.value = AppScreen.PrivateGallery
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Incorrect PIN. Please re-enter your PIN."
                }
            }
        }
    }

    // ----------------------------------------------------
    // First-Run Setup Steps
    // ----------------------------------------------------
    fun proceedToBiometricSetup(activity: FragmentActivity, phrase: String): Boolean {
        val validation = SecretTriggerManager.validatePhrase(phrase)
        if (validation.isFailure) {
            _statusMessage.value = validation.exceptionOrNull()?.message
            return false
        }
        setupPhrase = validation.getOrThrow()

        val canBio = KeystoreBiometricManager.canAuthenticate(activity) == BiometricManager.BIOMETRIC_SUCCESS
        if (canBio) {
            _currentScreen.value = AppScreen.FirstRunSetup(SetupStep.BIOMETRIC_CHECK)
        } else {
            _currentScreen.value = AppScreen.FirstRunSetup(SetupStep.PIN_CREATE)
        }
        return true
    }

    fun proceedToPinSetup() {
        _currentScreen.value = AppScreen.FirstRunSetup(SetupStep.PIN_CREATE)
    }

    fun finalizeSetupWithBiometrics(activity: FragmentActivity, pin: String) {
        val pinValidation = PinKeyDerivation.validatePin(pin)
        if (pinValidation.isFailure) {
            _statusMessage.value = pinValidation.exceptionOrNull()?.message
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val vmk = CryptoEngine.generateRandomKey()
                setupVmk = vmk

                val salt = PinKeyDerivation.generateSalt()
                val pinChars = pin.toCharArray()
                val pinKek = PinKeyDerivation.deriveKek(pinChars, salt)
                SecureMemory.zeroize(pinChars)

                setupPinSalt = salt
                setupPinKek = pinKek

                val canBio = KeystoreBiometricManager.canAuthenticate(activity) == BiometricManager.BIOMETRIC_SUCCESS

                if (canBio) {
                    keystoreBiometricManager.generateBiometricKey(requireUserAuthForThisKey = true)
                    val encryptCipher = keystoreBiometricManager.createEncryptCipher()
                    val cryptoObject = keystoreBiometricManager.getCryptoObject(encryptCipher)

                    withContext(Dispatchers.Main) {
                        val executor = ContextCompat.getMainExecutor(activity)
                        val biometricPrompt = BiometricPrompt(
                            activity,
                            executor,
                            object : BiometricPrompt.AuthenticationCallback() {
                                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                    super.onAuthenticationSucceeded(result)
                                    val authenticatedCipher = result.cryptoObject?.cipher ?: encryptCipher
                                    completeSetupFinalization(authenticatedCipher)
                                }

                                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                    super.onAuthenticationError(errorCode, errString)
                                    try {
                                        keystoreBiometricManager.generateBiometricKey(requireUserAuthForThisKey = false)
                                        val fallbackCipher = keystoreBiometricManager.createEncryptCipher()
                                        completeSetupFinalization(fallbackCipher)
                                    } catch (e: Exception) {
                                        _statusMessage.value = "Setup error: ${e.message}"
                                    }
                                }

                                override fun onAuthenticationFailed() {
                                    super.onAuthenticationFailed()
                                }
                            }
                        )

                        val promptInfo = BiometricPrompt.PromptInfo.Builder()
                            .setTitle("Register Biometrics")
                            .setSubtitle("Confirm biometric factor for secure envelope")
                            .setNegativeButtonText("Skip")
                            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                            .build()

                        biometricPrompt.authenticate(promptInfo, cryptoObject)
                    }
                } else {
                    keystoreBiometricManager.generateBiometricKey(requireUserAuthForThisKey = false)
                    val encryptCipher = keystoreBiometricManager.createEncryptCipher()
                    completeSetupFinalization(encryptCipher)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Setup failed: ${e.message}"
                }
            }
        }
    }

    private fun completeSetupFinalization(authenticatedCipher: Cipher) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val vmk = setupVmk ?: return@launch
                val pinKek = setupPinKek ?: return@launch
                val pinSalt = setupPinSalt ?: return@launch

                val envelope = VaultKeyManager.createEnvelope(vmk, pinKek, authenticatedCipher)
                val verifierKey = SecretTriggerManager.generateVerifierKey()
                val verifierHash = SecretTriggerManager.computeVerifier(setupPhrase, verifierKey)

                preferences.saveTriggerData(verifierKey, verifierHash)
                preferences.savePinSalt(pinSalt)
                preferences.saveVmkEnvelope(envelope)
                preferences.isSetupCompleted = true

                SecureMemory.zeroize(setupVmk)
                SecureMemory.zeroize(setupPinKek)
                setupVmk = null
                setupPinKek = null
                setupPinSalt = null
                setupPhrase = ""
                setupPin = ""

                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Setup completed successfully."
                    backStack.clear()
                    _currentScreen.value = AppScreen.DocHome
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Cryptographic initialization error: ${e.message}"
                }
            }
        }
    }

    fun showStatusMessage(message: String) {
        _statusMessage.value = message
    }

    fun importDeviceMediaItems(items: List<com.example.photo.DeviceMediaItem>) {
        if (items.isEmpty()) {
            isPickerActive = false
            return
        }

        val vmk = SecureVaultSession.getActiveVmk()
        if (vmk == null) {
            isPickerActive = false
            _statusMessage.value = "Vault session expired. Please re-authenticate."
            return
        }

        _isImporting.value = true
        viewModelScope.launch {
            try {
                var importedCount = 0
                for (item in items) {
                    val res = photoImporter.importFromUri(item.uri, vmk)
                    if (res.isSuccess) importedCount++
                }
                _statusMessage.value = if (importedCount > 0) "Encrypted and saved $importedCount device photo(s) into vault." else "Unable to read selected photo(s)."
            } catch (t: Throwable) {
                _statusMessage.value = "Import error: ${t.localizedMessage ?: "Unknown error"}"
            } finally {
                _isImporting.value = false
                isPickerActive = false
                lastBackgroundTimestamp = 0L
            }
        }
    }

    // ----------------------------------------------------
    // Photo Import & Management
    // ----------------------------------------------------
    fun importPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) {
            isPickerActive = false
            return
        }

        val vmk = SecureVaultSession.getActiveVmk()
        if (vmk == null) {
            isPickerActive = false
            _statusMessage.value = "Vault session expired. Please re-authenticate."
            return
        }

        _isImporting.value = true
        viewModelScope.launch {
            try {
                var importedCount = 0
                for (uri in uris) {
                    val res = photoImporter.importFromUri(uri, vmk)
                    if (res.isSuccess) importedCount++
                }
                _statusMessage.value = if (importedCount > 0) "Encrypted and saved $importedCount photo(s) into vault." else "Unable to read selected photo(s)."
            } catch (t: Throwable) {
                _statusMessage.value = "Import error: ${t.localizedMessage ?: "Unknown error"}"
            } finally {
                _isImporting.value = false
                isPickerActive = false
                lastBackgroundTimestamp = 0L
            }
        }
    }

    fun importCapturedBitmap(bitmap: Bitmap) {
        val vmk = SecureVaultSession.getActiveVmk()
        if (vmk == null) {
            _statusMessage.value = "Vault session expired."
            return
        }

        _isImporting.value = true
        viewModelScope.launch {
            try {
                val fileName = "Camera_${System.currentTimeMillis()}.jpg"
                val res = photoImporter.importBitmap(bitmap, fileName, vmk)
                if (res.isSuccess) {
                    _statusMessage.value = "Encrypted camera photo added to vault."
                } else {
                    _statusMessage.value = "Failed to save camera photo."
                }
            } catch (t: Throwable) {
                _statusMessage.value = "Camera save error: ${t.localizedMessage ?: "Error"}"
            } finally {
                _isImporting.value = false
                isPickerActive = false
                lastBackgroundTimestamp = 0L
            }
        }
    }

    fun importSamplePhotos(count: Int = 3) {
        val vmk = SecureVaultSession.getActiveVmk()
        if (vmk == null) {
            _statusMessage.value = "Vault session expired."
            return
        }

        _isImporting.value = true
        viewModelScope.launch {
            try {
                val titles = listOf(
                    "Encrypted Blueprint Matrix",
                    "Sunset Horizon Panorama",
                    "Quantum Security Topology",
                    "Alpine Mountain Vista",
                    "Abstract Cyber Emerald",
                    "Velvet Rose Architecture",
                    "Sapphire Neural Network",
                    "Golden Hour Landscape"
                )
                var countSuccess = 0
                for (i in 0 until count) {
                    val title = titles[i % titles.size]
                    val bitmap = photoImporter.createSamplePhotoBitmap(title, i)
                    val res = photoImporter.importBitmap(bitmap, "$title.jpg", vmk)
                    if (res.isSuccess) countSuccess++
                }
                _statusMessage.value = "Encrypted $countSuccess photo(s) into vault."
            } catch (t: Throwable) {
                _statusMessage.value = "Import error: ${t.localizedMessage ?: "Error"}"
            } finally {
                _isImporting.value = false
                isPickerActive = false
                lastBackgroundTimestamp = 0L
            }
        }
    }

    fun importSelectedSampleIndices(indices: List<Int>) {
        val vmk = SecureVaultSession.getActiveVmk()
        if (vmk == null) {
            _statusMessage.value = "Vault session expired."
            return
        }

        _isImporting.value = true
        viewModelScope.launch {
            try {
                val titles = listOf(
                    "Encrypted Blueprint Matrix",
                    "Sunset Horizon Panorama",
                    "Quantum Security Topology",
                    "Alpine Mountain Vista",
                    "Abstract Cyber Emerald",
                    "Velvet Rose Architecture",
                    "Sapphire Neural Network",
                    "Golden Hour Landscape"
                )
                var countSuccess = 0
                for (idx in indices) {
                    val title = titles[idx % titles.size]
                    val bitmap = photoImporter.createSamplePhotoBitmap(title, idx)
                    val res = photoImporter.importBitmap(bitmap, "$title.jpg", vmk)
                    if (res.isSuccess) countSuccess++
                }
                _statusMessage.value = "Encrypted and added $countSuccess photo(s) to vault."
            } catch (t: Throwable) {
                _statusMessage.value = "Import error: ${t.localizedMessage ?: "Error"}"
            } finally {
                _isImporting.value = false
                isPickerActive = false
                lastBackgroundTimestamp = 0L
            }
        }
    }

    suspend fun loadThumbnail(entity: EncryptedPhotoEntity): Bitmap? {
        val vmk = SecureVaultSession.getActiveVmk() ?: return null
        return photoRepository.loadThumbnailBitmap(entity, vmk)
    }

    suspend fun loadFullPhoto(entity: EncryptedPhotoEntity): Bitmap? {
        val vmk = SecureVaultSession.getActiveVmk() ?: return null
        return photoRepository.loadFullPhotoBitmap(entity, vmk)
    }

    suspend fun loadMetadata(entity: EncryptedPhotoEntity): PhotoMetadata? {
        val vmk = SecureVaultSession.getActiveVmk() ?: return null
        return photoRepository.loadMetadata(entity, vmk)
    }

    fun toggleFavorite(id: String, currentFav: Boolean) {
        viewModelScope.launch {
            try {
                photoRepository.toggleFavorite(id, currentFav)
            } catch (_: Throwable) {}
        }
    }

    fun deletePhoto(id: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                photoRepository.deletePhoto(id)
                onDeleted()
            } catch (_: Throwable) {}
        }
    }

    fun lockVault(activity: Activity) {
        SecureVaultSession.lock()
        AppSecurityLifecycle.setSecureWindow(activity, false)
        pendingDecryptCipher = null
        pendingOuterCiphertext = null
        isPickerActive = false
        lastBackgroundTimestamp = 0L
        backStack.clear()
        _currentScreen.value = AppScreen.DocHome
    }

    fun onAppResume(activity: Activity) {
        // Never lock if picker, permission dialog, or import is active
        if (isPickerActive || skipNextAutoLock || _isImporting.value) {
            skipNextAutoLock = false
            lastBackgroundTimestamp = 0L
            return
        }

        if (SecureVaultSession.isSessionActive() && lastBackgroundTimestamp > 0L) {
            val elapsedMs = System.currentTimeMillis() - lastBackgroundTimestamp
            val autoLockSeconds = preferences.autoLockSeconds

            // Only lock if user configured a non-zero timeout and left the app for that long
            if (autoLockSeconds > 0 && elapsedMs >= (autoLockSeconds * 1000L)) {
                lockVault(activity)
            }
        }
        lastBackgroundTimestamp = 0L
    }

    fun onAppBackgrounded() {
        if (!isPickerActive && !skipNextAutoLock && !_isImporting.value) {
            lastBackgroundTimestamp = System.currentTimeMillis()
        }
    }

    // ----------------------------------------------------
    // Security Settings & Safe Reset
    // ----------------------------------------------------
    fun performSafeReset(activity: Activity) {
        viewModelScope.launch {
            try {
                photoRepository.deleteAllPhotos()
                storageManager.purgeAllVaultFiles()
                database.photoDao().deleteAllPhotos()
                preferences.clearAll()
                keystoreBiometricManager.deleteKey()
                lockVault(activity)
                _statusMessage.value = "All data safely reset."
            } catch (t: Throwable) {
                _statusMessage.value = "Reset error: ${t.localizedMessage}"
            }
        }
    }
}
