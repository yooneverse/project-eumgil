package com.test.sherpatest

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.core.content.ContextCompat
import com.test.sherpatest.ui.MainScreen

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            // 권한 거부 시 UI에서 안내
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermission.launch(Manifest.permission.RECORD_AUDIO)
        }

        setContent {
            MaterialTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }
}
