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

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Exercises [ElfPatcher] against synthetic ELF64 files.
 *
 * Synthetic rather than a real `.so` because layoutlib's is 26 MB, which has no business in a
 * repository. [ElfWriter] emits the same structures the patcher reads on Linux: a `.dynamic`
 * section of `Elf64_Dyn` entries whose `DT_SONAME` / `DT_NEEDED` values index a packed `.dynstr`.
 */
class ElfPatcherTest {
  @get:Rule
  val temporaryFolder: TemporaryFolder = TemporaryFolder()

  @Test
  fun readsSonameAndNeeded() {
    val library = elf(soname = "libandroid_runtime.so", needed = listOf("libc.so.6", "libm.so.6"))

    assertThat(ElfPatcher.readSoname(library)).isEqualTo("libandroid_runtime.so")
    assertThat(ElfPatcher.readNeeded(library)).containsExactly("libc.so.6", "libm.so.6").inOrder()
  }

  @Test
  fun rewritesSonameInPlace() {
    val library = elf(soname = "libandroid_runtime.so", needed = listOf("libc.so.6"))

    val count = ElfPatcher.replaceDynamicString(library, "libandroid_runtime.so", "libandroid_runt0a1.so")

    assertThat(count).isEqualTo(1)
    assertThat(ElfPatcher.readSoname(library)).isEqualTo("libandroid_runt0a1.so")
    // Neighbouring strings in the packed table must be untouched.
    assertThat(ElfPatcher.readNeeded(library)).containsExactly("libc.so.6")
  }

  @Test
  fun rewritesNeededSoDependentsFollowTheRename() {
    val library = elf(soname = "layoutlib_jni.so", needed = listOf("libandroid_runtime.so", "libc.so.6"))

    ElfPatcher.replaceDynamicString(library, "libandroid_runtime.so", "libandroid_runt0a1.so")

    assertThat(ElfPatcher.readNeeded(library))
      .containsExactly("libandroid_runt0a1.so", "libc.so.6").inOrder()
    assertThat(ElfPatcher.readSoname(library)).isEqualTo("layoutlib_jni.so")
  }

  @Test
  fun rewritingIsIdempotentWhenNothingMatches() {
    val library = elf(soname = "layoutlib_jni.so", needed = listOf("libc.so.6"))
    val before = library.readBytes()

    val count = ElfPatcher.replaceDynamicString(library, "libandroid_runtime.so", "libandroid_runt0a1.so")

    assertThat(count).isEqualTo(0)
    assertThat(library.readBytes()).isEqualTo(before)
  }

  @Test
  fun refusesToGrowAString() {
    val library = elf(soname = "libandroid_runtime.so", needed = emptyList())

    try {
      ElfPatcher.replaceDynamicString(library, "libandroid_runtime.so", "libandroid_runtime_longer.so")
      throw AssertionError("Expected IOException")
    } catch (expected: IOException) {
      assertThat(expected).hasMessageThat().contains("same length")
    }
  }

  @Test
  fun refusesToRewriteAStringWhoseSuffixIsShared() {
    // String tables legitimately overlap suffixes: an entry may point into the middle of another
    // string. Overwriting in place would corrupt the shorter name.
    val library = elfWithSharedSuffix()

    try {
      ElfPatcher.replaceDynamicString(library, "libandroid_runtime.so", "libandroid_runt0a1.so")
      throw AssertionError("Expected IOException")
    } catch (expected: IOException) {
      assertThat(expected).hasMessageThat().contains("shares its suffix")
    }
  }

  @Test
  fun rejectsNonElfInput() {
    val notElf = temporaryFolder.newFile("nope.so").apply { writeBytes(ByteArray(128)) }

    try {
      ElfPatcher.readSoname(notElf)
      throw AssertionError("Expected IOException")
    } catch (expected: IOException) {
      assertThat(expected).hasMessageThat().contains("Not an ELF file")
    }
  }

  @Test
  fun rejects32BitElf() {
    val library = elf(soname = "lib.so", needed = emptyList())
    val bytes = library.readBytes().also { it[4] = 1 } // ELFCLASS32
    library.writeBytes(bytes)

    try {
      ElfPatcher.readSoname(library)
      throw AssertionError("Expected IOException")
    } catch (expected: IOException) {
      assertThat(expected).hasMessageThat().contains("64-bit little-endian")
    }
  }

