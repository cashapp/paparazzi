package app.cash.paparazzi.internal

import app.cash.paparazzi.internal.renderresources.ResourceIdManager
import app.cash.paparazzi.internal.renderresources.ResourceIdManagerModelModule
import app.cash.paparazzi.internal.renderresources.TempIdManager
import app.cash.paparazzi.internal.resources.AppResourceRepository
import app.cash.paparazzi.internal.resources.ResourceNamespacing
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.ResourceRepository
import java.util.logging.Logger
import java.util.regex.Pattern

/**
 * Register this [ExternalAndroidLibrary] with the [ResourceClassRegistry].
 */
//private fun ResourceClassRegistry.registerLibraryResources(
//  externalLib: ExternalAndroidLibrary,
//  idManager: ResourceIdManager,
//  repositoryManager: AppResourceRepository
//) {
//
//  // Choose which resources should be in the generated R class. This is described in the JavaDoc of ResourceClassGenerator.
//  val (rClassContents: ResourceRepository, resourcesNamespace: ResourceNamespace, packageName: String?) =
//    if (repositoryManager.namespacing === ResourceNamespacing.DISABLED) {
//      val resolvedPackageName = externalLib.getResolvedPackageName() ?: return
//      Triple(repositoryManager.appResources, ResourceNamespace.RES_AUTO, resolvedPackageName)
//    }
//    else {
//      val aarResources = repositoryManager.findLibraryResources(externalLib) ?: return
//      Triple(aarResources, aarResources.namespace, aarResources.packageName)
//    }
//  this.addLibrary(rClassContents, idManager, packageName, resourcesNamespace)
//}

/**
 * Register all the [Module] resources, including libraries and dependencies with the [ResourceClassRegistry].
 */
//private fun registerResources(module: Module) {
//  val repositoryManager = Renderer.appResources
//  val idManager = LibraryResourceClassLoader.tempIdManager
//  val classRegistry = ResourceClassRegistry.instance
//
//  // If final ids are used, we will read the real class from disk later (in loadAndParseRClass), using this class loader. So we
//  // can't treat it specially here, or we will read the wrong bytecode later.
//  if (!idManager.finalIdsUsed) {
//    val resourcePackageNames = getResourcePackageNames(false)
//    for (resourcePackageName in resourcePackageNames) {
//      classRegistry.addLibrary(
//        repositoryManager,
//        idManager,
//        resourcePackageName,
//        repositoryManager.namespace
//      )
//    }
//  }
//  module.getModuleSystem().getAndroidLibraryDependencies(DependencyScopeType.MAIN)
//    .filter { it.hasResources }
//    .forEach { classRegistry.registerLibraryResources(it, idManager, repositoryManager) }
//}

// matches foo.bar.R or foo.bar.R$baz
private val RESOURCE_CLASS_NAME = Pattern.compile(".+\\.R(\\$[^.]+)?$")
private fun isResourceClassName(className: String): Boolean = RESOURCE_CLASS_NAME.matcher(className).matches()

/**
 * [ClassLoader] responsible for loading the `R` class from libraries and dependencies of the given module.
 */
internal class LibraryResourceClassLoader(
  parent: ClassLoader?,
  private val childLoader: ClassLoader
) : ClassLoader(parent) {
  init {
//    registerResources(it)
  }

  private fun findResourceClass(name: String): Class<*> {
    if (!isResourceClassName(name)) {
      throw ClassNotFoundException(name)
    }

    if (tempIdManager.finalIdsUsed) {
      // If final IDs are used, we check to see if the child loader will load the class.  If so, throw a ClassNotFoundException
      // here and let the R classes be loaded by the child class loader.
      //
      // If compiled classes are not available, there are two possible scenarios:
      //     1) We are looking for a resource class available in the ResourceClassRegistry
      //     2) We are looking for a resource class that's not available in the ResourceClassRegistry
      //
      // In the first scenario, we'll load the R class using this class loader. This covers the case where users are opening a project
      // before compiling and want to view an XML resource file, which should work.  In the second, the resource class is not available
      // anywhere, so the class loader will (correctly) fail.
      if (childLoader.loadClass(name) != null) {
        throw ClassNotFoundException(name)
      }
    }

    val repositoryManager = Renderer.appResources
    val data =
      ResourceClassRegistry.instance.findClassDefinition(name, repositoryManager) ?: throw ClassNotFoundException(name)
    LOG.info("Defining class from AAR registry")
    return defineClass(name, data, 0, data.size)
  }

  override fun findClass(name: String): Class<*> =
    try {
      super.findClass(name)
    } catch (_: ClassNotFoundException) {
      findResourceClass(name)
    }

  companion object {
    private val LOG = Logger.getLogger(LibraryResourceClassLoader::class.java.name)

    internal val tempIdManager = TempIdManager(PaparazziResourceIdManagerModelModule)
  }
}

internal object PaparazziResourceIdManagerModelModule : ResourceIdManagerModelModule {
  override val isAppOrFeature: Boolean
    get() = true
  override val namespacing: ResourceNamespacing
    get() = ResourceNamespacing.REQUIRED
  override val useRBytecodeParsing: Boolean
    get() = true
}
