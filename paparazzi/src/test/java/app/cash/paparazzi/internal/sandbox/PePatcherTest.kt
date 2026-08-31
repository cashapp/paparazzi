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
 * Exercises [PePatcher] against synthetic PE32+ files.
 *
 * Synthetic rather than a real DLL because layoutlib's is 16 MB. [PeWriter] emits the structures
 * the patcher reads: an import directory whose descriptors name modules by RVA into `.rdata`, and
 * an export directory naming the library itself.
 */
class PePatcherTest {
  @get:Rule
  val temporaryFolder: TemporaryFolder = TemporaryFolder()

  @Test
  fun readsExportNameAndImports() {
    val dll = pe(exportName = "layoutlib_jni.dll", imports = listOf("libandroid_runtime.dll", "KERNEL32.dll"))

    assertThat(PePatcher.readExportName(dll)).isEqualTo("layoutlib_jni.dll")
    assertThat(PePatcher.readImportedModules(dll))
      .containsExactly("libandroid_runtime.dll", "KERNEL32.dll").inOrder()
  }

  @Test
  fun rewritesAnImportedModuleName() {
    val dll = pe(exportName = "layoutlib_jni.dll", imports = listOf("libandroid_runtime.dll", "KERNEL32.dll"))

    val count = PePatcher.replaceModuleName(dll, "libandroid_runtime.dll", "libandroid_run0a1z.dll")

    assertThat(count).isEqualTo(1)
    assertThat(PePatcher.readImportedModules(dll))
      .containsExactly("libandroid_run0a1z.dll", "KERNEL32.dll").inOrder()
    // Neighbouring strings in the packed table must be untouched.
    assertThat(PePatcher.readExportName(dll)).isEqualTo("layoutlib_jni.dll")
  }

  @Test
  fun rewritesTheLibrarysOwnExportName() {
    val dll = pe(exportName = "layoutlib_jni.dll", imports = listOf("KERNEL32.dll"))

    val count = PePatcher.replaceModuleName(dll, "layoutlib_jni.dll", "layoutlib0a1z.dll")

    assertThat(count).isEqualTo(1)
    assertThat(PePatcher.readExportName(dll)).isEqualTo("layoutlib0a1z.dll")
  }

  @Test
  fun rewritesBothWhenALibraryImportsItsOwnName() {
    // Not a real layoutlib shape, but the patcher must not stop at the first match.
    val dll = pe(exportName = "shared0000.dll", imports = listOf("shared0000.dll"))

    val count = PePatcher.replaceModuleName(dll, "shared0000.dll", "shared0a1z.dll")

    assertThat(count).isEqualTo(2)
    assertThat(PePatcher.readExportName(dll)).isEqualTo("shared0a1z.dll")
    assertThat(PePatcher.readImportedModules(dll)).containsExactly("shared0a1z.dll")
  }

  @Test
  fun rewritingIsANoOpWhenNothingMatches() {
    val dll = pe(exportName = "layoutlib_jni.dll", imports = listOf("KERNEL32.dll"))
    val before = dll.readBytes()

    val count = PePatcher.replaceModuleName(dll, "libandroid_runtime.dll", "libandroid_run0a1z.dll")

    assertThat(count).isEqualTo(0)
    assertThat(dll.readBytes()).isEqualTo(before)
  }

  @Test
  fun refusesToGrowAName() {
    val dll = pe(exportName = "layoutlib_jni.dll", imports = listOf("libandroid_runtime.dll"))

    try {
      PePatcher.replaceModuleName(dll, "libandroid_runtime.dll", "libandroid_runtime_longer.dll")
      throw AssertionError("Expected IOException")
    } catch (expected: IOException) {
      assertThat(expected).hasMessageThat().contains("same length")
    }
  }

