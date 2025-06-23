package app.cash.paparazzi

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.internal.OffByTwo
import org.junit.Rule
import org.junit.Test

class RenderExtensionTest {
  @get:Rule
  val paparazzi = Paparazzi(
    deviceConfig = DeviceConfig.NEXUS_5,
    snapshotHandler = SnapshotVerifier(maxPercentDifference = 0.01, differ = OffByTwo),
    renderExtensions = setOf(
      WrappedRenderExtension(Color.DKGRAY),
      WrappedRenderExtension(Color.RED),
      WrappedRenderExtension(Color.GREEN),
      WrappedRenderExtension(Color.BLUE)
    )
  )

  @Test
  fun `call multiple snapshots on view`() {
    val view = buildView(paparazzi.context)
    paparazzi.snapshot(view, name = "wrapped")
    paparazzi.snapshot(view, name = "wrapped")
  }

  private fun buildView(
    context: Context,
    rootLayoutParams: ViewGroup.LayoutParams? = ViewGroup.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT
    )
  ) = LinearLayout(context).apply {
    orientation = LinearLayout.VERTICAL
    rootLayoutParams?.let { layoutParams = it }
    addView(
      TextView(context).apply {
        id = 1
        text = "Text View Sample"
      }
    )

    addView(
      Button(context).apply {
        id = 5
        layoutParams = LinearLayout.LayoutParams(
          ViewGroup.LayoutParams.WRAP_CONTENT,
          ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
          gravity = Gravity.CENTER
        }
        text = "Button Sample"
      }
    )
  }
}

class WrappedRenderExtension(val color: Int) : RenderExtension {
  override fun renderView(contentView: View): View =
    FrameLayout(contentView.context).apply {
      layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
      setPadding(20, 20, 20, 20)

      setBackgroundColor(color)
      addView(contentView)
    }
}
