package app.cash.paparazzi.sample1060.dynamicfeature

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
fun HelloWorld() {
  Text(text = stringResource(id = app.cash.paparazzi.sample1060.R.string.app_name))
}
