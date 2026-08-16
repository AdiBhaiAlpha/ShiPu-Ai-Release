package com.example.ui.screen

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shipu.ai.R
import kotlinx.coroutines.delay

/**
 * Calm, refined startup splash screen using the canonical ShiPu AI logo.
 * Features a quiet scale and opacity entrance animation, followed by seamless transition
 * into the main application.
 */
@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit
) {
    Log.d("ShiPuAi_Startup", "ShiPuAI_STARTUP_08: SplashScreen rendering BEGIN")
    val scale = remember { Animatable(0.92f) }
    val alpha = remember { Animatable(0.0f) }

    LaunchedEffect(Unit) {
        // Start smooth entrance animation
        alpha.animateTo(
            targetValue = 1.0f,
            animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
        )
        scale.animateTo(
            targetValue = 1.0f,
            animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
        )
        delay(500) // Brief hold for calm feel
        Log.d("ShiPuAi_Startup", "ShiPuAI_STARTUP_09: SplashScreen finished, transitioning to main UI")
        onSplashFinished()
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("splash_screen_container"),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp)
            ) {
                // Canonical ShiPu AI Logo Asset
                Box(
                    modifier = Modifier
                        .scale(scale.value)
                        .alpha(alpha.value)
                        .size(80.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_shipu_logo),
                        contentDescription = "ShiPu AI Official Logo",
                        modifier = Modifier.size(62.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Brand Title
                Text(
                    text = "ShiPu AI",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.alpha(alpha.value)
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Subtitle Tagline
                Text(
                    text = "Personal Intelligent Assistant",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.alpha(alpha.value)
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Subtle Loading Spinner
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(18.dp)
                        .alpha(alpha.value),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp
                )
            }
        }
    }
}
