package app.cash.paparazzi.sample

import android.content.Context
import android.graphics.Color
import android.icu.text.MessageFormat
import android.text.Html
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes

class ResourcesDemoView(context: Context) : LinearLayout(context) {
  init {
    setBackgroundColor(Color.WHITE)
    orientation = LinearLayout.VERTICAL
    layoutParams = ViewGroup.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT
    )
    addImageView(R.drawable.camera)
    addImageView(R.drawable.ic_android_black_24dp)
    addTextView(resources.getBoolean(R.bool.adjust_view_bounds).toString())
    addTextView(
      text = "Color",
      backgroundColor = resources.getColor(R.color.keypadGreen, null)
    )
    addTextView(resources.getString(R.string.string_escaped_chars))
    addTextView("Height: ${resources.getDimension(R.dimen.textview_height)}")
    addTextView("Max speed: ${context.resources.getInteger(R.integer.max_speed)}")
    addTextView("Plurals:")
    plurals.forEach { (label, quantity) ->
      addView(
        LinearLayout(context).apply {
          orientation = LinearLayout.HORIZONTAL
          addTextView("$label:", width = WRAP_CONTENT, leftMargin = dip(4f))
          addTextView(
            context.resources.getQuantityString(R.plurals.plural_name, quantity),
            width = WRAP_CONTENT,
            weight = 1f,
            leftMargin = dip(4f),
            rightMargin = dip(4f)
          )
        }
      )
    }
    addTextView(resources.getString(R.string.string_name))
    addTextView(MessageFormat.format(context.resources.getString(R.string.string_name_xliff), 5))
    addTextView(
      Html.fromHtml(
        resources.getString(R.string.string_name_html),
        Html.FROM_HTML_MODE_LEGACY
      )
    )
    addTextView(context.resources.getStringArray(R.array.string_array_name).joinToString())
  }

  private fun LinearLayout.addImageView(@DrawableRes drawableRes: Int) {
    addView(
      ImageView(context).apply {
        layoutParams = LinearLayout.LayoutParams(
          ViewGroup.LayoutParams.WRAP_CONTENT,
          ViewGroup.LayoutParams.WRAP_CONTENT
        )
          .apply {
            gravity = Gravity.CENTER
            height = dip(imageSize)
            width = dip(imageSize)
          }
        setImageResource(drawableRes)
      }
    )
  }

  private fun LinearLayout.addTextView(
    text: CharSequence,
    @ColorInt backgroundColor: Int = 0,
    width: Int = ViewGroup.LayoutParams.MATCH_PARENT,
    height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
    weight: Float = 0f,
    leftMargin: Int = 0,
    rightMargin: Int = 0
  ) {
    addView(
      TextView(context).apply {
        layoutParams = LinearLayout.LayoutParams(width, height, weight).apply {
          setMargins(leftMargin, 0, rightMargin, 0)
        }
        this.text = text
        setTextColor(Color.BLACK)
        setBackgroundColor(backgroundColor)
      }
    )
  }

  private fun View.dip(value: Float): Int =
    TypedValue.applyDimension(
      TypedValue.COMPLEX_UNIT_DIP,
      value,
      resources.displayMetrics
    ).toInt()

  companion object {
    val plurals =
      mapOf("Zero" to 0, "One" to 1, "Two" to 2, "Few" to 3, "Many" to 11, "Other" to 100)
  }
}
