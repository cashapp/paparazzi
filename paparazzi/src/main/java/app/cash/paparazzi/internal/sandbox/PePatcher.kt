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
 * Minimal PE reader/writer for making a staged DLL uniquely identifiable.
 *
 * ### Why Windows needs this at all
 *
 * Microsoft documents the loader's first rule plainly: *"If a DLL with the same module name is
 * already loaded in memory, the system uses the loaded DLL, no matter which directory it is in."*
 * Copying layoutlib's DLLs into a per-sandbox directory therefore achieves nothing on its own — a
 * second `layoutlib_jni.dll` binds to whichever `libandroid_runtime.dll` is already in the process,
 * both copies drive the same native state, and layoutlib aborts part-way through re-initialising
 * it. Uniqueness has to come from the module *name*.
 *
 * ### Why this is simpler than the other two platforms
 *
 * Mach-O needed install names rewritten and the image re-signed; ELF needed `DT_SONAME` and
 * `DT_NEEDED` rewritten within a packed string table. Windows needs neither: layoutlib's DLLs carry
 * no Authenticode signature and a zero `CheckSum` field, so nothing has to be recomputed or
 * re-signed after an edit. Only two strings matter per library — the name in a dependent's import
 * descriptor, and the library's own export-directory name.
 *
 * As with ELF, replacements must be the same length. These strings sit in `.rdata` with no slack,
 * and growing one would mean relocating everything after it.
 *
 * Renaming is safe here in a way it is not on macOS. Static analysis of layoutlib 16.x shows the
 * only occurrences of either DLL name are the import descriptor and each library's own export name;
 * neither is passed to `LoadLibrary` at runtime. The Mach-O binaries, by contrast, import
 * `dlopen`/`dlsym` and break when their files are renamed.
 */
internal object PePatcher {
  private const val PE32 = 0x10b
  private const val PE32_PLUS = 0x20b

  private const val DIR_EXPORT = 0
  private const val DIR_IMPORT = 1
  private const val DIR_DELAY_IMPORT = 13

  private const val IMPORT_DESCRIPTOR_SIZE = 20
  private const val IMPORT_DESCRIPTOR_NAME_OFFSET = 12
  private const val DELAY_DESCRIPTOR_SIZE = 32
  private const val DELAY_DESCRIPTOR_NAME_OFFSET = 4
  private const val EXPORT_DIRECTORY_NAME_OFFSET = 12

  private class Layout(
    val buffer: ByteBuffer,
    val dataDirectoryOffset: Int,
    val sections: List<Section>
  ) {
    fun directory(index: Int): Pair<Int, Int> {
      val base = dataDirectoryOffset + index * 8
      return buffer.getInt(base) to buffer.getInt(base + 4)
    }

    /** Translates a relative virtual address to a file offset, or null if it lands outside. */
    fun fileOffset(rva: Int): Int? {
      val section = sections.firstOrNull {
        rva >= it.virtualAddress && rva < it.virtualAddress + maxOf(it.virtualSize, it.rawSize)
      } ?: return null
      return section.rawPointer + (rva - section.virtualAddress)
    }
  }

  private class Section(
    val virtualAddress: Int,
    val virtualSize: Int,
    val rawPointer: Int,
    val rawSize: Int
  )

  /** The name a DLL declares for itself in its export directory, or null if it exports nothing. */
  fun readExportName(file: File): String? {
    val bytes = file.readBytes()
    val layout = parse(bytes)
    val (rva, _) = layout.directory(DIR_EXPORT)
    if (rva == 0) return null
    val offset = layout.fileOffset(rva) ?: return null
    val nameRva = layout.buffer.getInt(offset + EXPORT_DIRECTORY_NAME_OFFSET)
    if (nameRva == 0) return null
    return readCString(bytes, layout.fileOffset(nameRva) ?: return null)
  }

  /** Every module this DLL imports, statically and via delay-load. */
  fun readImportedModules(file: File): List<String> {
    val bytes = file.readBytes()
    return moduleNameOffsets(bytes, parse(bytes)).map { readCString(bytes, it) }
  }

