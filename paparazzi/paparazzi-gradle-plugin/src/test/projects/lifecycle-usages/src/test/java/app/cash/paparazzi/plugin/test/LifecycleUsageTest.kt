package app.cash.paparazzi.plugin.test

import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class LifecycleUsageTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test fun OnBackPressedDispatcherOwner() {
    val view = View(paparazzi.context)
    view.doOnAttach {
      val dispatcherOwner = view.findViewTreeOnBackPressedDispatcherOwner()!!
      dispatcherOwner.getOnBackPressedDispatcher().addCallback(
        object : OnBackPressedCallback(/* enabled = */ true) {
          override fun handleOnBackPressed() = Unit
        }
      )
    }
    paparazzi.snapshot(view)
  }

  private fun View.doOnAttach(action: (view: View) -> Unit) {
    addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
      override fun onViewAttachedToWindow(view: View) = action(view)
      override fun onViewDetachedFromWindow(view: View) = Unit
    })
  }
}
