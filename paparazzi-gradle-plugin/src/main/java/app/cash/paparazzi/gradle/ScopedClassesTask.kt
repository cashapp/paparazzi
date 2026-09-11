package app.cash.paparazzi.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

/**
 * Carrier task used to pull a component's scoped `CLASSES` artifacts out of AGP.
 *
 * [com.android.build.api.variant.ScopedArtifactsOperation.toGet] only hands artifacts to a task, so
 * we register this no-op task purely to receive them and then re-expose them as providers that can
 * be wired into a [org.gradle.api.tasks.testing.Test] task's `testClassesDirs` and `classpath`.
 */
internal abstract class ScopedClassesTask : DefaultTask() {
  @get:Internal
  abstract val jars: ListProperty<RegularFile>

  @get:Internal
  abstract val dirs: ListProperty<Directory>

  @TaskAction
  fun noOp(): Unit = Unit
}
