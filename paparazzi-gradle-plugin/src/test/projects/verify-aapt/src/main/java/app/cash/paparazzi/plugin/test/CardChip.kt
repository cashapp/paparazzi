package app.cash.paparazzi.plugin.test

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
fun CardChip() {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Image(
      modifier = Modifier.size(140.dp),
      painter = painterResource(id = R.drawable.card_chip),
      contentDescription = null
    )
  }
}
