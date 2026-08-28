package app.cash.paparazzi.plugin.test

import android.media.AudioManager
import android.widget.LinearLayout
import app.cash.paparazzi.Paparazzi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/**
 * Paparazzi in its ordinary, unsandboxed mode, in the same module as the other two.
 *
 * Guards the migration path: adding Robolectric to a module must not disturb existing Paparazzi
 * tests, which still resolve the framework from the application class loader.
 */
class AudioManagerHostModeTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun audioManagerComesFromTheApplicationLoader() {
    val loaderName = AudioManagerProbe.definingLoaderName()

    assertFalse(
      "An unsandboxed test must not pick up Paparazzi's sandbox loader, got $loaderName",
      loaderName == "app.cash.paparazzi.internal.sandbox.SandboxClassLoader"
    )
    assertFalse(
      "An unsandboxed test must not pick up Robolectric's loader, got $loaderName",
      loaderName.startsWith("org.robolectric")
    )
    assertEquals(AudioManagerProbe.AUDIO_MANAGER, AudioManager::class.java.name)
  }

  @Test
  fun renderingStillWorks() {
    paparazzi.snapshot(LinearLayout(paparazzi.context))
  }

  @Test
  fun recordsProcessIdentityForTheInteropCheck() {
    AudioManagerProbe.recordProcess("paparazzi-host")
  }
}
