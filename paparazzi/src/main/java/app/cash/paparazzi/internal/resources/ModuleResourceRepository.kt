package app.cash.paparazzi.internal.resources

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.SingleNamespaceResourceRepository
import java.io.File

/**
 * Ported from: [ModuleResourceRepository.java](https://cs.android.com/android-studio/platform/tools/adt/idea/+/c847337ee5caa1d57cb1cb991cfcafaf6c90d0c6:android/src/com/android/tools/idea/res/ModuleResourceRepository.java)
 *
 * A repository responsible for all resources defined in all resource folders of a given module.
 * A resource folder can point to:
 * - A resource source set (src/main/res, src/debug/res)
 * - A location where [res values](https://developer.android.com/build/gradle-tips#share-custom-fields-and-resource-values-with-your-app-code) are generated
 */
internal class ModuleResourceRepository private constructor(
  displayName: String,
  private val namespace: ResourceNamespace,
  delegates: List<LocalResourceRepository>
) : MultiResourceRepository(displayName), SingleNamespaceResourceRepository {
  init {
    setChildren(delegates, emptyList())
  }

  override fun getNamespace(): ResourceNamespace = namespace

  override fun getPackageName(): String? = namespace.packageName
  override fun getNamespaces(): Set<ResourceNamespace> =
    super<MultiResourceRepository>.getNamespaces()

  override fun getLeafResourceRepositories(): Collection<SingleNamespaceResourceRepository> =
    super<MultiResourceRepository>.getLeafResourceRepositories()

  companion object {
    /**
     * Returns the resource repository for a single module (which can possibly have multiple resource folders).
     * Does not include resources from any dependencies.
     *
     * @param namespace the namespace for the repository
     * @return the resource repository
     */
    fun forMainResources(
      namespace: ResourceNamespace,
      resourceDirectories: List<File>
    ): LocalResourceRepository {
      return ModuleResourceRepository(
        displayName = "", // TODO
        namespace = namespace,
        delegates = addRepositoriesInReverseOverlayOrder(resourceDirectories, namespace)
      )
    }

    /**
     * Inserts repositories for the given [resourceDirectories] as [ResourceFolderRepository] instances, in the right order.
     *
     * [resourceDirectories] is assumed to be in the inverse order of what we need. The code in
     * [MultiResourceRepository.getMap] gives priority to child repositories which are earlier
     * in the list, so after creating repositories for every folder, we add them in reverse to the list.
     *
     * @param resourceDirectories directories for which repositories should be constructed
     */
    private fun addRepositoriesInReverseOverlayOrder(
      resourceDirectories: List<File>,
      namespace: ResourceNamespace
    ): List<LocalResourceRepository> =
      buildList {
        resourceDirectories.asReversed().forEach {
          add(ResourceFolderRepository(it, namespace))
        }
      }
  }
}
