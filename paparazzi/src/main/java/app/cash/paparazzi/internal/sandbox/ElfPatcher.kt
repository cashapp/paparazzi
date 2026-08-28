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

import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal ELF64 reader/writer for making a staged shared library uniquely identifiable.
 *
 * ### Why not shell out to patchelf
 *
 * `patchelf` is the obvious tool, and it is the wrong dependency: it is absent from most CI images
 * and from stock distributions, so requiring it would turn "run your screenshot tests" into "and
 * also install this". Since the only edits needed here are same-length string replacements, the
 * hard part of `patchelf` — growing `.dynstr` and relocating everything after it — never arises.
 *
 * ### What it edits
 *
 * `DT_SONAME` and `DT_NEEDED` are offsets into `.dynstr`, a packed string table with no slack. A
 * longer name would require rewriting the whole file, so replacements must be the same length,
 * which is why [NativeLibraryStaging] substitutes trailing characters rather than appending.
 *
 * Only 64-bit little-endian ELF is supported, which covers every platform layoutlib ships a
 * `linux` classifier for. Anything else fails loudly rather than corrupting a library.
 */
internal object ElfPatcher {
  private const val EI_CLASS = 4
  private const val EI_DATA = 5
  private const val ELFCLASS64 = 2
  private const val ELFDATA2LSB = 1

  private const val SHT_DYNAMIC = 6
  private const val SHT_DYNSYM = 11

  private const val DT_NULL = 0L
  private const val DT_NEEDED = 1L
  private const val DT_SONAME = 14L

  private class Section(
    val type: Int,
    val offset: Long,
    val size: Long,
    val link: Int,
    val entrySize: Long
  )

  /** A `DT_SONAME` or `DT_NEEDED` entry and the string it points at. */
  data class DynamicString(val tag: Long, val stringFileOffset: Long, val value: String)

  /** Reads the `DT_SONAME` of [file], or null if it declares none. */
  fun readSoname(file: File): String? = dynamicStrings(file.readBytes()).firstOrNull { it.tag == DT_SONAME }?.value

  /** Reads every `DT_NEEDED` of [file]. */
  fun readNeeded(file: File): List<String> =
    dynamicStrings(file.readBytes()).filter { it.tag == DT_NEEDED }.map { it.value }

  /**
   * Replaces occurrences of [from] with [to] in [file]'s `DT_SONAME` and `DT_NEEDED` entries.
   *
   * @return the number of entries rewritten.
   * @throws IOException if the replacement is not the same length, or if the string being replaced
   *   is shared with another dynamic entry or a dynamic symbol name — string tables may overlap
   *   suffixes, and blindly overwriting a shared string would corrupt an unrelated name.
   */
  fun replaceDynamicString(file: File, from: String, to: String): Int {
    if (from == to) return 0
    if (from.length != to.length) {
      throw IOException(
        "Cannot replace '$from' with '$to': .dynstr is packed, so a replacement must be the same " +
          "length. Growing it would mean relocating the rest of the file."
      )
    }

    val bytes = file.readBytes()
    val targets = dynamicStrings(bytes).filter { it.value == from }
    if (targets.isEmpty()) return 0

    val protected = referencedStringOffsets(bytes)
    for (target in targets) {
      // Anything pointing *into* the middle of this string is sharing its suffix.
      val range = (target.stringFileOffset + 1) until (target.stringFileOffset + from.length)
      val overlap = protected.firstOrNull { it in range }
      if (overlap != null) {
        throw IOException(
          "Refusing to rewrite '$from' in ${file.name}: another name shares its suffix at file " +
            "offset $overlap, so overwriting in place would corrupt it."
        )
      }
    }

    val replacement = to.toByteArray(Charsets.UTF_8)
    for (target in targets) {
      replacement.copyInto(bytes, target.stringFileOffset.toInt())
    }
    file.writeBytes(bytes)
    return targets.size
  }

