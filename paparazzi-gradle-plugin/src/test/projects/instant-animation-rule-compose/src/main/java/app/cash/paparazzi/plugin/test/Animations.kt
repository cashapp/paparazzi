package app.cash.paparazzi.plugin.test

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Preview
@Composable
fun AnimationsPreview() {
  Animations {
    println(it)
  }
}

@Composable
fun Animations(
  onLog: (String) -> Unit
) {
  var animate by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) {
    animate = true
  }

  val animationSpec = tween<Dp>(10_000, delayMillis = 20_000)
  val anim by animateDpAsState(
    targetValue = if (animate) 100.dp else 50.dp,
    label = "Size",
    animationSpec = animationSpec
  ) {
    onLog("finished")
  }
  onLog("onDraw anim=${anim.value}")

  Column(
    modifier = Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    val infiniteTransition = rememberInfiniteTransition(label = "Infinite")

    val scale by infiniteTransition.animateFloat(
      label = "Scale",
      initialValue = 0f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(animation = tween(10_000))
    )

    val color by infiniteTransition.animateColor(
      label = "Color",
      initialValue = Color.Red,
      targetValue = Color.Blue,
      animationSpec = infiniteRepeatable(
        animation = tween(10_000),
        repeatMode = RepeatMode.Reverse
      )
    )

    AnimatedVisibility(
      visible = animate,
      enter = expandIn(animationSpec = tween(10_000)),
      exit = shrinkOut(animationSpec = tween(10_000))
    ) {
      Box(
        Modifier
          .padding(10.dp)
          .size(200.dp)
          .background(Color.Yellow)
      )
    }

    Box(
      Modifier
        .padding(10.dp)
        .size(anim)
        .background(Color.Green)
    )

    Box(
      Modifier
        .padding(10.dp)
        .size(50.dp)
        .scale(scale)
        .background(color)
    )

    AnimatedContent(
      modifier = Modifier
        .padding(10.dp),
      targetState = animate,
      label = "AnimatedContent"
    ) {
      if (it) {
        Box(Modifier.size(120.dp).background(Color.LightGray))
      } else {
        Box(Modifier.size(40.dp).background(Color.DarkGray))
      }
    }

    Box(
      Modifier
        .background(Color.Magenta)
        .animateContentSize(animationSpec = tween(5_000))
        .size(if (scale > 0.25f) 150.dp else 40.dp)
    )
  }
}
