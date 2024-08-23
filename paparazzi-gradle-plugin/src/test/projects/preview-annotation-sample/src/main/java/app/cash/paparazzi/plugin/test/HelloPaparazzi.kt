package app.cash.paparazzi.plugin.test

import android.content.res.Configuration
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.tooling.preview.Wallpapers
import app.cash.paparazzi.annotations.Paparazzi

@Paparazzi
@Preview
@Composable
fun HelloPaparazzi() {
  Text("Hello, Paparazzi!")
}

@Paparazzi
@Preview
@Composable
fun HelloPaparazziParameterized(
  @PreviewParameter(provider = PreviewData::class) text: String
) {
  Text(text)
}

@Paparazzi
@Preview(
  name = "PreviewConfig",
  group = "Previews",
  device = "id:Nexus 6",
  apiLevel = 33,
  showSystemUi = true,
  uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
  wallpaper = Wallpapers.RED_DOMINATED_EXAMPLE,
  showBackground = true,
  fontScale = 1.5f
)
@Composable
fun HelloPaparazziPreviewConfig() {
  Text("Hello Paparazzi Preview Config!")
}

object PreviewData : PreviewParameterProvider<String> {
  override val values: Sequence<String> = sequenceOf(
    "Hello, Paparazzi One!",
    "Hello, Paparazzi Two!"
  )
}
