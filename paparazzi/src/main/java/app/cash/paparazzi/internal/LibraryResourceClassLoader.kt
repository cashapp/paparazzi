package app.cash.paparazzi.internal

import java.util.logging.Logger
import java.util.regex.Pattern

public class LibraryResourceClassLoader(parent: ClassLoader?) : ClassLoader(parent) {
  override fun findClass(name: String): Class<*> =
    try {
      super.findClass(name)
    } catch (e: ClassNotFoundException) {
      findResourceClass(name)
    }

  private fun findResourceClass(name: String): Class<*> {
    if (!isResourceClassName(name)) {
      throw ClassNotFoundException(name)
    }

    if (StudioResourceIdManager.get(module).finalIdsUsed) {
      // If final IDs are used, we check to see if the child loader will load the class.  If so, throw a ClassNotFoundException
      // here and let the R classes be loaded by the child class loader.
      //
      // If compiled classes are not available, there are two possible scenarios:
      //     1) We are looking for a resource class available in the ResourceClassRegistry
      //     2) We are looking for a resource class that's not available in the ResourceClassRegistry
      //     2) We are looking for a resource class that's not available in the ResourceClassRegistry
      //
      // In the first scenario, we'll load the R class using this class loader. This covers the case where users are opening a project
      // before compiling and want to view an XML resource file, which should work.  In the second, the resource class is not available
      // anywhere, so the class loader will (correctly) fail.
      if (childLoader.loadClass(name) != null) {
        throw ClassNotFoundException(name)
      }
    }

    val repositoryManager = StudioResourceRepositoryManager.getInstance()
    val data = ResourceClassRegistry().findClassDefinition(name, repositoryManager) ?: throw ClassNotFoundException(name)
    //LOG.debug("  Defining class from AAR registry")
    return defineClass(name, data, 0, data.size)
  }

  public companion object {
    private val LOG = Logger.getLogger(LibraryResourceClassLoader::class.java.getName())
  }
}

// matches foo.bar.R or foo.bar.R$baz
private val RESOURCE_CLASS_NAME = Pattern.compile(".+\\.R(\\$[^.]+)?$")
private fun isResourceClassName(className: String): Boolean = RESOURCE_CLASS_NAME.matcher(className).matches()
