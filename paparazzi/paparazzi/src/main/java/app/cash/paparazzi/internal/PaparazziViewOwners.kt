package app.cash.paparazzi.internal

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

internal class PaparazziLifecycleOwner : LifecycleOwner {
  val registry = LifecycleRegistry(this)
  override fun getLifecycle(): Lifecycle = registry
}

internal class PaparazziSavedStateRegistryOwner(
  private val lifecycleOwner: LifecycleOwner
) : SavedStateRegistryOwner, LifecycleOwner by lifecycleOwner {
  private val controller = SavedStateRegistryController.create(this).apply { performRestore(null) }
  override val savedStateRegistry: SavedStateRegistry = controller.savedStateRegistry
}