  @Test
  fun rejectsNonPeInput() {
    val notPe = temporaryFolder.newFile("nope.dll").apply { writeBytes(ByteArray(256)) }

    try {
      PePatcher.readExportName(notPe)
      throw AssertionError("Expected IOException")
    } catch (expected: IOException) {
      assertThat(expected).hasMessageThat().contains("MZ signature")
    }
  }

  @Test
  fun rejectsTruncatedPeHeader() {
    val dll = pe(exportName = "a.dll", imports = emptyList())
    val bytes = dll.readBytes()
    // Point e_lfanew somewhere with no PE signature.
    ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(0x3C, 0x10)
    dll.writeBytes(bytes)

    try {
      PePatcher.readExportName(dll)
      throw AssertionError("Expected IOException")
    } catch (expected: IOException) {
      assertThat(expected).hasMessageThat().contains("PE signature")
    }
  }

  private fun pe(exportName: String, imports: List<String>): File =
    temporaryFolder.newFile("pe-${counter++}.dll")
      .apply { writeBytes(PeWriter.build(exportName, imports)) }

  private companion object {
    var counter = 0
  }
}

/** Emits just enough valid PE32+ structure for [PePatcher] to parse. */
private object PeWriter {
  fun build(exportName: String, imports: List<String>): ByteArray {
    val headerSize = 0x200
    val sectionRawPointer = headerSize
    val sectionVirtualAddress = 0x1000

    // .rdata layout: import descriptors, export directory, then the packed name strings.
    val importTableSize = (imports.size + 1) * 20
    val exportDirectorySize = 40
    val stringsStart = importTableSize + exportDirectorySize

    val strings = StringBuilder()
    val offsets = mutableMapOf<String, Int>()
    for (name in imports + exportName) {
      if (name in offsets) continue
      offsets[name] = stringsStart + strings.length
      strings.append(name).append('\u0000')
    }

    val sectionSize = stringsStart + strings.length
    val body = ByteBuffer.allocate(sectionSize).order(ByteOrder.LITTLE_ENDIAN)

    fun rva(sectionOffset: Int) = sectionVirtualAddress + sectionOffset

    imports.forEachIndexed { index, name ->
      val base = index * 20
      body.putInt(base, 0) // OriginalFirstThunk
      body.putInt(base + 12, rva(offsets.getValue(name))) // Name
    }
    // Null descriptor terminates the array.

    val exportBase = importTableSize
    body.putInt(exportBase + 12, rva(offsets.getValue(exportName))) // Name

    body.position(stringsStart)
    body.put(strings.toString().toByteArray(Charsets.US_ASCII))

    val total = headerSize + sectionSize
    val out = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)
    out.put('M'.code.toByte()).put('Z'.code.toByte())
    out.putInt(0x3C, 0x80)

    val pe = 0x80
    out.putInt(pe, 0x00004550) // "PE\0\0"
    val coff = pe + 4
    out.putShort(coff, 0x8664.toShort()) // Machine
    out.putShort(coff + 2, 1) // NumberOfSections
    val optionalSize = 112 + 16 * 8
    out.putShort(coff + 16, optionalSize.toShort())

    val opt = coff + 20
    out.putShort(opt, 0x20b) // PE32+

    val dd = opt + 112
    out.putInt(dd, rva(exportBase)) // Export table
    out.putInt(dd + 4, exportDirectorySize)
    out.putInt(dd + 8, rva(0)) // Import table
    out.putInt(dd + 12, importTableSize)

    val sectionTable = opt + optionalSize
    out.position(sectionTable)
    out.put(".rdata\u0000\u0000".toByteArray(Charsets.US_ASCII))
    out.putInt(sectionTable + 8, sectionSize) // VirtualSize
    out.putInt(sectionTable + 12, sectionVirtualAddress)
    out.putInt(sectionTable + 16, sectionSize) // SizeOfRawData
    out.putInt(sectionTable + 20, sectionRawPointer)

    out.position(headerSize)
    out.put(body.array())
    return out.array()
  }
}
