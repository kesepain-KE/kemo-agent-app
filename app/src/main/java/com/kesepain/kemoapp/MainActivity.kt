package com.kesepain.kemoapp

import android.Manifest
import android.app.LocaleManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kesepain.kemoapp.data.local.Prefs
import com.kesepain.kemoapp.security.BiometricHelper
import com.kesepain.kemoapp.ui.components.AppBackground
import com.kesepain.kemoapp.ui.navigation.KemoNav
import com.kesepain.kemoapp.ui.screens.connect.ConnectScreen
import com.kesepain.kemoapp.ui.screens.unlock.UnlockScreen
import com.kesepain.kemoapp.ui.theme.KemoTheme
import com.kesepain.kemoapp.ui.theme.KemoTone
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Locale

class MainActivity : FragmentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyLocale(runBlocking { Prefs(this@MainActivity).snapshot().language })
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel()
            val state by viewModel.state.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val dark = when (state.preferences.themeMode) { "dark" -> true; "light" -> false; else -> systemDark }
            val tone = runCatching { KemoTone.valueOf(state.preferences.tone) }.getOrDefault(KemoTone.Purple)
            val backgroundActive = state.preferences.themeBackgroundUri.isNotBlank()
            var showSplash by rememberSaveable { mutableStateOf(true) }
            DisposableEffect(state.configured, state.unlocked) {
                if (!state.configured || !state.unlocked) {
                    window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                }
                onDispose { }
            }
            LaunchedEffect(Unit) { delay(650); showSplash = false }
            KemoTheme(tone, dark, state.preferences.dynamicColor, backgroundActive) {
                Box(Modifier.fillMaxSize()) {
                    AppBackground(
                        uriValue = state.preferences.themeBackgroundUri,
                        mimeType = state.preferences.themeBackgroundMime,
                        darkTheme = dark,
                        reloadKey = state.themeBackgroundRevision,
                    )
                    when {
                        showSplash -> Box(
                            modifier = Modifier.fillMaxSize().background(if (backgroundActive) Color.Transparent else MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painter = painterResource(R.drawable.kemo_brand_internal),
                                contentDescription = stringResource(R.string.app_name),
                                modifier = Modifier.size(240.dp),
                                contentScale = ContentScale.Fit,
                            )
                        }
                        !state.hasSavedAccounts && !state.directEntry -> {
                            val current = state.preferences.accounts.firstOrNull { it.id == state.preferences.currentAccountId }
                            ConnectScreen(
                                current = current,
                                busy = state.busy,
                                error = state.error,
                                rememberedDeviceToken = state.rememberedDeviceToken,
                                rememberedUserPassword = state.rememberedUserPassword,
                                initiallyRememberCredentials = state.rememberCredentials,
                                onConnect = viewModel::connect,
                                onEnterDirectly = viewModel::enterAppDirectly,
                            )
                        }
                        !state.unlocked -> {
                            val title = stringResource(R.string.unlock_title)
                            val subtitle = stringResource(R.string.unlock_subtitle)
                            UnlockScreen(
                                state.error,
                                onBiometric = if (state.preferences.biometricEnabled) {
                                    { BiometricHelper.authenticate(this@MainActivity, title, subtitle) { if (it) viewModel.unlockWithBiometric() else viewModel.reportBiometricFailed() } }
                                } else null,
                                onPassword = viewModel::unlockWithPassword,
                            )
                        }
                        else -> KemoNav(
                            state, viewModel,
                            initialTask = intent?.data?.host == "task",
                            onLanguageChanged = { language ->
                                viewModel.setLanguage(language)
                                lifecycleScope.launch { delay(150); applyLocale(language); recreate() }
                            },
                        )
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun applyLocale(language: String) {
        val tag = if (language == "zh" || language == "en") language else ""
        if (Build.VERSION.SDK_INT >= 33) {
            getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
        } else if (tag.isNotEmpty()) {
            val locale = Locale.forLanguageTag(tag)
            Locale.setDefault(locale)
            val config = Configuration(resources.configuration).apply { setLocale(locale) }
            resources.updateConfiguration(config, resources.displayMetrics)
        }
    }
}
