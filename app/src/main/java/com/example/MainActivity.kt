package com.example

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.ui.AppScreen
import com.example.ui.MainViewModel
import com.example.ui.SetupStep
import com.example.ui.screens.DocArticleDetailScreen
import com.example.ui.screens.DocHomeScreen
import com.example.ui.screens.DocSearchResultsScreen
import com.example.ui.screens.DocSettingsScreen
import com.example.ui.screens.FirstRunSetupScreen
import com.example.ui.screens.PrivateAuthPinScreen
import com.example.ui.screens.PrivateGalleryScreen
import com.example.ui.screens.PrivatePhotoViewerScreen
import com.example.ui.screens.PrivateSecuritySettingsScreen
import com.example.ui.theme.OpenKnowledgeTheme

class MainActivity : FragmentActivity() {

    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        viewModel = ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MainViewModel(applicationContext) as T
                }
            }
        )[MainViewModel::class.java]

        setContent {
            OpenKnowledgeTheme {
                MainContent(activity = this, viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onAppResume(this)
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            viewModel.onAppBackgrounded()
        }
    }
}

@Composable
fun MainContent(
    activity: FragmentActivity,
    viewModel: MainViewModel
) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val photos by viewModel.photos.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    // System Back button handling
    BackHandler {
        val handled = viewModel.navigateBack()
        if (!handled) {
            activity.finish()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val screen = currentScreen) {
                is AppScreen.DocHome -> {
                    DocHomeScreen(
                        onSearchSubmit = { query ->
                            viewModel.onSearchSubmitted(activity, query)
                        },
                        onArticleClick = { articleId ->
                            viewModel.navigateTo(AppScreen.DocArticleDetail(articleId))
                        },
                        onSettingsClick = {
                            viewModel.navigateTo(AppScreen.DocSettings)
                        }
                    )
                }

                is AppScreen.DocSearchResults -> {
                    DocSearchResultsScreen(
                        query = screen.query,
                        onBackClick = { viewModel.navigateBack() },
                        onArticleClick = { articleId ->
                            viewModel.navigateTo(AppScreen.DocArticleDetail(articleId))
                        }
                    )
                }

                is AppScreen.DocArticleDetail -> {
                    DocArticleDetailScreen(
                        articleId = screen.articleId,
                        onBackClick = { viewModel.navigateBack() }
                    )
                }

                is AppScreen.DocSettings -> {
                    DocSettingsScreen(
                        isSetupCompleted = viewModel.preferences.isSetupCompleted,
                        onBackClick = { viewModel.navigateBack() },
                        onStartSetupClick = {
                            viewModel.startFirstRunSetup()
                        }
                    )
                }

                is AppScreen.FirstRunSetup -> {
                    FirstRunSetupScreen(
                        currentStep = screen.step,
                        activity = activity,
                        onBack = { viewModel.navigateBack() },
                        onStartPhraseSetup = {
                            viewModel.navigateTo(AppScreen.FirstRunSetup(SetupStep.TRIGGER_PHRASE))
                        },
                        onProceedFromPhrase = { phrase ->
                            viewModel.proceedToBiometricSetup(activity, phrase)
                        },
                        onProceedFromBiometrics = {
                            viewModel.proceedToPinSetup()
                        },
                        onFinalizeSetup = { pin ->
                            viewModel.finalizeSetupWithBiometrics(activity, pin)
                        }
                    )
                }

                is AppScreen.PrivateAuthPin -> {
                    PrivateAuthPinScreen(
                        onSubmitPin = { pin ->
                            viewModel.submitPin(activity, pin)
                        },
                        onCancel = {
                            viewModel.lockVault(activity)
                        },
                        errorMessage = statusMessage
                    )
                }

                is AppScreen.PrivateGallery -> {
                    PrivateGalleryScreen(
                        photos = photos,
                        isImporting = isImporting,
                        onPickerLaunched = { 
                            viewModel.isPickerActive = true
                            viewModel.skipNextAutoLock = true
                        },
                        onPickerClosed = { 
                            viewModel.isPickerActive = false
                            viewModel.skipNextAutoLock = false
                        },
                        onImportUris = { uris ->
                            viewModel.importPhotos(uris)
                        },
                        onImportDeviceItems = { items ->
                            viewModel.importDeviceMediaItems(items)
                        },
                        onImportBitmap = { bitmap ->
                            viewModel.importCapturedBitmap(bitmap)
                        },
                        onImportSamplePhotos = { count ->
                            viewModel.importSamplePhotos(count)
                        },
                        onImportSelectedSampleIndices = { indices ->
                            viewModel.importSelectedSampleIndices(indices)
                        },
                        onShowMessage = { msg ->
                            viewModel.showStatusMessage(msg)
                        },
                        onPhotoClick = { photoId ->
                            viewModel.navigateTo(AppScreen.PrivateViewer(photoId))
                        },
                        onLockClick = {
                            viewModel.lockVault(activity)
                        },
                        onSettingsClick = {
                            viewModel.navigateTo(AppScreen.PrivateSecuritySettings)
                        },
                        loadThumbnail = { entity ->
                            viewModel.loadThumbnail(entity)
                        },
                        photoImporter = viewModel.photoImporter
                    )
                }

                is AppScreen.PrivateViewer -> {
                    PrivatePhotoViewerScreen(
                        currentPhotoId = screen.photoId,
                        allPhotos = photos,
                        onBack = { viewModel.navigateBack() },
                        onDeletePhoto = { photoId ->
                            viewModel.deletePhoto(photoId)
                        },
                        onToggleFavorite = { photoId, currentFav ->
                            viewModel.toggleFavorite(photoId, currentFav)
                        },
                        loadFullPhoto = { entity ->
                            viewModel.loadFullPhoto(entity)
                        },
                        loadMetadata = { entity ->
                            viewModel.loadMetadata(entity)
                        }
                    )
                }

                is AppScreen.PrivateSecuritySettings -> {
                    PrivateSecuritySettingsScreen(
                        photoRepository = viewModel.photoRepository,
                        currentAutoLockSeconds = viewModel.preferences.autoLockSeconds,
                        onSetAutoLock = { seconds ->
                            viewModel.preferences.autoLockSeconds = seconds
                        },
                        onSafeReset = {
                            viewModel.performSafeReset(activity)
                        },
                        onBack = { viewModel.navigateBack() }
                    )
                }
            }
        }
    }
}
