package app.cash.paparazzi.plugin.test

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import app.cash.paparazzi.annotations.Paparazzi

@Paparazzi
@Preview
@Composable
fun HelloPaparazzi(
  @PreviewParameter(NameProvider::class) name: String,
) {
  Text(text = name)
}

class NameProvider: PreviewParameterProvider<String> {
  override val values: Sequence<String>
    get() = sequenceOf("Papa", "Razzi")
}
