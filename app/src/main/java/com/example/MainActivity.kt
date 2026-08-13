package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.screen.AuthScreen
import com.example.ui.screen.MainChatScreen
import com.example.ui.screen.SplashScreen
import com.example.ui.theme.ShiPuAiTheme
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.ChatViewModel

/**
 * Main Activity entry point for ShiPu AI.
 * Handles edge-to-edge layout, Theme dynamic switching, startup SplashScreen animation,
 * and authenticated route switching.
 */
class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()
    private val chatViewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val authUiState by authViewModel.uiState.collectAsStateWithLifecycle()
            val chatUiState by chatViewModel.uiState.collectAsStateWithLifecycle()

            var isSplashVisible by remember { mutableStateOf(true) }

            // Initialize chat user session when authenticated
            LaunchedEffect(authUiState.isAuthenticated, authUiState.userId) {
                if (authUiState.isAuthenticated && authUiState.userId != null) {
                    chatViewModel.initUser(authUiState.userId!!)
                }
            }

            ShiPuAiTheme(darkTheme = chatUiState.isDarkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Crossfade(
                        targetState = isSplashVisible,
                        animationSpec = tween(durationMillis = 400),
                        label = "splash_transition"
                    ) { splashActive ->
                        if (splashActive) {
                            SplashScreen(
                                onSplashFinished = { isSplashVisible = false }
                            )
                        } else {
                            Crossfade(
                                targetState = authUiState.isAuthenticated,
                                animationSpec = tween(durationMillis = 400),
                                label = "auth_chat_transition"
                            ) { isAuthenticated ->
                                if (isAuthenticated) {
                                    MainChatScreen(
                                        chatViewModel = chatViewModel,
                                        chatUiState = chatUiState,
                                        authUiState = authUiState,
                                        onLogout = { authViewModel.logout() },
                                        onDeleteAccount = { authViewModel.deleteAccount() }
                                    )
                                } else {
                                    AuthScreen(
                                        authViewModel = authViewModel,
                                        uiState = authUiState
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
