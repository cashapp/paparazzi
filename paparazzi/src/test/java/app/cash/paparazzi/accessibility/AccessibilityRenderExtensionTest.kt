package app.cash.paparazzi.accessibility

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
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
            maxPercentDifferent = 0.1, // Default percent used in Paparazzi constructor.
          )
        }

        override fun close() = Unit
      }
    }

    override fun close() = Unit
  }
}