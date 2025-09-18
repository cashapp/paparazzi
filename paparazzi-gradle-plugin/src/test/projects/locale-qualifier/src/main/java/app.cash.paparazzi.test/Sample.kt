import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import app.cash.paparazzi.plugin.test.R

@Composable
@Preview
fun Default() {
  GreetingPreview()
}

@Composable
@Preview(locale = "en")
fun En() {
  GreetingPreview()
}

@Composable
@Preview(locale = "en-rGB")
fun EnGB() {
  GreetingPreview()
}

@Composable
fun GreetingPreview() {
  Text(
    text = stringResource(R.string.color)
  )
}
