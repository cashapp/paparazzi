package app.cash.paparazzi.sample

import android.icu.text.MessageFormat
import android.text.Html
import android.widget.TextView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.cash.paparazzi.sample.ResourcesDemoView.Companion.plurals

const val imageSize = 120f

@Preview
@Composable
fun ResourcesDemo() {
  Column(
    modifier = Modifier
      .background(Color.White)
      .fillMaxSize()
  ) {
    Image(
      modifier = Modifier
        .align(alignment = Alignment.CenterHorizontally)
        .size(imageSize.dp),
      contentScale = ContentScale.FillBounds,
      painter = painterResource(id = R.drawable.camera),
      contentDescription = "camera"
    )
    Image(
      modifier = Modifier
        .align(alignment = Alignment.CenterHorizontally)
        .size(imageSize.dp),
      contentScale = ContentScale.FillBounds,
      painter = painterResource(id = R.drawable.ic_android_black_24dp),
      contentDescription = "android"
    )

    val resources = LocalContext.current.resources

    Text(
      text = resources.getBoolean(R.bool.adjust_view_bounds).toString(),
      color = Color.Black
    )
    Text(
      modifier = Modifier
        .fillMaxWidth()
        .background(
          Color(resources.getColor(R.color.keypadGreen, null))
        ),
      text = "Color",
      color = Color.Black
    )
    Text(
      text = resources.getString(R.string.string_escaped_chars),
      color = Color.Black
    )
    Text(
      text = "Height: ${resources.getDimension(R.dimen.textview_height)}",
      color = Color.Black
    )
    Text(
      text = "Max speed: ${resources.getInteger(R.integer.max_speed)}",
      color = Color.Black
    )
    Text(
      text = "Plurals:",
      color = Color.Black
    )
    plurals.forEach { (label, quantity) ->
      Row(
        modifier = Modifier.fillMaxWidth()
      ) {
        Text(
          modifier = Modifier.padding(start = 4.dp),
          text = "$label:",
          color = Color.Black
        )
        Text(
          modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
          text = resources.getQuantityString(R.plurals.plural_name, quantity),
          color = Color.Black
        )
      }
    }
    Text(
      text = resources.getString(R.string.string_name),
      color = Color.Black
    )
    Text(
      text = MessageFormat.format(resources.getString(R.string.string_name_xliff), 5),
      color = Color.Black
    )
    AndroidView(
      factory = { context -> TextView(context) },
      update = {
        it.text =
          Html.fromHtml(resources.getString(R.string.string_name_html), Html.FROM_HTML_MODE_LEGACY)
        it.setTextColor(android.graphics.Color.BLACK)
      }
    )
    Text(
      resources.getStringArray(R.array.string_array_name).joinToString(),
      color = Color.Black
    )
  }
}
