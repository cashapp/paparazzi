package app.cash.paparazzi.internal

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.ResourceRepository
import java.util.WeakHashMap

/** Returns whether this is an R class or one of its inner classes. */
private fun String.isRClassName() = endsWith(".R") || substringAfterLast('.').startsWith("R$")

/** A project-wide registry for class lookup of resource classes (R classes). */
public class ResourceClassRegistry {
  private val repositoryMap = WeakHashMap<ResourceRepository, ResourceRepositoryInfo>()
  private var packages = createPackageCache()

  /**
   * Adds definition of a new R class to the registry. The R class will contain resources from the
   * given repo in the given namespace and will be generated when the [findClassDefinition] is
   * called with a class name that matches the [packageName] and the `repo` resource repository can
   * be found in the [StudioResourceRepositoryManager] passed to [findClassDefinition].
   *
   * Note that the [ResourceClassRegistry] is a project-level component, so the same R class may be
   * generated in different ways depending on the repository used. In non-namespaced project, the
   * repository is the full [AppResourceRepository] of the module in question. In namespaced
   * projects the repository is a [com.android.resources.aar.AarResourceRepository] of just the AAR
   * contents.
   */
  public fun addLibrary(
    repo: ResourceRepository,
    idManager: ResourceIdManager,
    packageName: String?,
    namespace: ResourceNamespace,
  ) {
    if (packageName.isNullOrEmpty()) return

    var info = repositoryMap[repo]
    if (info == null) {
      info = ResourceRepositoryInfo(repo, idManager, namespace)
      repositoryMap[repo] = info
    }
    info.packages += packageName
    packages = createPackageCache() // Invalidate cache.
  }

  /** Looks up a class definition for the given name, if possible */
  public fun findClassDefinition(
    className: String,
    repositoryManager: ResourceRepositoryManager,
  ): ByteArray? {
    if (!className.isRClassName()) return null
    val pkg = className.substringBeforeLast(".", "")
    if (pkg in packages) {
      val namespace = ResourceNamespace.fromPackageName(pkg)
      val repositories = repositoryManager.getAppResourcesForNamespace(namespace)
      return findClassGenerator(repositories, className)?.generate(className)
    }
    return null
  }

  /**
   * Ideally, this method would not exist. But there are potential bugs in the caching mechanism.
   * So, the method should be called when rendering fails due to hard-to-explain causes like
   * NoSuchFieldError.
   *
   * @see ResourceIdManager.resetDynamicIds
   */
  public fun clearCache() {
    repositoryMap.clear()
    packages = createPackageCache()
  }

  private fun findClassGenerator(
    repositories: List<ResourceRepository>,
    className: String,
  ): ResourceClassGenerator? {
    return repositories
      .asSequence()
      .mapNotNull { repositoryMap[it]?.resourceClassGenerator }
      .reduceOrNull { _, _ ->
        // There is a package name collision between libraries. Throw NoClassDefFoundError
        // exception.
        throw NoClassDefFoundError(
          "$className class could not be loaded because of package name collision between libraries"
        )
      }
  }

  private fun createPackageCache() =
    buildSet { repositoryMap.values.forEach { this.addAll(it.packages) } }

  private fun removeRepository(repo: ResourceRepository) {
    repositoryMap.remove(repo)
    packages = createPackageCache()
  }

  private class ResourceRepositoryInfo(
    repo: ResourceRepository,
    idManager: ResourceIdManager,
    namespace: ResourceNamespace,
  ) {
    val resourceClassGenerator = ResourceClassGenerator.create(idManager, repo, namespace)
    val packages = mutableSetOf<String>()
  }
}
