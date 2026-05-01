package app.cash.paparazzi.plugin.test

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.coroutineScope
import app.cash.paparazzi.Paparazzi
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LifecycleCoroutineScopeTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test fun lifecycleCoroutineDoesNotBlockSubsequentGifs() {
    assertCounterAdvances()
    assertCounterAdvances()
  }

  private fun assertCounterAdvances() {
    var maxCounter = 0

    val view = ComposeView(paparazzi.context)
    view.setContent {
      val counter by produceState(initialValue = 0) {
        while (isActive) {
          delay(100)
          value += 1
        }
      }

      SideEffect {
        if (counter > maxCounter) {
          maxCounter = counter
        }
      }

      val lifecycleOwner = LocalLifecycleOwner.current
      DisposableEffect(Unit) {
        val animatable = Animatable(0f)
        onDispose {
          lifecycleOwner.lifecycle.coroutineScope.launch {
            animatable.animateTo(1f)
          }
        }
      }

      Text(
        modifier = Modifier
          .fillMaxSize()
          .background(Color(0xFF073858))
          .padding(16.dp),
        text = "Counter = $counter",
        color = Color.Yellow
      )
    }

    paparazzi.gif(view, end = 500L, fps = 10)

    assertTrue("Expected counter to advance, maxCounter=$maxCounter", maxCounter >= 1)
  }
}
