package app.cash.paparazzi.plugin.test

import android.content.Context
import android.graphics.Canvas
import android.graphics.Insets
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowInsetsAnimation
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test
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
    val view = ComposeView(paparazzi.context).apply {
      layoutParams = LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
      setContent {
        SyntheticWindowInsets {
          AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { InsetAwareView(it) }
          )
        }
      }
    }
    paparazzi.snapshot(view, offsetMillis = 16L)
  }

  @Composable
  private fun SyntheticWindowInsets(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val statusBarHeightPx = with(density) { 62.dp.roundToPx() }
    val navigationBarHeightPx = with(density) { 24.dp.roundToPx() }
    val keyboardHeightPx = with(density) { 225.dp.roundToPx() }

    val insets = ViewWindowInsets.Builder()
      .setInsets(ViewWindowInsets.Type.statusBars(), Insets.of(0, statusBarHeightPx, 0, 0))
      .setInsetsIgnoringVisibility(ViewWindowInsets.Type.statusBars(), Insets.of(0, statusBarHeightPx, 0, 0))
      .setInsets(ViewWindowInsets.Type.navigationBars(), Insets.of(0, 0, 0, navigationBarHeightPx))
      .setInsetsIgnoringVisibility(ViewWindowInsets.Type.navigationBars(), Insets.of(0, 0, 0, navigationBarHeightPx))
      .setInsets(ViewWindowInsets.Type.ime(), Insets.of(0, 0, 0, keyboardHeightPx))
      .setVisible(ViewWindowInsets.Type.ime(), true)
      .build()

    val composeView = LocalView.current
    val rootView = composeView.rootView
    DisposableEffect(composeView, rootView, insets) {
      val listener = View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
        composeView.dispatchSystemUiInsets(insets)
        view.dispatchSystemUiInsets(insets)
      }
      composeView.dispatchSystemUiInsets(insets)
      rootView.dispatchSystemUiInsets(insets)
      composeView.addOnLayoutChangeListener(listener)
      rootView.addOnLayoutChangeListener(listener)
      onDispose {
        composeView.removeOnLayoutChangeListener(listener)
        rootView.removeOnLayoutChangeListener(listener)
      }
    }

    content()
  }

  private fun View.dispatchSystemUiInsets(insets: ViewWindowInsets) {
    dispatchApplyWindowInsets(insets)
    dispatchWindowInsetsAnimationProgress(insets, emptyList<WindowInsetsAnimation>())
  }

  private class InsetAwareView(context: Context) : View(context) {
    private val background = Paint().apply { color = android.graphics.Color.GRAY }
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
      canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), background)

      val lineHeight = 56f
      val firstBaseline = maxOf(
        topInset + 160f,
        height - bottomInset - 170f
      )
      canvas.drawText("This text should", 28f, firstBaseline, text)
      canvas.drawText("respect synthetic", 28f, firstBaseline + lineHeight, text)
      canvas.drawText("window insets.", 28f, firstBaseline + (lineHeight * 2), text)
    }
  }
}
