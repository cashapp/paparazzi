package app.cash.paparazzi.plugin.test

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.MotionEvent.PointerCoords
import android.view.MotionEvent.PointerProperties
import android.view.View
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.delay

class ComposeRippleTest {
  @get:Rule
  val paparazzi = Paparazzi(
    renderingMode = SessionParams.RenderingMode.SHRINK
  )

  @Test
  fun ripple() {
    val view = ComposeView(paparazzi.context).apply {
      layoutParams = LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
    }
    view.setContent {
      MaterialTheme {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .clickable {}
            .padding(12.dp),
          contentAlignment = Alignment.Center
        ) {
          Text("Tap me")
        }
      }

      LaunchedEffect(Unit) {
        // If we dispatch the down immediately then nothing happens.
        delay(1)
        val downTime = view.dispatchDown()
        delay(16)
        view.dispatchUp(downTime)
      }
    }

    paparazzi.gif(view, end = 1000L)
  }
}

private fun View.dispatchDown(position: IntOffset = IntOffset(width / 2, height / 2)): Long {
  val now = SystemClock.uptimeMillis()
  dispatchMotionEvent(
    action = MotionEvent.ACTION_DOWN,
    downTime = now,
    eventTime = now,
    position = position
  )
  return now
}

private fun View.dispatchUp(downTime: Long, position: IntOffset = IntOffset(width / 2, height / 2)) {
  dispatchMotionEvent(
    action = MotionEvent.ACTION_UP,
    downTime = downTime,
    eventTime = SystemClock.uptimeMillis(),
    position = position
  )
}

private fun View.dispatchMotionEvent(action: Int, downTime: Long, eventTime: Long, position: IntOffset) {
  val event = MotionEvent.obtain(
    downTime,
    eventTime,
    action,
    1,
    arrayOf(
      PointerProperties().also {
        it.id = 0
        it.toolType = MotionEvent.TOOL_TYPE_FINGER
      }
    ),
    arrayOf(
      PointerCoords().also {
        it.x = position.x.toFloat()
        it.y = position.y.toFloat()
      }
    ),
    0,
    0,
    1f,
    1f,
    0,
    0,
    InputDevice.SOURCE_TOUCHSCREEN,
    0
  )

  dispatchTouchEvent(event)
  event.recycle()
}
