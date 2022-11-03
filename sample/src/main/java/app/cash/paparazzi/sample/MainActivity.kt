package app.cash.paparazzi.sample

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.ui.viewinterop.AndroidView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
          AndroidView(
            factory = { context ->
              CustomButton(
                ContextThemeWrapper(context, R.style.CustomTheme),
              )
            },
            update = { button ->
              with(button) {
                text = "Hello Paparazzi"
              }
            }
          )
        }

//        binding = ActivityMainBinding.inflate(layoutInflater)
//        setContentView(binding.root)
//
//        setSupportActionBar(binding.toolbar)
    }
}
