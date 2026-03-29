// MainActivity.kt
package com.poc.voicepipeline

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.poc.voicepipeline.ui.MainScreen
import com.poc.voicepipeline.ui.theme.VoicePipelineTheme
import com.poc.voicepipeline.viewmodel.VoiceViewModel

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(
                this,
                "Microphone permission is required for voice input",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request mic permission
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        setContent {
            VoicePipelineTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: VoiceViewModel = viewModel()
                    val uiState by viewModel.uiState.collectAsState()

                    MainScreen(
                        uiState = uiState,
                        onToggleListening = { viewModel.toggleListening() },
                        onSend = { viewModel.sendForRefinement() },
                        onClear = { viewModel.clearAll() },
                        onDismissError = { viewModel.dismissError() }
                    )
                }
            }
        }
    }
}