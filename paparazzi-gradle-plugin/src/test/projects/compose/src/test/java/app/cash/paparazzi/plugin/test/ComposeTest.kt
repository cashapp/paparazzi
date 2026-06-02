package app.cash.paparazzi.plugin.test

import android.content.Context
import android.graphics.Canvas
import android.graphics.Insets
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams.RenderingMode.SHRINK
import org.junit.Rule
import org.junit.Test
import kotlin.math.roundToInt
import android.view.WindowInsets as ViewWindowInsets

class ComposeTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun compose() {
    paparazzi.snapshot {
      HelloPaparazzi()
    }
  }

  @Test
  fun composeDefaultLayoutParams() {
    paparazzi.snapshot {
      Box(
        modifier = Modifier
          .background(Color.Cyan)
          .fillMaxWidth(),
        contentAlignment = Alignment.Center
      ) {
        Text("Hello")
      }
    }
  }

  @Test
  fun syntheticWindowInsets() {
    val density = paparazzi.context.resources.displayMetrics.density
    fun Int.dpToPx(): Int = (this * density).toInt()

    val insets = ViewWindowInsets.Builder()
      .setInsets(ViewWindowInsets.Type.statusBars(), Insets.of(0, 62.dpToPx(), 0, 0))
      .setInsets(ViewWindowInsets.Type.navigationBars(), Insets.of(0, 0, 0, 24.dpToPx()))
      .setInsets(ViewWindowInsets.Type.ime(), Insets.of(0, 0, 0, 225.dpToPx()))
      .build()

    val view = ComposeView(paparazzi.context).apply {
      setContent {
        AndroidView(
          modifier = Modifier.fillMaxSize(),
          factory = { InsetAwareView(it) }
        )
      }
      addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
        v.dispatchApplyWindowInsets(insets)
      }
    }
    paparazzi.snapshot(view, offsetMillis = 16L)
  }

  @Test
  fun anchoredDraggableAnchorsFromSize() {
    paparazzi.unsafeUpdateConfig(renderingMode = SHRINK)

    paparazzi.snapshot {
      val state = remember { AnchoredDraggableState(initialValue = 1) }

      Box(
        modifier = Modifier
          .size(width = 320.dp, height = 64.dp)
          .background(Color.LightGray)
          .onSizeChanged { size ->
            state.updateAnchors(
              DraggableAnchors {
                0 at 0f
                1 at size.width / 2f
              }
            )
          }
          .anchoredDraggable(
            state = state,
            orientation = Orientation.Horizontal,
            enabled = false
          )
      ) {
        Box(
          modifier = Modifier
            .offset {
              IntOffset(
                x = if (state.offset.isNaN()) 0 else state.offset.roundToInt(),
                y = 0
              )
            }
            .size(width = 160.dp, height = 64.dp)
            .background(Color(0xFF2C6BED))
        )
        Text("left", Modifier.align(Alignment.CenterStart).padding(start = 56.dp))
        Text("right", Modifier.align(Alignment.CenterEnd).padding(end = 56.dp))
      }
    }
  }

  private class InsetAwareView(context: Context) : View(context) {
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = android.graphics.Color.WHITE
      textSize = 52f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private var topInset = 0
    private var bottomInset = 0

    override fun onApplyWindowInsets(insets: ViewWindowInsets): ViewWindowInsets {
      topInset = insets.getInsets(ViewWindowInsets.Type.statusBars()).top
      bottomInset = maxOf(
        insets.getInsets(ViewWindowInsets.Type.navigationBars()).bottom,
        insets.getInsets(ViewWindowInsets.Type.ime()).bottom
      )
      invalidate()
      return insets
    }

    override fun onDraw(canvas: Canvas) {
      canvas.drawColor(android.graphics.Color.GRAY)
      val baseline = maxOf(topInset + 160f, height - bottomInset - 170f)
      canvas.drawText("This text should", 28f, baseline, text)
      canvas.drawText("respect synthetic", 28f, baseline + 56f, text)
      canvas.drawText("window insets.", 28f, baseline + 112f, text)
    }
  }
}
