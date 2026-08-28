package app.cash.paparazzi.plugin.test

import android.media.AudioManager
import android.widget.LinearLayout
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.PaparazziRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Paparazzi half of the interop fixture, sandboxed.
 *
 * Runs in the same module, and the same Gradle test worker, as [AudioManagerRobolectricTest]. Two
 * frameworks each stand up their own class loader over their own Android framework jar; neither may
 * end up holding the other's classes, or the host's.
 */
@RunWith(PaparazziRunner::class)
class AudioManagerPaparazziTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Before
  fun assumeSandboxIsSupported() {
    // This fixture deliberately mixes sandboxed and unsandboxed layoutlib in one JVM, which
    // Windows cannot do: it resolves DLL imports by base name.
    assumeFalse(
      "layoutlib cannot be sandboxed alongside unsandboxed Paparazzi in one JVM on Windows",
      System.getProperty("os.name").lowercase(java.util.Locale.US).startsWith("windows")
    )
  }

  @Test
  fun audioManagerComesFromPaparazzisSandbox() {
    val loaderName = AudioManagerProbe.definingLoaderName()

    assertEquals("app.cash.paparazzi.internal.sandbox.SandboxClassLoader", loaderName)
    assertFalse(
      "Paparazzi must not be handed Robolectric's copy of the framework",
      loaderName.startsWith("org.robolectric")
    )
  }

  @Test
  fun audioManagerIsADistinctCopyFromTheHosts() {
    // Both come from layoutlib.jar, but they are different Class objects with different statics.
    // This is the whole point of the sandbox, and it is what lets Robolectric coexist.
    val host = AudioManagerProbe.hostCopy()

    assertEquals(AudioManagerProbe.AUDIO_MANAGER, host.name)
    assertNotSame(host, AudioManager::class.java)
  }

  @Test
  fun frameworkComesFromLayoutlibNotAndroidAll() {
    val codeSource = AudioManagerProbe.codeSourceOrNull()

    assertTrue(
      "Expected AudioManager to be read from layoutlib, got $codeSource",
      codeSource != null && codeSource.contains("layoutlib")
    )
  }

  @Test
  fun frameworkIsNotRobolectricInstrumented() {
    // The converse of the Robolectric fixture's assertion: layoutlib's AudioManager must reach
    // Paparazzi untouched, with none of Robolectric's rewritten members.
    assertFalse(
      "Paparazzi's AudioManager carries Robolectric instrumentation",
      AudioManagerProbe.isRobolectricInstrumented()
    )
  }

  @Test
  fun renderingStillWorksAlongsideRobolectric() {
    paparazzi.snapshot(LinearLayout(paparazzi.context))
  }

  @Test
  fun recordsProcessIdentityForTheInteropCheck() {
    AudioManagerProbe.recordProcess("paparazzi-sandboxed")
  }
}
