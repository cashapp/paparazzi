package app.cash.paparazzi.plugin.test

import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity.CENTER
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.LinearLayout.VERTICAL
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class NinePatchTest {
  @get:Rule
  val paparazzi = Paparazzi(theme = "Theme.App")

  @Test
  fun ninePatch() {
    val launch = LinearLayout(paparazzi.context).apply {
      val outValue = TypedValue()
      context.theme.resolveAttribute(android.R.attr.listDivider, outValue, true)
      dividerDrawable = AppCompatResources.getDrawable(context, outValue.resourceId)

      finishSetup()
    }
    paparazzi.snapshot(launch)
  }

  private fun LinearLayout.finishSetup() {
    showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
    orientation = VERTICAL
    gravity = CENTER
    setBackgroundColor(Color.GRAY)
    addView(
      TextView(context).apply {
        text = "Hello"
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 32f)
      },
      WRAP_CONTENT,
      WRAP_CONTENT
    )
    addView(
      TextView(context).apply {
        text = "Paparazzi"
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 32f)
      },
      WRAP_CONTENT,
      WRAP_CONTENT
    )
  }
}
