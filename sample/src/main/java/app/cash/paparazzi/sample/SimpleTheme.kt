package app.cash.paparazzi.sample

import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable


enum class DesignTheme {
  LIGHT,
  DARK
}

@Composable
fun SimpleTheme(
  designTheme: DesignTheme = DesignTheme.LIGHT,
  content: @Composable () -> Unit
) {
  MaterialTheme(
    colors = when (designTheme) {
      DesignTheme.LIGHT -> lightColors()
      DesignTheme.DARK -> darkColors()
    },
    content = content
  )
}
