package app.cash.paparazzi.sample

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.sample.util.ScaledPaparazzi
import app.cash.paparazzi.sample.util.ThemedPaparazzi

data class SimpleCardModel(
  val title: String,
  val desc: String
)

@Composable
fun SimpleCard(
  modifier: Modifier = Modifier,
  model: SimpleCardModel
) {
  Card(modifier = modifier.wrapContentSize(), backgroundColor = MaterialTheme.colors.background) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(model.title, style = MaterialTheme.typography.h6)
      Text(
        model.desc, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.body1
      )
    }
  }
}


@ScaledPaparazzi
@Preview
@Composable
fun SimpleCardPreview() {
  val model = SimpleCardModel(
    title = "Hello World",
    desc = "This is a preview"
  )

  SimpleCard(model = model)
}

@ThemedPaparazzi
@Preview
@Composable
internal fun SimpleCardPreviewProvider(
  @PreviewParameter(TitleProvider::class) title: String
) {
  SimpleCard(model = SimpleCardModel(
    title = title,
    desc = "This is a provided title"
  ))
}

class TitleProvider : PreviewParameterProvider<String> {
  override val values: Sequence<String> = sequenceOf(
      "Hello World", "Hello World".reversed(),
  )
}

