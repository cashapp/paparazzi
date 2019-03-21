/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.squareup.paparazzi.internal

import libcore.io.Streams
import java.io.IOException

/**
 * Module class loader that loads classes from the test project.
 *
 * @param moduleRoot The path to the module root
 * @param parent The parent class loader
 */
class ModuleClassLoader(
  moduleRoot: String,
  parent: ClassLoader
) : ClassLoader(parent) {
  private val classes = mutableMapOf<String, Class<*>>()
  private val moduleRoot: String = moduleRoot + if (moduleRoot.endsWith("/")) "" else "/"

  @Throws(ClassNotFoundException::class)
  override fun findClass(name: String): Class<*>? {
    try {
      return super.findClass(name)
    } catch (ignored: ClassNotFoundException) {
    }

    var clazz: Class<*>? = classes[name]
    if (clazz == null) {
      val path = name.replace('.', '/') + ".class"
      try {
        val b = Streams.readFully(getResourceAsStream(moduleRoot + path))
        clazz = defineClass(name, b, 0, b.size)
        classes[name] = clazz
      } catch (ignore: IOException) {
        throw ClassNotFoundException("$name not found")
      }
    }

    return clazz
  }
}
