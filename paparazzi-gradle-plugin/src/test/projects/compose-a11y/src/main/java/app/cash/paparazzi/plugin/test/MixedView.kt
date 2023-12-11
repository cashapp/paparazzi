package app.cash.paparazzi.plugin.test

import android.content.Context
import android.view.View.GONE
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
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
      TextView(context).apply {
        id = 1
        text = "Hidden Legacy Text View"
        visibility = GONE
      }
    )

    addView(
      Button(context).apply {
        id = 2
        text = "Legacy Button"
      }
    )

    addView(
      ImageView(context).apply {
        id = 3
        contentDescription = "Legacy Image View"
      }
    )

    addView(
      ComposeView(context).apply {
        id = 4
        setContent {
          SimpleComposable()
        }
      }
    )

    addView(
      ComposeView(context).apply {
        id = 5
        setContent {
          CompositeComposable()
        }
      }
    )
  }
}
