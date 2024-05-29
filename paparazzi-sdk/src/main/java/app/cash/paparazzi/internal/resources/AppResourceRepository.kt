package app.cash.paparazzi.internal.resources

import java.io.File

/**
 * Ported from: [AppResourceRepository.java](https://cs.android.com/android-studio/platform/tools/adt/idea/+/308d48ccea9508984bce3e120a03ed9a105d4331:android/src/com/android/tools/idea/res/AppResourceRepository.java)
 *
 * Returns the repository with all non-framework resources available to a given module (in the current variant).
 * This includes not just the resources defined in this module, but in any other modules that this module depends
 * on, as well as any libraries those modules may depend on (e.g. appcompat).
 *
 * When a layout is rendered in the layout editor, it is getting resources from the app resource repository:
 * it should see all the resources just like the app does.
 */
internal class AppResourceRepository private constructor(
  displayName: String,
  localResources: List<LocalResourceRepository>,
  libraryResources: Collection<AarSourceResourceRepository>
) : MultiResourceRepository("$displayName with modules and libraries") {

  init {
    setChildren(localResources, libraryResources)
  }

  companion object {
    fun create(
      localResourceDirectories: List<File>,
      moduleResourceDirectories: List<File>,
      libraryRepositories: Collection<AarSourceResourceRepository>
    ): AppResourceRepository {
      return AppResourceRepository(
        displayName = "",
        listOf(ProjectResourceRepository.create(localResourceDirectories, moduleResourceDirectories)),
        libraryRepositories
      )
    }
  }
}
