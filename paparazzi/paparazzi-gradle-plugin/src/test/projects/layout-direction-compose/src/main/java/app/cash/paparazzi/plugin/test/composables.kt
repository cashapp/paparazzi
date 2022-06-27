package app.cash.paparazzi.plugin.test

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TitleColor() {
  Column(
    Modifier.fillMaxSize()
      .background(Color.White)
      .padding(16.dp),
  ) {
    Text(
      text = stringResource(id = R.string.color),
      fontSize = 20.sp,
      color = Color.Black,
    )
  }
}
