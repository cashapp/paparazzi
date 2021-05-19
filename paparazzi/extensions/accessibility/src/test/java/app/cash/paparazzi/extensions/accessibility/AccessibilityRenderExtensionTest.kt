package app.cash.paparazzi.extensions.accessibility

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.widget.Button
import android.widget.CheckedTextView
import android.widget.FrameLayout
import android.widget.FrameLayout.LayoutParams
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class AccessibilityRenderExtensionTest {

  @get:Rule
  val paparazzi = Paparazzi().apply {
    addRenderExtension(AccessibilityRenderExtension())
  }

  @Test
  fun accessibility() {
    val textView = TextView(paparazzi.context).apply {
      text = "Hello"
      layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
      updateViewMarginAndPadding()
    }
    val button = Button(paparazzi.context).apply {
      text = "Click me"
      layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
      updateViewMarginAndPadding()
    }


    val imageView = ImageView(paparazzi.context).apply {
      contentDescription = "Hi"

      val bitmap = loadBitmapFromResources("camera.png")
      setImageBitmap(bitmap)

      layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, 400)
      updateViewMarginAndPadding()
    }

    val frameLayout = FrameLayout(paparazzi.context).apply {
      importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
      layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0).apply {
        weight = 1f
        updateViewMarginAndPadding()
      }
    }

    CheckedTextView(paparazzi.context).run {
      isChecked = true
      text = "Checked"
      frameLayout.addView(this, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
      updateViewMarginAndPadding()
    }
    val viewWithoutContentDesc = View(paparazzi.context).apply {
      layoutParams = LayoutParams(300, 100)
      elevation = 10f
      setBackgroundColor(Color.LTGRAY)
      updateViewMarginAndPadding()
    }

    paparazzi.snapshot(LinearLayout(paparazzi.context).apply {
      orientation = LinearLayout.VERTICAL
      addView(textView)
      addView(imageView)
      addView(frameLayout)
      addView(button)
      addView(viewWithoutContentDesc)
    })
  }

  private fun loadBitmapFromResources(resource: String): Bitmap? {
    val resourceAsStream = javaClass.classLoader!!.getResourceAsStream(resource)
    return BitmapFactory.decodeStream(resourceAsStream)
  }

  private fun View.updateViewMarginAndPadding() {
    setPaddingRelative(10, 10, 10, 10)
    (layoutParams as? MarginLayoutParams)?.setMarginsRelative(10, 10, 10, 10)
  }
}