package app.cash.paparazzi

import android.os._Original_Build
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.Remapper
import java.io.IOException
import java.util.Deque
import java.util.LinkedList
import java.util.logging.Logger

/**
 * Derived from https://cs.android.com/android-studio/platform/tools/adt/idea/+/mirror-goog-studio-main:layoutlib/src/com/android/layoutlib/LayoutlibClassLoader.java
 *
 * [ClassLoader] used for Layoutlib.
 *
 * Currently it only generates `android.os.Build` dynamically by copying the class in [_Original_Build].
 * By generating `android.os.Build` dynamically, we avoid to have it in the classpath of the plugins. Some plugins check for the
 * existence of the class in order to detect if they are running Android. This is just a workaround for that.
 */
class LayoutlibClassLoader internal constructor(parent: ClassLoader) : ClassLoader(parent) {
  private val logger: Logger = Logger.getLogger(LayoutlibClassLoader::class.java.name)

  fun initialize() {
    println("initializing class loader")
    // Define the android.os.Build and all inner classes by renaming everything in android.os._Original_Build
    generate(_Original_Build::class.java)
  }

  private fun toBinaryClassName(name: String): String = name.replace('.', '/')

  private fun toClassName(name: String): String = name.replace('/', '.')

  /**
   * Creates a copy of the passed class, replacing its name with "android.os.Build".
   */
  private fun generate(originalBuildClass: Class<*>) {
    val loader = parent
    val originalBuildClassName = originalBuildClass.name
    val originalBuildBinaryClassName = toBinaryClassName(originalBuildClassName)
    val pendingClasses: Deque<String> = LinkedList()
    pendingClasses.push(originalBuildClassName)

    val remapper = object : Remapper() {
      override fun map(typeName: String) =
        if (typeName.startsWith(originalBuildBinaryClassName)) {
          "android/os/Build${typeName.substring(originalBuildBinaryClassName.length)}"
        } else {
          typeName
        }
    }
    while (!pendingClasses.isEmpty()) {
      val name = pendingClasses.pop()
      val newName = "android.os.Build${name.substring(originalBuildClassName.length)}"
      val binaryName = toBinaryClassName(name)
      try {
        loader.getResourceAsStream("$binaryName.class").use { input ->
          val writer = ClassWriter(0)
          val reader = ClassReader(input)
          val classRemapper = object : ClassRemapper(writer, remapper) {
            override fun visitInnerClass(
              name: String, outerName: String?, innerName: String, access: Int
            ) {
              if (outerName?.startsWith(binaryName) == true) {
                pendingClasses.push(toClassName(name))
              }
              super.visitInnerClass(name, outerName, innerName, access)
            }
          }
          reader.accept(classRemapper, 0)
          val classBytes = writer.toByteArray()

          defineClass(newName, classBytes, 0, classBytes.size)
        }
      } catch (e: IOException) {
        logger.warning("Unable to define android.os.Build: $e")
      }
    }

    loadClass("android.os.Build")
  }
}