  private fun elf(soname: String, needed: List<String>): File =
    temporaryFolder.newFile("${soname.substringBefore('.')}-${counter++}.so")
      .apply { writeBytes(ElfWriter.build(soname, needed)) }

  /** A `.dynstr` where `runtime.so` is a suffix of `libandroid_runtime.so`, and both are indexed. */
  private fun elfWithSharedSuffix(): File =
    temporaryFolder.newFile("shared-${counter++}.so")
      .apply { writeBytes(ElfWriter.buildWithSharedSuffix()) }

  private companion object {
    var counter = 0
  }
}

/** Emits just enough valid ELF64 little-endian structure for [ElfPatcher] to parse. */
private object ElfWriter {
  private const val SHT_STRTAB = 3
  private const val SHT_DYNAMIC = 6
  private const val DT_NEEDED = 1L
  private const val DT_SONAME = 14L
  private const val DT_NULL = 0L

  fun build(soname: String, needed: List<String>): ByteArray {
    val strings = StringTable()
    val entries = needed.map { DT_NEEDED to strings.add(it) } + listOf(DT_SONAME to strings.add(soname))
    return assemble(entries, strings.bytes())
  }

  fun buildWithSharedSuffix(): ByteArray {
    // "libandroid_runtime.so\0" with a second entry pointing at "runtime.so" inside it.
    val full = "libandroid_runtime.so"
    val table = StringTable()
    val fullOffset = table.add(full)
    val suffixOffset = fullOffset + (full.length - "runtime.so".length)
    return assemble(
      listOf(DT_SONAME to fullOffset, DT_NEEDED to suffixOffset.toLong()),
      table.bytes()
    )
  }

  private fun assemble(entries: List<Pair<Long, Long>>, stringTable: ByteArray): ByteArray {
    val headerSize = 64
    val dynamicSize = (entries.size + 1) * 16
    val dynamicOffset = headerSize.toLong()
    val stringsOffset = dynamicOffset + dynamicSize
    val sectionHeaderOffset = stringsOffset + stringTable.size

    val total = (sectionHeaderOffset + 3 * 64).toInt()
    val buffer = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)

    buffer.put(byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte()))
    buffer.put(2) // ELFCLASS64
    buffer.put(1) // ELFDATA2LSB
    buffer.position(0x28)
    buffer.putLong(sectionHeaderOffset)
    buffer.position(0x3A)
    buffer.putShort(64) // e_shentsize
    buffer.putShort(3) // e_shnum
    buffer.putShort(0) // e_shstrndx

    buffer.position(dynamicOffset.toInt())
    for ((tag, value) in entries) {
      buffer.putLong(tag)
      buffer.putLong(value)
    }
    buffer.putLong(DT_NULL)
    buffer.putLong(0)

    buffer.position(stringsOffset.toInt())
    buffer.put(stringTable)

    // Section 0 is the conventional null entry; 1 is .dynamic, linked to 2, which is .dynstr.
    section(buffer, sectionHeaderOffset.toInt(), type = 0, offset = 0, size = 0, link = 0, entrySize = 0)
    section(
      buffer, sectionHeaderOffset.toInt() + 64, type = SHT_DYNAMIC,
      offset = dynamicOffset, size = dynamicSize.toLong(), link = 2, entrySize = 16
    )
    section(
      buffer, sectionHeaderOffset.toInt() + 128, type = SHT_STRTAB,
      offset = stringsOffset, size = stringTable.size.toLong(), link = 0, entrySize = 0
    )
    return buffer.array()
  }

  private fun section(buffer: ByteBuffer, base: Int, type: Int, offset: Long, size: Long, link: Int, entrySize: Long) {
    buffer.putInt(base, 0) // sh_name
    buffer.putInt(base + 4, type)
    buffer.putLong(base + 8, 0) // sh_flags
    buffer.putLong(base + 16, 0) // sh_addr
    buffer.putLong(base + 24, offset)
    buffer.putLong(base + 32, size)
    buffer.putInt(base + 40, link)
    buffer.putInt(base + 44, 0) // sh_info
    buffer.putLong(base + 48, 1) // sh_addralign
    buffer.putLong(base + 56, entrySize)
  }

  private class StringTable {
    private val out = StringBuilder("\u0000")

    fun add(value: String): Long {
      val offset = out.length.toLong()
      out.append(value).append('\u0000')
      return offset
    }

    fun bytes(): ByteArray = out.toString().toByteArray(Charsets.UTF_8)
  }
}
