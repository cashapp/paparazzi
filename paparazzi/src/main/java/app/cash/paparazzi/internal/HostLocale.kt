/*
 * Copyright (C) 2026 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package app.cash.paparazzi.internal

import java.util.IllformedLocaleException
import java.util.Locale

/**
 * The locale of the machine running the tests, for formatting messages read by developers.
 *
 * Code under test may call [Locale.setDefault] to render a localized UI, which would otherwise
 * leak into Paparazzi's own output, e.g. printing diff percentages in Arabic-Indic digits.
 * [Locale.setDefault] does not modify the `user.*` system properties the JVM derived its startup
 * locale from, so those still describe the host.
 */
internal val hostLocale: Locale by lazy {
  fun property(name: String): String = System.getProperty("$name.format") ?: System.getProperty(name).orEmpty()

  try {
    Locale.Builder()
      .setLanguage(property("user.language"))
      .setScript(property("user.script"))
      .setRegion(property("user.country"))
      .setVariant(property("user.variant"))
      .build()
  } catch (e: IllformedLocaleException) {
    Locale.ROOT
  }
}

/** Formats this string with [hostLocale], independent of the current default locale. */
internal fun String.formatForHost(vararg args: Any?): String = String.format(hostLocale, this, *args)
