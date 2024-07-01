package app.cash.paparazzi.sample

import android.graphics.Color.BLUE
import android.graphics.Color.YELLOW
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.GradientDrawable.OVAL
import android.graphics.drawable.GradientDrawable.Orientation.TL_BR
import android.view.View
import android.widget.LinearLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import org.junit.Rule
import org.junit.Test

class RenderingIssuesTest {
  @get:Rule
  val paparazzi = Paparazzi(
    renderingMode = SessionParams.RenderingMode.SHRINK
  )

  @Test
  fun example() {
    paparazzi.snapshot {
      Box(
        modifier = Modifier.background(Color(0xFF000033))
      ) {
        Text("ExampleText", color = Color.White)
      }
    }
  }

  @Test
  fun simpleBoxAlpha() {
    paparazzi.snapshot { SimpleBoxAlphaRepro() }
  }

  @Test
  fun simpleBoxAlpha2() {
    paparazzi.snapshot { SimpleBoxAlphaRepro2() }
  }

  @Test
  fun gradient() {
    paparazzi.snapshot(
      View(paparazzi.context).apply {
        layoutParams = LinearLayout.LayoutParams(100, 100).apply {
          setMargins(20, 20, 20, 20)
        }
        foreground = GradientDrawable(TL_BR, intArrayOf(YELLOW, BLUE)).apply {
          shape = OVAL
        }
        contentDescription = "Foreground Drawable"
      }
    )
  }
}
