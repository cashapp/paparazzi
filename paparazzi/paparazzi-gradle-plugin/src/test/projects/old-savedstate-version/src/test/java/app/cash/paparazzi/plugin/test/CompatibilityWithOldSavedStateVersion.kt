package app.cash.paparazzi.plugin.test

import android.view.View
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.ViewTreeSavedStateRegistryOwner
import app.cash.paparazzi.Paparazzi
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

class CompatibilityWithOldSavedStateVersion {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun `SavedStateRegistry should not be installed on older versions of savedstate`() {
    val view = View(paparazzi.context)
    var savedStateOwner: SavedStateRegistryOwner? = null
    var lifecycleOwner: LifecycleOwner? = null

    view.doOnAttach {
      lifecycleOwner = ViewTreeLifecycleOwner.get(view)
      savedStateOwner = ViewTreeSavedStateRegistryOwner.get(view)
    }

    paparazzi.snapshot(view)

    // Because this test's gradle module uses savedstate:1.1.0, which does
    // not have SavedStateRegistryController.Companion.create(), Paparazzi
    // should not install a SavedStateRegistryOwner for this View.
    assertThat(savedStateOwner).isNull()
    assertThat(lifecycleOwner).isNotNull()
  }

  private fun View.doOnAttach(action: (view: View) -> Unit) {
    addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
      override fun onViewAttachedToWindow(view: View) = action(view)
      override fun onViewDetachedFromWindow(view: View) = Unit
    })
  }
}
