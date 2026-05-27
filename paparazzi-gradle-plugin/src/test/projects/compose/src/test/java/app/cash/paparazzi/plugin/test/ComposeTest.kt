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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        FakeSystemUi {
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
  private fun FakeSystemUi(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val statusBarHeight = 62.dp
    val navigationBarHeight = 24.dp
    val keyboardHeight = 225.dp
    val statusBarHeightPx = with(density) { statusBarHeight.roundToPx() }
    val navigationBarHeightPx = with(density) { navigationBarHeight.roundToPx() }
    val keyboardHeightPx = with(density) { keyboardHeight.roundToPx() }

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
      val listener = object : View.OnLayoutChangeListener {
        override fun onLayoutChange(
          view: View,
          left: Int,
          top: Int,
          right: Int,
          bottom: Int,
          oldLeft: Int,
          oldTop: Int,
          oldRight: Int,
          oldBottom: Int
        ) {
          composeView.dispatchSystemUiInsets(insets)
          view.dispatchSystemUiInsets(insets)
        }
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

    Box(Modifier.fillMaxSize()) {
      content()
      FakeStatusBar(
        modifier = Modifier
          .align(Alignment.TopCenter)
          .fillMaxWidth()
          .height(statusBarHeight)
      )
      FakeSoftKeyboard(
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
          .height(keyboardHeight),
        navigationBarHeight = navigationBarHeight
      )
      FakeGestureNavigation(
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
          .height(navigationBarHeight)
      )
    }
  }

  @Composable
  private fun FakeStatusBar(modifier: Modifier = Modifier) {
    Row(
      modifier = modifier
        .wrapContentHeight(align = Alignment.Top)
        .padding(horizontal = 16.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(text = "9:00", color = Color.Black)
      Spacer(Modifier.weight(1f))
      Box(Modifier.size(16.dp).background(Color.Black, CircleShape))
      Box(Modifier.size(width = 8.dp, height = 16.dp).background(Color.Black))
    }
  }

  @Composable
  private fun FakeSoftKeyboard(modifier: Modifier = Modifier, navigationBarHeight: Dp) {
    Column(
      modifier = modifier
        .background(Color.DarkGray)
        .padding(bottom = navigationBarHeight)
        .padding(top = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      listOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM").forEach { letters ->
        Row(
          modifier = Modifier.height(44.dp),
          horizontalArrangement = Arrangement.spacedBy(16.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          for (letter in letters) {
            Text(text = letter.toString(), color = Color.White, fontSize = 20.sp)
          }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.Black.copy(alpha = 0.2f)))
      }

      Box(
        Modifier
          .padding(vertical = 8.dp)
          .size(width = 160.dp, height = 24.dp)
          .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(32.dp))
      )
    }
  }

  @Composable
  private fun FakeGestureNavigation(modifier: Modifier = Modifier) {
    Box(modifier, Alignment.Center) {
      Box(
        Modifier
          .size(width = 100.dp, height = 4.dp)
          .background(Color.White, RoundedCornerShape(4.dp))
      )
    }
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
      canvas.drawText("This text should be", 28f, firstBaseline, text)
      canvas.drawText("positioned above the", 28f, firstBaseline + lineHeight, text)
      canvas.drawText("keyboard.", 28f, firstBaseline + (lineHeight * 2), text)
    }
  }
}
