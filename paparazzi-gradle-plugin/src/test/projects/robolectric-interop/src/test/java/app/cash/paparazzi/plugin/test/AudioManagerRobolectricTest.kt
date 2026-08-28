package app.cash.paparazzi.plugin.test

import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Robolectric half of the interop fixture.
 *
 * Runs in the same module, and the same Gradle test worker, as [AudioManagerPaparazziTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioManagerRobolectricTest {
  @Test
  fun audioManagerComesFromRobolectricsSandbox() {
    val loaderName = AudioManagerProbe.definingLoaderName()

    assertTrue(
      "Expected a Robolectric class loader, got $loaderName",
      loaderName.startsWith("org.robolectric")
    )
    assertNotEquals(
      "Robolectric must not be handed Paparazzi's copy of the framework",
      "app.cash.paparazzi.internal.sandbox.SandboxClassLoader",
      loaderName
    )
  }

  @Test
  fun frameworkIsRobolectricInstrumentedAndroidAll() {
    assertTrue(
      "AudioManager shows no sign of Robolectric instrumentation, so it is not android-all",
      AudioManagerProbe.isRobolectricInstrumented()
    )
  }

  @Test
  fun audioManagerIsNotTheHostCopy() {
    // The host classpath carries layoutlib's android.*; Robolectric must be using android-all.
    assertNotSame(
      "Robolectric's AudioManager is the host's, so it is not sandboxed at all",
      AudioManagerProbe.hostCopy(),
      AudioManager::class.java
    )
  }

  @Test
  fun androidBehaviourIsRobolectricsNotLayoutlibs() {
    // Proves this is a working android-all + shadow implementation rather than merely a
    // differently-loaded class: ShadowAudioManager backs getRingerMode().
    val context = RuntimeEnvironment.getApplication()
    val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager

    assertEquals(AudioManager.RINGER_MODE_NORMAL, audioManager.ringerMode)
  }

  @Test
  fun recordsProcessIdentityForTheInteropCheck() {
    AudioManagerProbe.recordProcess("robolectric")
  }
}
