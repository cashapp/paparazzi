package app.cash.paparazzi.gradle

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

/**
 * A Paparazzi test component built from a first-party source set, e.g. `src/screenshotTest`.
 *
 * This is Paparazzi's own equivalent of an AGP host-test component. AGP's screenshot-test component
 * is still experimental, is gated behind `android.experimental.enableScreenshotTest`, and is shared
 * with whichever other tool wants to own that source set, so this models the pieces Paparazzi
 * actually needs — a compilation, a classpath, and somewhere to keep goldens — without depending on
 * it.
 */
internal class PaparazziHostTest(
  /** Component name, e.g. `debugScreenshotTest`. Mirrors AGP's host-test component naming. */
  val name: String,
  /** Golden image directory, e.g. `src/screenshotTest/snapshots`. */
  val snapshotDir: Provider<Directory>,
  /** Compiled classes for this source set only; the test task scans these for test classes. */
  val testClassesDirs: FileCollection,
  /** Full runtime classpath: this source set, the variant under test, and their dependencies. */
  val runtimeClasspath: FileCollection,
  /** Compilation tasks the test task must run after. */
  val compileTasks: List<TaskProvider<*>>
)

internal const val ARTIFACT_TYPE_ATTRIBUTE_NAME: String = "artifactType"
internal val ARTIFACT_TYPE: Attribute<String> = Attribute.of(ARTIFACT_TYPE_ATTRIBUTE_NAME, String::class.java)

/** AGP's artifact type for the `classes.jar` inside an AAR. */
internal const val ANDROID_CLASSES_JAR: String = "android-classes-jar"

/** Classes that have been through Paparazzi's own instrumentation. */
internal const val PAPARAZZI_INSTRUMENTED_CLASSES: String = "paparazzi-instrumented-classes"

/**
 * Creates a resolvable configuration for [sourceSetName] that resolves Android artifacts the same
 * way [template] does.
 *
 * The declared dependencies come from the user-facing `screenshotTestImplementation`-style
 * configurations, but resolution needs the variant's attributes (build type, flavour, usage, and so
 * on) for AAR and project dependencies to select the right artifacts, so they are copied verbatim.
 */
internal fun Project.createResolvableClasspath(
  name: String,
  template: Configuration,
  extendsFrom: List<Configuration>
): Configuration =
  configurations.create(name) { configuration ->
    configuration.isCanBeConsumed = false
    configuration.isCanBeResolved = true
    configuration.setExtendsFrom(extendsFrom)

    val templateAttributes = template.attributes
    templateAttributes.keySet().forEach { key ->
      @Suppress("UNCHECKED_CAST")
      val attribute = key as Attribute<Any>
      templateAttributes.getAttribute(attribute)?.let { value ->
        configuration.attributes.attribute(attribute, value)
      }
    }
  }

/**
 * Resolves a configuration down to plain class jars/directories usable on a JVM classpath.
 *
 * When [instrumented] is set, artifacts are additionally run through
 * [app.cash.paparazzi.gradle.instrumentation.ResourcesCompatTransformAction].
 */
internal fun Configuration.asClassesClasspath(instrumented: Boolean = false): FileCollection =
  incoming.artifactView { view ->
    view.attributes.attribute(
      ARTIFACT_TYPE,
      if (instrumented) PAPARAZZI_INSTRUMENTED_CLASSES else ANDROID_CLASSES_JAR
    )
    // Not every node on the graph publishes an Android classes jar (for example plain JVM
    // artifacts already are one); lenient resolution keeps those rather than failing.
    view.lenient(true)
  }.files

internal typealias KotlinCompileProvider = TaskProvider<out KotlinJvmCompile>
internal typealias JavaCompileProvider = TaskProvider<JavaCompile>