  /** Every `DT_SONAME` / `DT_NEEDED` entry, resolved against `.dynstr`. */
  private fun dynamicStrings(bytes: ByteArray): List<DynamicString> {
    val buffer = validated(bytes)
    val sections = sections(buffer)
    val result = mutableListOf<DynamicString>()

    for (dynamic in sections.filter { it.type == SHT_DYNAMIC }) {
      val strings = sections.getOrNull(dynamic.link) ?: continue
      val entrySize = if (dynamic.entrySize > 0) dynamic.entrySize else 16
      var offset = dynamic.offset
      val end = dynamic.offset + dynamic.size

      while (offset + entrySize <= end) {
        val tag = buffer.getLong(offset.toInt())
        if (tag == DT_NULL) break
        if (tag == DT_SONAME || tag == DT_NEEDED) {
          val stringOffset = strings.offset + buffer.getLong((offset + 8).toInt())
          result += DynamicString(tag, stringOffset, readCString(bytes, stringOffset.toInt()))
        }
        offset += entrySize
      }
    }
    return result
  }

  /**
   * File offsets of every string referenced by a dynamic entry or a dynamic symbol.
   *
   * Used to prove that a string is not shared before overwriting it.
   */
  private fun referencedStringOffsets(bytes: ByteArray): Set<Long> {
    val buffer = validated(bytes)
    val sections = sections(buffer)
    val offsets = mutableSetOf<Long>()

    for (dynamic in sections.filter { it.type == SHT_DYNAMIC }) {
      val strings = sections.getOrNull(dynamic.link) ?: continue
      val entrySize = if (dynamic.entrySize > 0) dynamic.entrySize else 16
      var offset = dynamic.offset
      val end = dynamic.offset + dynamic.size
      while (offset + entrySize <= end) {
        val tag = buffer.getLong(offset.toInt())
        if (tag == DT_NULL) break
        offsets += strings.offset + buffer.getLong((offset + 8).toInt())
        offset += entrySize
      }
    }

    for (symbols in sections.filter { it.type == SHT_DYNSYM }) {
      val strings = sections.getOrNull(symbols.link) ?: continue
      val entrySize = if (symbols.entrySize > 0) symbols.entrySize else 24
      var offset = symbols.offset
      val end = symbols.offset + symbols.size
      while (offset + entrySize <= end) {
        offsets += strings.offset + (buffer.getInt(offset.toInt()).toLong() and 0xFFFFFFFFL)
        offset += entrySize
      }
    }
    return offsets
  }

  private fun sections(buffer: ByteBuffer): List<Section> {
    val sectionHeaderOffset = buffer.getLong(0x28)
    val entrySize = buffer.getShort(0x3A).toInt() and 0xFFFF
    val count = buffer.getShort(0x3C).toInt() and 0xFFFF

    return (0 until count).map { index ->
      val base = (sectionHeaderOffset + index.toLong() * entrySize).toInt()
      Section(
        type = buffer.getInt(base + 4),
        offset = buffer.getLong(base + 24),
        size = buffer.getLong(base + 32),
        link = buffer.getInt(base + 40),
        entrySize = buffer.getLong(base + 56)
      )
    }
  }

  private fun validated(bytes: ByteArray): ByteBuffer {
    if (bytes.size < 64 ||
      bytes[0] != 0x7F.toByte() || bytes[1] != 'E'.code.toByte() ||
      bytes[2] != 'L'.code.toByte() || bytes[3] != 'F'.code.toByte()
    ) {
      throw IOException("Not an ELF file.")
    }
    if (bytes[EI_CLASS].toInt() != ELFCLASS64 || bytes[EI_DATA].toInt() != ELFDATA2LSB) {
      throw IOException(
        "Only 64-bit little-endian ELF is supported (class=${bytes[EI_CLASS]}, data=${bytes[EI_DATA]})."
      )
    }
    return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
  }

  private fun readCString(bytes: ByteArray, offset: Int): String {
    var end = offset
    while (end < bytes.size && bytes[end] != 0.toByte()) end++
    return String(bytes, offset, end - offset, Charsets.UTF_8)
  }
}
