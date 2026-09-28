package com.example.homelab2.view

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.homelab2.BuildConfig
import com.example.homelab2.ui.CommentUiState
import com.example.homelab2.ui.theme.HomeLab2Theme
import com.example.homelab2.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HomeLab2Theme {
                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(Unit) {
                    viewModel.snackbarEvent.collect { message ->
                        snackbarHostState.showSnackbar(message)
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
                ) { innerPadding ->
                    DataScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
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
    val secondsRemaining by viewModel.secondsRemaining.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.startPolling()
    }
    val backgroundColor = when (textState) {
        is CommentUiState.Loading -> Color(0x606052EE)
        is CommentUiState.Error -> Color(0xFFC66828)
        is CommentUiState.Safe -> Color(0xFF2E7D32)      // Green
        is CommentUiState.Suspicious -> Color(0xFFC62828) // Red
    }

    val displayText = when (val state = textState) {
        is CommentUiState.Loading -> "Loading GitHub comments..."
        is CommentUiState.Error -> state.text
        is CommentUiState.Safe -> state.text
        is CommentUiState.Suspicious -> "ALERT (Suspicious): ${state.text}"
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = displayText,
                color = Color.White,
                textAlign = TextAlign.Center, // Centers multiline text alignment
                modifier = Modifier.padding(20.dp)
            )

            Text(
                text = "Next refresh in ${secondsRemaining}s",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            val prNumber = when (val state = textState) {
                is CommentUiState.Safe -> state.prNumber
                is CommentUiState.Suspicious -> state.prNumber
                else -> null
            }

            // show when not loading and prNumber is present
            if (textState !is CommentUiState.Loading && textState !is CommentUiState.Error && prNumber != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Row {
                    Button(
                        onClick = {
                            viewModel.onForceMergeClicked(prNumber)
                        }
                    ) {
                        Text(
                            "Merge",
                            fontSize = 32.sp,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Button(
                        onClick = {
                            viewModel.onForceRejectClicked(prNumber)
                        }
                    ) {
                        Text(
                            "Reject",
                            fontSize = 32.sp
                        )
                    }
                }
            }
        }
    }
}