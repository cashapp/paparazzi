/*
 * Copyright (C) 2024 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.cash.paparazzi.internal.sandbox

import java.net.URL
import java.net.URLClassLoader
import java.util.Enumeration

/**
 * A parent-last [URLClassLoader] that defines its own copy of every class
 * [SandboxConfiguration.shouldAcquire] approves, and delegates the rest upwards.
 *
 * Classes are still loaded lazily — the JVM resolves a class only when a referencing method is
 * linked — so constructing a loader costs nothing until the first `loadClass`. What the loader buys
 * is *identity*: an acquired class defined here is a distinct `Class` object from the host's, with
 * its own statics, and it becomes garbage collectable as soon as this loader does.
 */
internal class SandboxClassLoader(
  private val configuration: SandboxConfiguration,
  parent: ClassLoader = SandboxClassLoader::class.java.classLoader
) : URLClassLoader(configuration.classpath.toTypedArray(), parent) {
  /** Names this loader defined itself, for diagnostics and tests. */
  private val acquired = mutableSetOf<String>()

  override fun loadClass(name: String, resolve: Boolean): Class<*> {
    synchronized(getClassLoadingLock(name)) {
      findLoadedClass(name)?.let {
        if (resolve) resolveClass(it)
        return it
      }

      val loaded = if (configuration.shouldAcquire(name)) {
        try {
          findClass(name).also { acquired += name }
        } catch (e: ClassNotFoundException) {
          // Policy says isolate, but nothing on the sandbox classpath provides it. Falling back to
          // the parent is strictly better than failing: worst case we share a class we would rather
          // not have shared, best case it is a synthetic or generated type that never had a
          // separate copy to begin with.
          parent?.loadClass(name) ?: throw e
        }
      } else {
        parent?.loadClass(name) ?: findClass(name)
      }

      if (resolve) resolveClass(loaded)
      return loaded
    }
  }

  /**
   * Parent-last resource lookup, so an acquired class reading its own `META-INF` or `.properties`
   * sibling sees the sandbox copy rather than the host's.
   */
  override fun getResource(name: String): URL? = findResource(name) ?: parent?.getResource(name)

  override fun getResources(name: String): Enumeration<URL> {
    val local = findResources(name).toList()
    val fromParent = parent?.getResources(name)?.toList().orEmpty()
    return java.util.Collections.enumeration(local + fromParent.filterNot { it in local })
  }

  /** Class names this loader has defined so far. */
  fun acquiredClassNames(): Set<String> = synchronized(this) { acquired.toSet() }

  override fun toString(): String = "SandboxClassLoader@${Integer.toHexString(hashCode())}"

  companion object {
    init {
      registerAsParallelCapable()
    }
  }
}
