package app.cash.paparazzi.internal

import app.cash.paparazzi.internal.renderresources.ResourceClassGenerator
import app.cash.paparazzi.internal.renderresources.ResourceIdManager
import app.cash.paparazzi.internal.resources.AppResourceRepository
import app.cash.paparazzi.internal.resources.MultiResourceRepository
import app.cash.paparazzi.internal.resources.ResourceNamespacing
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.ResourceRepository
import java.util.WeakHashMap

/** Returns whether this is an R class or one of its inner classes. */
private fun String.isRClassName() = endsWith(".R") || substringAfterLast('.').startsWith("R$")

internal class ResourceClassRegistry {
    private val repoMap = WeakHashMap<ResourceRepository, ResourceRepositoryInfo>()
    private var packages: Set<String> = createPackageCache()

    fun addLibrary(
        repo: ResourceRepository,
        idManager: ResourceIdManager,
        packageName: String?,
        namespace: ResourceNamespace,
    ) {
        if (packageName.isNullOrEmpty()) return
        var info = repoMap[repo]
        if (info == null) {
            info = ResourceRepositoryInfo(repo, idManager, namespace)
            repoMap[repo] = info
        }
        info.packages.add(packageName)
        packages = createPackageCache() // Invalidate cache.
    }

    /** Looks up a class definition for the given name, if possible */
    fun findClassDefinition(
        className: String,
        repositoryManager: AppResourceRepository,
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

    private fun findClassGenerator(
        repositories: List<ResourceRepository>,
        className: String,
    ): ResourceClassGenerator? {
        return repositories
            .asSequence()
            .mapNotNull { repoMap[it]?.resourceClassGenerator }
            .reduceOrNull { _, _ ->
                // There is a package name collision between libraries. Throw NoClassDefFoundError
                // exception.
                throw NoClassDefFoundError(
                    "$className class could not be loaded because of package name collision between libraries"
                )
            }
    }

    private fun createPackageCache(): Set<String> {
        return buildSet { repoMap.values.forEach { addAll(it.packages) } }
    }

    private fun removeRepository(repo: ResourceRepository) {
        repoMap.remove(repo)
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

    companion object {
        internal val instance = ResourceClassRegistry()
    }
}

private fun AppResourceRepository.getAppResourcesForNamespace(
    namespace: ResourceNamespace
): List<ResourceRepository> {
    val appRepository = this as MultiResourceRepository
    return appRepository.getRepositoriesForNamespace(namespace)
}

