/*
 * Copyright (C) 2026 Square, Inc.
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
package app.cash.paparazzi.layoutlib

/**
 * Orders versions like `16.1.0 < 16.1.0-jdk17 < 16.1.1`: numeric components first, then an
 * unqualified release sorts before a qualified one with the same numbers.
 */
internal object VersionOrder : Comparator<String> {
  override fun compare(a: String, b: String): Int {
    val na = numbers(a)
    val nb = numbers(b)
    for (i in 0 until maxOf(na.size, nb.size)) {
      val c = na.getOrElse(i) { 0 }.compareTo(nb.getOrElse(i) { 0 })
      if (c != 0) return c
    }
    return qualifier(a).compareTo(qualifier(b))
  }

  fun isStable(version: String): Boolean = version.matches(Regex("""\d+(\.\d+)*"""))

  private fun numbers(version: String) = version.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }

  private fun qualifier(version: String) = version.substringAfter('-', missingDelimiterValue = "")
}
