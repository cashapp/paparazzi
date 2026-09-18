package app.cash.paparazzi.gradle

import app.cash.paparazzi.gradle.utils.artifactsFor
import com.android.build.api.variant.UnitTest
import com.android.build.api.variant.Variant
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider

/**
 * All the relevant sources for a given Android variant.
 */
internal class AndroidVariantSources(
  private val variant: Variant,
  private val unitTest: UnitTest,
  private val project: Project
) {
  val localResourceDirs: Provider<List<Directory>>? by lazy {
    variant.sources.res?.all?.map { layers -> layers.flatten() }?.map { it.asReversed() }
  }

  // https://android.googlesource.com/platform/tools/base/+/96015063acd3455a76cdf1cc71b23b0828c0907f/build-system/gradle-core/src/main/java/com/android/build/gradle/tasks/MergeResources.kt#875
  val moduleResourceDirs: FileCollection by lazy {
    unitTest.runtimeConfiguration
      .artifactsFor(AndroidArtifacts.ArtifactType.ANDROID_RES.type) {
        it is ProjectComponentIdentifier && !it.isCurrentProject()
      }
      .artifactFiles
  }

  val aarExplodedDirs: FileCollection by lazy {
    unitTest.runtimeConfiguration
      .artifactsFor(AndroidArtifacts.ArtifactType.ANDROID_RES.type) { it !is ProjectComponentIdentifier }
      .artifactFiles
  }

  val localAssetDirs: Provider<List<Directory>>? by lazy {
    variant.sources.assets?.all?.map { layers -> layers.flatten() }?.map { it.asReversed() }
  }

  // https://android.googlesource.com/platform/tools/base/+/96015063acd3455a76cdf1cc71b23b0828c0907f/build-system/gradle-core/src/main/java/com/android/build/gradle/tasks/MergeResources.kt#875
  val moduleAssetDirs: FileCollection by lazy {
    unitTest.runtimeConfiguration
      .artifactsFor(AndroidArtifacts.ArtifactType.ASSETS.type) {
        it is ProjectComponentIdentifier && !it.isCurrentProject()
      }
      .artifactFiles
  }

  val aarAssetDirs: FileCollection by lazy {
    unitTest.runtimeConfiguration
      .artifactsFor(AndroidArtifacts.ArtifactType.ASSETS.type) { it !is ProjectComponentIdentifier }
      .artifactFiles
  }

  val packageAwareArtifactFiles: FileCollection by lazy {
    unitTest.runtimeConfiguration
      .artifactsFor(AndroidArtifacts.ArtifactType.SYMBOL_LIST_WITH_PACKAGE_NAME.type) { !it.isCurrentProject() }
      .artifactFiles
  }

  /**
   * Distinguishes by both build path and project path to correctly handle included builds.
   * @return `true` if this identifier refers to the current project.
   */
  private fun ComponentIdentifier.isCurrentProject(): Boolean {
    if (this !is ProjectComponentIdentifier) return false
    return build.buildPath == project.gradle.buildPath && projectPath == project.path
  }
}
