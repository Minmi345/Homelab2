package com.example.homelab2.view

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.homelab2.ui.theme.HomeLab2Theme
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.example.homelab2.viewmodel.MainViewModel
import androidx.compose.material3.Button
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import com.example.homelab2.BuildConfig
import com.example.homelab2.ui.CommentUiState

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HomeLab2Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                   // TokenDisplayScreen()
                    DataScreen(viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun TokenDisplayScreen() {
    val token = BuildConfig.GITHUB_TOKEN

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (token.isNotEmpty()) "Token: $token" else "Token Not Found in BuildConfig",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}


@Composable
fun DataScreen(viewModel: MainViewModel,
               modifier: Modifier = Modifier) {
    val textState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.startPolling()
    }
    val backgroundColor = when (textState) {
        is CommentUiState.Loading -> Color(0x606052EE)
        is CommentUiState.Safe -> Color(0xFF2E7D32)      // Green
        is CommentUiState.Suspicious -> Color(0xFFC62828) // Red
    }

    val displayText = when (val state = textState) {
        is CommentUiState.Loading -> "Loading GitHub comments..."
        is CommentUiState.Safe -> state.text
        is CommentUiState.Suspicious -> "ALERT (Suspicious): ${state.text}"
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Text(text = displayText,
            color = Color.White,
            textAlign = TextAlign.Center, // Centers multiline text alignment
            modifier = Modifier.padding(20.dp))
    }
}