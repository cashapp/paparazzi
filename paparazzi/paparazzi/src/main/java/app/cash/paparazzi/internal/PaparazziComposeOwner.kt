package app.cash.paparazzi.internal

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

internal class PaparazziComposeOwner private constructor() : LifecycleOwner, SavedStateRegistryOwner {
  private val lifecycleRegistry: LifecycleRegistry = LifecycleRegistry(this)
  private val savedStateRegistryController = SavedStateRegistryController.create(this)

  override fun getLifecycle(): Lifecycle = lifecycleRegistry
  override val savedStateRegistry: SavedStateRegistry = savedStateRegistryController.savedStateRegistry

  companion object {
    fun register(view: View) {
      val owner = PaparazziComposeOwner()
      owner.savedStateRegistryController.performRestore(null)
      owner.lifecycleRegistry.currentState = Lifecycle.State.CREATED
      ViewTreeLifecycleOwner.set(view, owner)
      view.setViewTreeSavedStateRegistryOwner(owner)
    }
  }
}