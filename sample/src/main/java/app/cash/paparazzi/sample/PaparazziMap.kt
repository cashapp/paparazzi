package app.cash.paparazzi.sample

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import com.google.maps.android.compose.GoogleMap

@Preview
@Composable
fun PaparazziMap() {
  CompositionLocalProvider(
    LocalInspectionMode provides false,
  ) {
    GoogleMap(
      modifier = Modifier.fillMaxSize()
    )
  }
}
