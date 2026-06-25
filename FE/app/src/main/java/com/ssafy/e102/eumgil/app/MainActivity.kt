package com.ssafy.e102.eumgil.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.e102.eumgil.app.navigation.AppNavHost
import com.ssafy.e102.eumgil.core.designsystem.theme.BusanEumgilTheme
import com.ssafy.e102.eumgil.core.model.TextSizePreference

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        volumeControlStream = defaultAppVolumeControlStream()
        enableEdgeToEdge()

        setContent {
            val appContainer = remember { (application as BusanEumgilApp).appContainer }
            val textSizePreference by
                remember(appContainer) {
                    appContainer.textSizePreferenceRepository.observeTextSizePreference()
                }.collectAsStateWithLifecycle(initialValue = TextSizePreference.DEFAULT)

            BusanEumgilTheme(textSizePreference = textSizePreference) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppNavHost()
                }
            }
        }
    }
}
