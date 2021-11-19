package app.cash.paparazzi.accessibility

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.GradientDrawable.OVAL
import android.graphics.drawable.GradientDrawable.Orientation.TL_BR
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.Snapshot
import app.cash.paparazzi.SnapshotHandler
import app.cash.paparazzi.internal.ImageUtils
import org.junit.Rule
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class AccessibilityRenderExtensionTest {
  @get:Rule
  val paparazzi = Paparazzi(
    deviceConfig = DeviceConfig.NEXUS_5.copy(
      // Needed to render accessibility content next to main content.
      screenWidth = DeviceConfig.NEXUS_5.screenWidth * 2,
      softButtons = false
    ),
    snapshotHandler = TestSnapshotVerifier(),
    renderExtensions = setOf(AccessibilityRenderExtension())
  )

  @Test
  fun test() {
    val view = buildView(paparazzi.context)
    paparazzi.snapshot(view)
  }

  private fun buildView(context: Context) =
    LinearLayout(context).apply {
      orientation = LinearLayout.VERTICAL
      layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
      addView(TextView(context).apply {
        text = "Text View Sample"
      })

      addView(View(context).apply {
        layoutParams = LinearLayout.LayoutParams(100, 100)
        contentDescription = "Content Description Sample"
      })

      addView(View(context).apply {
        layoutParams = LinearLayout.LayoutParams(100, 100).apply {
          setMarginsRelative(20, 20, 20, 20)
        }
        contentDescription = "Margin Sample"
      })

      addView(View(context).apply {
        layoutParams = LinearLayout.LayoutParams(100, 100).apply {
          setMarginsRelative(20, 20, 20, 20)
        }
        foreground = GradientDrawable(TL_BR, intArrayOf(Color.YELLOW, Color.BLUE)).apply {
          shape = OVAL
        }
        contentDescription = "Foreground Drawable"
      })

      addView(Button(context).apply {
        layoutParams = LinearLayout.LayoutParams(
          ViewGroup.LayoutParams.WRAP_CONTENT,
          ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
          gravity = Gravity.CENTER
        }
        text = "Button Sample"
      })
    }

  private class TestSnapshotVerifier : SnapshotHandler {
    override fun newFrameHandler(
      snapshot: Snapshot,
      frameCount: Int,
      fps: Int
    ): SnapshotHandler.FrameHandler {
      return object : SnapshotHandler.FrameHandler {
        override fun handle(image: BufferedImage) {
          val expected = File("src/test/resources/accessibility.png")
          ImageUtils.assertImageSimilar(
            relativePath = expected.path,
            image = image,
            goldenImage = ImageIO.read(expected),
            maxPercentDifferent = 0.1,
          )
        }

        override fun close() = Unit
      }
    }

    override fun close() = Unit
  }
}