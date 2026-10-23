package app.cash.paparazzi.plugin.test

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview
@Composable
fun SimpleAnimation() {
  Box(Modifier.fillMaxSize()) {
    var visible by remember {
      mutableStateOf(false)
    }

    LaunchedEffect(Unit) {
      visible = true
    }

    val infiniteTransition = rememberInfiniteTransition(label = "infinite-transition")
    val boxColor by infiniteTransition.animateColor(
      initialValue = Color.Red,
      targetValue = Color.Blue,
      animationSpec = infiniteRepeatable(tween(200, 300), RepeatMode.Reverse),
      label = "color"
    )

    AnimatedVisibility(
      visible = visible,
      enter = fadeIn(tween(delayMillis = 200)),
      exit = fadeOut(tween(delayMillis = 100))
    ) {
      Box(
        Modifier
          .size(100.dp)
          .background(boxColor)
      )
    }

    val scale by
      infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(200, 100), RepeatMode.Reverse),
        label = "scale"
      )

    Text(
      modifier = Modifier.scale(scale),
      text = "Hello, Paparazzi"
    )
  }
}
