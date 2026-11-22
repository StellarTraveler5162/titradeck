package com.titradeck.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.titradeck.app.R
import kotlinx.coroutines.delay

@Composable
internal fun StartupAnimation(showOnLaunch: Boolean, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(showOnLaunch) }
    LaunchedEffect(Unit) {
        if (visible) {
            delay(420)
            visible = false
        }
    }
    Box(Modifier.fillMaxSize()) {
        if (!visible) content()
        AnimatedVisibility(visible, exit = fadeOut(tween(180))) {
            Box(Modifier.fillMaxSize().background(colorResource(R.color.launch_surface)), contentAlignment = Alignment.Center) {
                Column(Modifier.offset(y = 18.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    AppBrandIcon(modifier = Modifier.testTag("startup_icon"), size = 64.dp)
                    Text(
                        "TITRADECK",
                        color = colorResource(R.color.launch_wordmark),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.sp,
                    )
                }
            }
        }
    }
}
