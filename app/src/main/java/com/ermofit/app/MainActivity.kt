package com.ermofit.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ermofit.app.navigation.ErmoFitNavHost
import com.ermofit.app.ui.i18n.ProvideAppStrings
import com.ermofit.app.ui.main.AppSettingsViewModel
import com.ermofit.app.ui.theme.ErmoFitTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ErmoFitApp()
        }
    }
}

@Composable
private fun ErmoFitApp() {
    val appSettingsViewModel: AppSettingsViewModel = hiltViewModel()
    val appSettings = appSettingsViewModel.uiState.collectAsStateWithLifecycle().value

    ProvideAppStrings(language = appSettings.language) {
        ErmoFitTheme(themeMode = appSettings.themeMode) {
            ErmoFitNavHost()
        }
    }
}
