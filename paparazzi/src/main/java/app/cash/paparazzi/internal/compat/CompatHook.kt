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
package app.cash.paparazzi.internal.compat

import app.cash.paparazzi.internal.LayoutlibVersion

/**
 * One layoutlib behavior difference, declared as ordered [Variant]s. The first variant whose
 * version range contains the running layoutlib version *and* whose [Variant.available] probe
 * passes wins.
 *
 * An unknown version (`null`) is treated as newer than every bound, so `since` gates pass and
 * `until` gates fail, leaving feature probes to decide.
 */
internal class CompatHook<T>(
  val name: String,
  val variants: List<Variant<T>>
) {
  class Variant<T>(
    val label: String,
    /** Inclusive lower bound; `null` means no lower bound. */
    val since: LayoutlibVersion? = null,
    /** Exclusive upper bound; `null` means no upper bound. */
    val until: LayoutlibVersion? = null,
    val available: () -> Boolean = { true },
    val impl: T
  ) {
    fun matches(version: LayoutlibVersion?): Boolean {
      val inRange = if (version == null) {
        until == null
      } else {
        (since == null || version >= since) && (until == null || version < until)
      }
      return inRange && available()
    }
  }

  fun select(version: LayoutlibVersion?): Variant<T> =
    variants.firstOrNull { it.matches(version) }
      ?: error(
        "No '$name' implementation for layoutlib ${version ?: "<unknown>"}; " +
          "tried: ${variants.joinToString { it.label }}"
      )
}

internal class CompatHookBuilder<T>(private val name: String) {
  private val variants = mutableListOf<CompatHook.Variant<T>>()

  fun variant(
    label: String,
    since: String? = null,
    until: String? = null,
    available: () -> Boolean = { true },
    impl: T
  ) {
    variants += CompatHook.Variant(label, since?.let(::parseBound), until?.let(::parseBound), available, impl)
  }

  fun build(): CompatHook<T> = CompatHook(name, variants.toList())

  private fun parseBound(value: String) =
    requireNotNull(LayoutlibVersion.parse(value)) { "Invalid layoutlib version bound '$value' in '$name'" }
}

internal fun <T> compatHook(name: String, block: CompatHookBuilder<T>.() -> Unit): CompatHook<T> =
  CompatHookBuilder<T>(name).apply(block).build()

/**
 * A [CompatHook] bound to a version and resolved once, on first use. Registered hooks can be listed
 * via [CompatRegistry.describe] for diagnostics.
 */
internal class CompatRegistry(private val version: () -> LayoutlibVersion?) {
  private val resolved = linkedMapOf<String, Lazy<CompatHook.Variant<*>>>()

  fun <T> register(hook: CompatHook<T>): Lazy<T> {
    val variant = lazy { hook.select(version()) }
    resolved[hook.name] = variant
    return lazy { variant.value.impl }
  }

  /** `hook -> chosen variant` for every registered hook (forces resolution). */
  fun describe(): Map<String, String> = resolved.mapValues { (_, v) -> v.value.label }
}