  /**
   * Replaces [from] with [to] wherever this DLL names a module — its imports, its delay imports, and
   * its own export-directory name.
   *
   * @return the number of strings rewritten.
   * @throws IOException if the replacement is a different length, or if the string is shared with
   *   another module name, which would corrupt it.
   */
  fun replaceModuleName(file: File, from: String, to: String): Int {
    if (from == to) return 0
    if (from.length != to.length) {
      throw IOException(
        "Cannot replace '$from' with '$to': PE module name strings are packed into .rdata, so a " +
          "replacement must be the same length. Growing one would mean relocating the rest of the " +
          "image."
      )
    }

    val bytes = file.readBytes()
    val layout = parse(bytes)

    val allOffsets = moduleNameOffsets(bytes, layout) + listOfNotNull(exportNameOffset(bytes, layout))
    val targets = allOffsets.filter { readCString(bytes, it) == from }
    if (targets.isEmpty()) return 0

    for (target in targets) {
      // Anything pointing into the middle of this string is sharing its tail.
      val interior = (target + 1) until (target + from.length)
      val overlap = allOffsets.firstOrNull { it != target && it in interior }
      if (overlap != null) {
        throw IOException(
          "Refusing to rewrite '$from' in ${file.name}: another module name shares its suffix at " +
            "file offset $overlap, so overwriting in place would corrupt it."
        )
      }
    }

    val replacement = to.toByteArray(Charsets.US_ASCII)
    for (target in targets) replacement.copyInto(bytes, target)
    file.writeBytes(bytes)
    return targets.size
  }

  private fun exportNameOffset(bytes: ByteArray, layout: Layout): Int? {
    val (rva, _) = layout.directory(DIR_EXPORT)
    if (rva == 0) return null
    val offset = layout.fileOffset(rva) ?: return null
    val nameRva = layout.buffer.getInt(offset + EXPORT_DIRECTORY_NAME_OFFSET)
    if (nameRva == 0) return null
    return layout.fileOffset(nameRva)
  }

  /** File offsets of the module-name string in every import and delay-import descriptor. */
  private fun moduleNameOffsets(bytes: ByteArray, layout: Layout): List<Int> {
    val offsets = mutableListOf<Int>()

    for ((directory, descriptorSize, nameOffset) in listOf(
      Triple(DIR_IMPORT, IMPORT_DESCRIPTOR_SIZE, IMPORT_DESCRIPTOR_NAME_OFFSET),
      Triple(DIR_DELAY_IMPORT, DELAY_DESCRIPTOR_SIZE, DELAY_DESCRIPTOR_NAME_OFFSET)
    )) {
      val (rva, _) = layout.directory(directory)
      if (rva == 0) continue
      var cursor = layout.fileOffset(rva) ?: continue

      while (cursor + descriptorSize <= bytes.size) {
        val nameRva = layout.buffer.getInt(cursor + nameOffset)
        if (nameRva == 0) break // The descriptor array is null-terminated.
        layout.fileOffset(nameRva)?.let(offsets::add)
        cursor += descriptorSize
      }
    }
    return offsets
  }

  private fun parse(bytes: ByteArray): Layout {
    if (bytes.size < 0x40 || bytes[0] != 'M'.code.toByte() || bytes[1] != 'Z'.code.toByte()) {
      throw IOException("Not a PE file: missing MZ signature.")
    }
    // PE is always little-endian on the architectures layoutlib ships for.
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

    val peHeader = buffer.getInt(0x3C)
    if (peHeader <= 0 || peHeader + 24 > bytes.size ||
      buffer.getInt(peHeader) != 0x00004550 // "PE\0\0"
    ) {
      throw IOException("Not a PE file: missing PE signature.")
    }

    val coff = peHeader + 4
    val sectionCount = buffer.getShort(coff + 2).toInt() and 0xFFFF
    val optionalHeaderSize = buffer.getShort(coff + 16).toInt() and 0xFFFF
    val optionalHeader = coff + 20

    val magic = buffer.getShort(optionalHeader).toInt() and 0xFFFF
    val dataDirectoryOffset = when (magic) {
      PE32 -> optionalHeader + 96
      PE32_PLUS -> optionalHeader + 112
      else -> throw IOException("Unsupported PE optional header magic 0x${magic.toString(16)}.")
    }

    val sectionTable = optionalHeader + optionalHeaderSize
    val sections = (0 until sectionCount).map { index ->
      val base = sectionTable + index * 40
      Section(
        virtualSize = buffer.getInt(base + 8),
        virtualAddress = buffer.getInt(base + 12),
        rawSize = buffer.getInt(base + 16),
        rawPointer = buffer.getInt(base + 20)
      )
    }

    return Layout(buffer, dataDirectoryOffset, sections)
  }

  private fun readCString(bytes: ByteArray, offset: Int): String {
    var end = offset
    while (end < bytes.size && bytes[end] != 0.toByte()) end++
    return String(bytes, offset, end - offset, Charsets.US_ASCII)
  }
}
