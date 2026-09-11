package app.cash.paparazzi.plugin.test

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class SecureTextFieldTest {
  @get:Rule
  val paparazzi = Paparazzi()

  // Regression test: BasicSecureTextField (Compose Foundation 1.12+) registers a callback with
  // android.text.ShowSecretsSetting (API 37), which layoutlib doesn't implement and throws
  // "not mocked" without Paparazzi's interceptor.
  @Test
  fun secureTextField() {
    paparazzi.snapshot {
      BasicSecureTextField(
        state = rememberTextFieldState("hunter2"),
        modifier = Modifier.padding(16.dp)
      )
    }
  }
}
