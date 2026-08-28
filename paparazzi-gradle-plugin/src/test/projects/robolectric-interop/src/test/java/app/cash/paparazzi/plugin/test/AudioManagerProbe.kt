package app.cash.paparazzi.plugin.test

import android.media.AudioManager

/**
 * Shared probes for the Robolectric/Paparazzi interop fixture.
 *
 * `android.media.AudioManager` is a useful subject because it exists in both worlds and comes from
 * a different artifact in each: Robolectric loads it from `android-all`, Paparazzi from
 * `layoutlib.jar`. If either framework were to reach across into the other's copy — or into the
 * host's — that is a class identity bug, and this is where it shows up.
 */
object AudioManagerProbe {
  const val AUDIO_MANAGER: String = "android.media.AudioManager"

  /** The loader that defined the [AudioManager] this code links against. */
  fun definingLoader(): ClassLoader = AudioManager::class.java.classLoader!!

  fun definingLoaderName(): String = definingLoader().javaClass.name

  /**
   * The host's copy of [AudioManager], reached through the sandbox loader's parent.
   *
   * Deliberately not `getSystemClassLoader()`: Gradle's test worker builds its own loader
   * hierarchy, and the parent link is the precise relationship being asserted.
   */
  fun hostCopy(): Class<*> {
    val parent = requireNotNull(definingLoader().parent) {
      "$AUDIO_MANAGER was loaded by a loader with no parent, so it cannot be sandboxed."
    }
    return parent.loadClass(AUDIO_MANAGER)
  }

  /**
   * True if this [AudioManager] was instrumented by Robolectric.
   *
   * Robolectric's ClassInstrumentor renames each original method to `$$robo$$<name>`, so the
   * presence of such a member is direct evidence of an instrumented `android-all` class rather
   * than layoutlib's. Robolectric defines these classes from transformed bytes with no
   * ProtectionDomain, so [codeSourceOrNull] cannot answer this.
   */
  fun isRobolectricInstrumented(): Boolean =
    AudioManager::class.java.declaredMethods.any { it.name.startsWith("$\$robo$") } ||
      AudioManager::class.java.declaredFields.any { it.name.startsWith("__robo") }

  /** Where the [AudioManager] in scope was actually read from, when the JVM will say. */
  fun codeSourceOrNull(): String? = AudioManager::class.java.protectionDomain?.codeSource?.location?.toString()

  /**
   * Records this JVM's identity so the build can prove all three test classes shared one process.
   *
   * Coexisting in a module is easy if Gradle forks between classes; coexisting in a single JVM is
   * the property actually under test.
   */
  fun recordProcess(tag: String) {
    val dir = java.io.File(System.getProperty("user.dir"), "build/interop").apply { mkdirs() }
    java.io.File(dir, "$tag.pid").writeText(ProcessHandle.current().pid().toString())
  }
}
