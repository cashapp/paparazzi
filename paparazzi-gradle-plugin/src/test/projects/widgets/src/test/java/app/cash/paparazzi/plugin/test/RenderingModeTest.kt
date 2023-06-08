package app.cash.paparazzi.plugin.test

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.viewinterop.AndroidView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams.RenderingMode
import com.android.ide.common.rendering.api.SessionParams.RenderingMode.NORMAL
import com.android.ide.common.rendering.api.SessionParams.RenderingMode.SHRINK
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(TestParameterInjector::class)
class RenderingModeTest(
  @TestParameter val mode: Mode
) {
  @get:Rule
  val paparazzi = Paparazzi(
    deviceConfig = DeviceConfig.PIXEL_3,
    renderingMode = mode.renderingMode,
    showSystemUi = mode.showSystemUi
  )

  @Test fun default() {
    paparazzi.snapshot {
      Box {
        AndroidView(
          factory = { buildView(paparazzi.context) }
        )
      }
    }
  }

  enum class Mode(
    val renderingMode: RenderingMode,
    val showSystemUi: Boolean
  ) {
    WIDGET(renderingMode = SHRINK, showSystemUi = false),
    FULL_SCREEN(renderingMode = NORMAL, showSystemUi = true)
  }

  private fun buildView(context: Context): View {
    return LinearLayout(context).apply {
      orientation = LinearLayout.VERTICAL
      layoutParams = ViewGroup.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
      addView(
        TextView(context).apply {
          id = 1
          text = "Text View Sample"
        }
      )

      addView(
        View(context).apply {
          id = 2
          layoutParams = LinearLayout.LayoutParams(100, 100)
          contentDescription = "Content Description Sample"
        }
      )

      addView(
        View(context).apply {
          id = 3
          layoutParams = LinearLayout.LayoutParams(100, 100).apply {
            setMargins(20, 20, 20, 20)
          }
          contentDescription = "Margin Sample"
        }
      )

      addView(
        View(context).apply {
          id = 4
          layoutParams = LinearLayout.LayoutParams(100, 100).apply {
            setMargins(20, 20, 20, 20)
          }
          foreground = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.YELLOW, Color.BLUE)
          ).apply {
            shape = GradientDrawable.OVAL
          }
          contentDescription = "Foreground Drawable"
        }
      )

      addView(
        Button(context).apply {
          id = 5
          layoutParams = LinearLayout.LayoutParams(
            WRAP_CONTENT,
            WRAP_CONTENT
          ).apply {
            gravity = Gravity.CENTER
          }
          text = "Button Sample"
        }
      )
    }
  }
}
