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
 * Build-time facts about each layoutlib release Paparazzi has been verified against, generated
 * from `gradle/layoutlib-compat.properties` (maintained by `./gradlew verifyLayoutlibVersion`, see LAYOUTLIB.md).
 * The runtime reads the same file for per-version ICU data (see `LayoutlibCompat`).
 */
internal object LayoutlibVersions {
  /** Every layoutlib version with a verified entry, in ascending order. */
  val knownVersions: List<String> = LAYOUTLIB_KNOWN_VERSIONS

  /**
   * layoutlib's POM doesn't declare a `layoutlib-api` dependency, so its bytecode can reference API
   * classes missing from the version Paparazzi pins. Maps a layoutlib version to the *minimum*
   * `layoutlib-api` satisfying every reference in its jar; absent means Paparazzi's pin suffices.
   *
   * Paparazzi's own references (and the abstract surface of `LayoutlibCallback`, `ILayoutLog`, etc.
   * it implements) are satisfied by every `layoutlib-api` from 31.0.0 through 32.4.1, so raising it
   * is safe.
   */
  val minLayoutlibApi: Map<String, String> = LAYOUTLIB_MIN_API

  /** Framework API level each known layoutlib bundles (`ro.build.version.sdk` in its runtime). */
  val bundledSdk: Map<String, Int> = LAYOUTLIB_BUNDLED_SDK

  fun isKnown(layoutlibVersion: String): Boolean = layoutlibVersion in knownVersions

  /**
   * Minimum `layoutlib-api` for [layoutlibVersion]. Unknown versions inherit the requirement of the
   * closest known version at or below them (API requirements only grow), or `null` if older than
   * every known version.
   */
  fun minLayoutlibApiFor(layoutlibVersion: String): String? {
    if (isKnown(layoutlibVersion)) return minLayoutlibApi[layoutlibVersion]
    val requested = parse(layoutlibVersion) ?: return null
    return knownVersions
      .filter { compare(parse(it)!!, requested) <= 0 }
      .mapNotNull { minLayoutlibApi[it] }
      .maxWithOrNull { a, b -> compare(parse(a)!!, parse(b)!!) }
  }

  /**
   * Framework API level bundled by [layoutlibVersion]. Unknown versions assume the closest known
   * version at or below them (conservative: never newer than what's been verified), or the oldest
   * known version if older than all of them.
   */
  fun bundledSdkFor(layoutlibVersion: String): Int {
    bundledSdk[layoutlibVersion]?.let { return it }
    val requested = parse(layoutlibVersion)
    val closest = requested?.let { req -> knownVersions.lastOrNull { compare(parse(it)!!, req) <= 0 } }
    return bundledSdk.getValue(closest ?: knownVersions.first())
  }

  /**
   * The targetSdk Paparazzi renders with. An explicit `testOptions.targetSdk` is honored as-is;
   * otherwise `compileSdk` capped at [bundledSdk], since `Build.VERSION.SDK_INT` reporting a level
   * newer than layoutlib's framework makes SDK-gated code call APIs that don't exist; otherwise
   * [bundledSdk].
   */
  fun resolveTargetSdk(explicitTargetSdk: Int?, compileSdk: Int?, bundledSdk: Int): Int =
    explicitTargetSdk ?: compileSdk?.coerceAtMost(bundledSdk) ?: bundledSdk

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
