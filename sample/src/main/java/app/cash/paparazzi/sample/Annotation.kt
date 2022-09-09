package app.cash.paparazzi.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.annotation.api.Paparazzi
import app.cash.paparazzi.annotation.api.config.ComposableWrapper
import app.cash.paparazzi.annotation.api.config.ValuesComposableWrapper
import app.cash.paparazzi.sample.util.DesignTheme
import app.cash.paparazzi.sample.util.DesignTheme.DARK
import app.cash.paparazzi.sample.util.DesignTheme.LIGHT
import app.cash.paparazzi.sample.util.SimpleTheme

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
        model.desc,
        modifier = Modifier.padding(top = 8.dp),
        style = MaterialTheme.typography.body1
      )
    }
  }
}

@Paparazzi
@Preview
@Composable
fun SimpleCardPreview() {
  val model = SimpleCardModel(
    title = "Hello World",
    desc = "This is a basic preview"
  )

  SimpleCard(model = model)
}

@Paparazzi(
  name = "greenBox",
  composableWrapper = GreenBoxComposableWrapper::class
)
@Composable
fun GreenBoxPreview() {
  val model = SimpleCardModel(
    title = "Hello World",
    desc = "In a green box"
  )

  SimpleCard(model = model)
}

@ThemedScaledPaparazzi
@Composable
fun ThemedScaledPreview() {
  val model = SimpleCardModel(
    title = "Hello World",
    desc = "Themed & scaled"
  )

  SimpleCard(model = model)
}

@Paparazzi
@Preview
@Composable
fun TitleProviderPreview(
  @PreviewParameter(TitleProvider::class) title: String
) {
  SimpleCard(
    model = SimpleCardModel(
      title = title,
      desc = "This is a provided title"
    )
  )
}

class TitleProvider : PreviewParameterProvider<String> {
  override val values: Sequence<String> = sequenceOf("Hello", "World")
}

class GreenBoxComposableWrapper : ComposableWrapper {
  @Composable
  override fun wrap(
    content: @Composable () -> Unit
  ) {
    Box(
      modifier = Modifier
        .wrapContentSize()
        .background(Color.Green)
        .padding(24.dp)
    ) {
      content()
    }
  }
}

class ThemeComposableWrapper : ValuesComposableWrapper<DesignTheme>() {
  override val values = sequenceOf(LIGHT, DARK)

  @Composable
  override fun wrap(
    value: DesignTheme,
    content: @Composable () -> Unit
  ) {
    SimpleTheme(value) {
      content()
    }
  }
}

@Paparazzi(
  name = "themed,scaled",
  fontScales = [1.0f, 2.0f],
  composableWrapper = ThemeComposableWrapper::class
)
annotation class ThemedScaledPaparazzi
