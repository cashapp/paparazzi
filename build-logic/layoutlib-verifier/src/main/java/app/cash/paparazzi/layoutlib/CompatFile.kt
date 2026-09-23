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

import java.io.File

/** One verified layoutlib version in `gradle/layoutlib-compat.properties`. */
internal data class CompatEntry(val icu: String, val sdk: Int, val minLayoutlibApi: String? = null)

/**
 * Reads and writes `gradle/layoutlib-compat.properties`, preserving its leading comment block and
 * keeping entries sorted by [VersionOrder].
 */
internal class CompatFile(private val header: List<String>, val entries: Map<String, CompatEntry>) {
  fun with(version: String, entry: CompatEntry) = CompatFile(header, entries + (version to entry))

  fun render(): String =
    buildString {
      header.dropLastWhile(String::isBlank).forEach { appendLine(it) }
      appendLine()
      entries.keys.sortedWith(VersionOrder).forEach { version ->
        val entry = entries.getValue(version)
        appendLine("$version.$ICU=${entry.icu}")
        appendLine("$version.$SDK=${entry.sdk}")
        entry.minLayoutlibApi?.let { appendLine("$version.$MIN_API=$it") }
      }
    }

  fun writeTo(file: File) = file.writeText(render())

  companion object {
    private const val ICU = "icu"
    private const val SDK = "sdk"
    private const val MIN_API = "minLayoutlibApi"
    private val ENTRY = Regex("""^(.+)\.($ICU|$SDK|$MIN_API)=(.*)$""")

    fun parse(text: String): CompatFile {
      val lines = text.lines()
      val header = lines.takeWhile { !ENTRY.matches(it) }
      val values = mutableMapOf<String, MutableMap<String, String>>()
      lines.drop(header.size).forEach { line ->
        if (line.isBlank() || line.startsWith("#")) return@forEach
        val match = ENTRY.matchEntire(line) ?: throw VerificationException("unrecognized line in compat file: $line")
        val (version, key, value) = match.destructured
        values.getOrPut(version) { mutableMapOf() }[key] = value
      }
      val entries = values.mapValues { (version, keys) ->
        CompatEntry(
          icu = keys[ICU] ?: throw VerificationException("$version has no .$ICU entry"),
          sdk = keys[SDK]?.toIntOrNull() ?: throw VerificationException("$version has no numeric .$SDK entry"),
          minLayoutlibApi = keys[MIN_API]
        )
      }
      return CompatFile(header, entries)
    }

    fun read(file: File) = parse(file.readText())
  }
}
