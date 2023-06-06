package app.cash.paparazzi.internal.resources

import com.android.ide.common.rendering.api.ResourceNamespace
import java.io.File

/**
 * Ported from: [ProjectResourceRepository.java](https://cs.android.com/android-studio/platform/tools/adt/idea/+/c847337ee5caa1d57cb1cb991cfcafaf6c90d0c6:android/src/com/android/tools/idea/res/ProjectResourceRepository.java)
 *
 * The resource repository for a module along with all its (local) module dependencies.
 * The repository doesn't contain resources from AAR dependencies.
 */
internal class ProjectResourceRepository private constructor(
  displayName: String,
  localResources: List<LocalResourceRepository>
) : MultiResourceRepository("$displayName with modules") {
  init {
    setChildren(localResources, emptyList())
  }

  companion object {
    fun create(
      resourceDirectories: List<File>
    ): ProjectResourceRepository {
      return ProjectResourceRepository(
        displayName = "main",
        // TODO: need mapOf(package to listOf(resourceDirectory)) for each transitive project module
        localResources = listOf(
          ModuleResourceRepository.forMainResources(
            namespace = getNamespace(namespacing = ResourceNamespacing.DISABLED, packageName = "TODO"),
            resourceDirectories = resourceDirectories
          )
        )
      )
    }

    private fun getNamespace(namespacing: ResourceNamespacing, packageName: String?): ResourceNamespace {
      if (namespacing === ResourceNamespacing.DISABLED || packageName == null) {
        return ResourceNamespace.RES_AUTO
      }
      return ResourceNamespace.fromPackageName(packageName)
    }
  }
}
