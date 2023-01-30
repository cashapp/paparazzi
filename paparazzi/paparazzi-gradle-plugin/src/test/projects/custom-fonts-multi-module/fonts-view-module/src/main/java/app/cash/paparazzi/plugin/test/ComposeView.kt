package app.cash.paparazzi.plugin.test

import android.content.Context
import android.util.AttributeSet
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.plugin.font.R

class ComposeView(context: Context, attrs: AttributeSet) : AbstractComposeView(context, attrs) {

  @Composable
  override fun Content() {
    val font = FontFamily(Font(R.font.cashmarket_medium))

    Column {
      Text(
        text = "Compose: text with custom font",
        fontFamily = font
      )

      Image(
        modifier = Modifier.size(32.dp),
        painter = painterResource(R.drawable.arrow_up),
        contentDescription = "arrow up"
      )
    }
  }
}
