package app.cash.paparazzi.plugin.test

import android.content.Context
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.material.Text
import androidx.compose.ui.platform.ComposeView

class MixedView(context: Context) : LinearLayout(context) {
  init {
    orientation = LinearLayout.VERTICAL

    addView(
      TextView(context).apply {
        id = 1
        text = "Legacy Text View"
      }
    )

    addView(
      ComposeView(context).apply {
        id = 2
        setContent {
          Text("Compose Basic Text")
        }
      }
    )
  }
}
