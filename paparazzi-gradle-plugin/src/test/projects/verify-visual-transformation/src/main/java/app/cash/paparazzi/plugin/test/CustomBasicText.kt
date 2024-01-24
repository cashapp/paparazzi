package app.cash.paparazzi.plugin.test

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp

@Composable
fun CustomBasicText(value: String) {
  val decimalStyle = SpanStyle(fontSize = 30.sp)
  val fractionalStyle = SpanStyle(fontSize = 15.sp)
  Surface {
    BasicTextField(
      value = value,
      onValueChange = {},
      visualTransformation = DoubleVisualTransformation(decimalStyle, fractionalStyle),
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
  }
}

@Preview
@Composable
fun FractionalPreview() {
  CustomBasicText(value = "10.00")
}

@Preview
@Composable
fun DecimalPreview() {
  CustomBasicText(value = "10")
}

class DoubleVisualTransformation(
  private val decimalStyle: SpanStyle,
  private val fractionalStyle: SpanStyle
) : VisualTransformation {
  override fun filter(text: AnnotatedString) = TransformedText(
    buildAmountAnnotatedString(text.toString()),
    OffsetMapping.Identity
  )

  private fun buildAmountAnnotatedString(text: String): AnnotatedString {
    val builder = AnnotatedString.Builder()
    when (text.contains(".")) {
      false -> builder.withStyle(style = decimalStyle) { append(text.ifEmpty { "" }) }
      true -> {
        val groups = text.split(".")
        val decimal: String = groups[0]
        builder.withStyle(style = decimalStyle) {
          append(decimal)
        }
        val fractional: String? = groups.getOrNull(1)
        fractional?.let {
          builder.withStyle(style = fractionalStyle) {
            append(" ")
            append(fractional.toString())
          }
        }
      }
    }
    return builder.toAnnotatedString()
  }
}
