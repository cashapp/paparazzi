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
package app.cash.paparazzi.gradle

/**
 * Build-time facts about each layoutlib release Paparazzi has been verified against.
 *
 * The runtime-side counterpart (ICU data file, behavioral shims) lives in
 * `app.cash.paparazzi.internal.LayoutlibCompat`. Keep both in sync when adding a version.
 */
internal object LayoutlibVersions {
  /**
   * layoutlib's POM doesn't declare a `layoutlib-api` dependency, so its bytecode can reference API
   * classes missing from the version Paparazzi pins.
   *
   * Values are the *minimum* `layoutlib-api` satisfying every `com.android.ide.common.rendering.api`
   * and `com.android.resources` class/method/field reference in that layoutlib jar, computed by
   * scanning its constant pool against each `layoutlib-api` release. `null` means Paparazzi's pinned
   * version already suffices.
   *
   * Paparazzi's own references (and the abstract surface of `LayoutlibCallback`, `ILayoutLog`, etc.
   * it implements) are satisfied by every `layoutlib-api` from 31.0.0 through 32.4.1, so raising it
   * is safe.
   */
  val minLayoutlibApi: Map<String, String?> = linkedMapOf(
    "16.2.1" to null,
    "16.2.3" to null,
    "16.2.4" to null,
    "17.0.0" to null,
    // RecyclableImage, RenderSizeProvider, SessionParams.getSizeProvider()
    "17.0.1" to "32.3.0"
  )

  val knownVersions: Set<String> get() = minLayoutlibApi.keys

  fun isKnown(layoutlibVersion: String): Boolean = layoutlibVersion in minLayoutlibApi

  /**
   * Minimum `layoutlib-api` for [layoutlibVersion]. Unknown versions inherit the requirement of the
   * closest known version at or below them (API requirements only grow), or `null` if older than
   * every known version.
   */
  fun minLayoutlibApiFor(layoutlibVersion: String): String? {
    minLayoutlibApi[layoutlibVersion]?.let { return it }
    if (isKnown(layoutlibVersion)) return null
    val requested = parse(layoutlibVersion) ?: return null
    return minLayoutlibApi.entries
      .filter { (known, _) -> compare(parse(known)!!, requested) <= 0 }
      .mapNotNull { it.value }
      .maxWithOrNull { a, b -> compare(parse(a)!!, parse(b)!!) }
  }

  /** Returns [required] if it's newer than [requested], otherwise `null` (never downgrade). */
  fun upgradeTo(requested: String?, required: String?): String? {
    if (required == null) return null
    val req = requested?.let(::parse) ?: return required
    return if (compare(req, parse(required)!!) < 0) required else null
  }

  private fun parse(version: String): List<Int>? =
    version.substringBefore('-').split('.').map { it.toIntOrNull() ?: return null }

  private fun compare(a: List<Int>, b: List<Int>): Int {
    for (i in 0 until maxOf(a.size, b.size)) {
      val c = a.getOrElse(i) { 0 }.compareTo(b.getOrElse(i) { 0 })
      if (c != 0) return c
    }
    return 0
  }
}
