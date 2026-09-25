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

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LayoutlibVerifierTest {
  @get:Rule
  val temp = TemporaryFolder()

  @Test
  fun versionOrder() {
    val sorted = listOf("17.0.1", "16.1.1", "16.1.0-jdk17", "16.1.0", "16.10.0", "16.2.4").sortedWith(VersionOrder)
    assertThat(sorted).containsExactly("16.1.0", "16.1.0-jdk17", "16.1.1", "16.2.4", "16.10.0", "17.0.1").inOrder()
    assertThat(VersionOrder.isStable("16.1.0")).isTrue()
    assertThat(VersionOrder.isStable("16.1.0-jdk17")).isFalse()
  }

  @Test
  fun compatFileRoundTripsAndSorts() {
    val text = """
      |# header
      |# more header
      |
      |17.0.1.icu=icudt78l.dat
      |17.0.1.sdk=37
      |17.0.1.minLayoutlibApi=32.3.0
      |16.2.3.icu=icudt76l.dat
      |16.2.3.sdk=36
      |
    """.trimMargin()
    val compat = CompatFile.parse(text)
    assertThat(compat.entries).containsExactly(
      "16.2.3", CompatEntry("icudt76l.dat", 36),
      "17.0.1", CompatEntry("icudt78l.dat", 37, "32.3.0")
    )
    assertThat(compat.with("16.1.0-jdk17", CompatEntry("icudt76l.dat", 36)).render()).isEqualTo(
      """
        |# header
        |# more header
        |
        |16.1.0-jdk17.icu=icudt76l.dat
        |16.1.0-jdk17.sdk=36
        |16.2.3.icu=icudt76l.dat
        |16.2.3.sdk=36
        |17.0.1.icu=icudt78l.dat
        |17.0.1.sdk=37
        |17.0.1.minLayoutlibApi=32.3.0
        |
      """.trimMargin()
    )
  }

  @Test
  fun compatFileRequiresSdk() {
    val error = runCatching { CompatFile.parse("16.2.3.icu=icudt76l.dat\n") }.exceptionOrNull()
    assertThat(error).isInstanceOf(VerificationException::class.java)
    assertThat(error).hasMessageThat().contains(".sdk")
  }

  @Test
  fun readsZipEntryFromCentralDirectoryAndLocalHeader() {
    val bytes = ByteArrayOutputStream().also { out ->
      ZipOutputStream(out).use { zip ->
        zip.putNextEntry(ZipEntry("data/other.bin"))
        zip.write(ByteArray(4096) { it.toByte() })
        zip.putNextEntry(ZipEntry("build.prop"))
        zip.write("ro.build.version.sdk=37\n".repeat(20).toByteArray())
      }
    }.toByteArray()
    val record = ZipRecords.centralDirectoryEntry(bytes, "build.prop")!!
    val local = bytes.copyOfRange(record.localHeaderOffset.toInt(), bytes.size)
    assertThat(String(ZipRecords.readLocalEntry(local, record))).isEqualTo("ro.build.version.sdk=37\n".repeat(20))
    assertThat(ZipRecords.centralDirectoryEntry(bytes, "missing")).isNull()
  }

  @Test
  fun missingMemberInheritedFromSeparateJarIsDetected() {
    // Mirrors layoutlib's Bridge extending layoutlib-api's Bridge: a removed method must not be
    // reported as "inherited" just because the chain eventually reaches java/lang/Object.
    val api = jar("api.jar", classFile("api/Bridge", superName = "java/lang/Object", methods = listOf("init" to "()V")))
    val oldImpl =
      jar("old.jar", classFile("impl/Bridge", superName = "api/Bridge", methods = listOf("prepareThread" to "()V")))
    val newImpl = jar("new.jar", classFile("impl/Bridge", superName = "api/Bridge"))

    val refs = listOf(
      Ref.Member(Ref.Kind.METHOD, "impl/Bridge", "prepareThread", "()V"),
      Ref.Member(Ref.Kind.METHOD, "impl/Bridge", "init", "()V"),
      Ref.Member(Ref.Kind.METHOD, "impl/Bridge", "hashCode", "()I")
    )
    assertThat((ClassModel.of(api) + ClassModel.of(oldImpl)).missing(refs)).isEmpty()
    assertThat((ClassModel.of(api) + ClassModel.of(newImpl)).missing(refs))
      .containsExactly(Ref.Member(Ref.Kind.METHOD, "impl/Bridge", "prepareThread", "()V"))
  }

  @Test
  fun missingClassIsDetected() {
    val model = ClassModel.of(jar("a.jar", classFile("a/Present")))
    assertThat(model.missing(listOf(Ref.Type("a/Present"), Ref.Type("a/Gone")))).containsExactly(Ref.Type("a/Gone"))
  }

  @Test
  fun referencesReadConstantPool() {
    val caller = classFile("p/Caller", methods = listOf("call" to "()V")) { cw ->
      val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "run", "()V", null, null)
      mv.visitCode()
      mv.visitMethodInsn(Opcodes.INVOKESTATIC, "lib/Api", "doThing", "(J)V", false)
      mv.visitFieldInsn(Opcodes.GETSTATIC, "lib/Api", "sTime", "J")
      mv.visitLdcInsn(123456789012L) // long constant occupies two constant-pool slots
      mv.visitInsn(Opcodes.POP2)
      mv.visitInsn(Opcodes.RETURN)
      mv.visitMaxs(0, 0)
      mv.visitEnd()
    }
    val refs = references(jar("caller.jar", caller), ownerFilter = { it.startsWith("lib/") })
    assertThat(refs).containsExactly(
      Ref.Type("lib/Api"),
      Ref.Member(Ref.Kind.METHOD, "lib/Api", "doThing", "(J)V"),
      Ref.Member(Ref.Kind.FIELD, "lib/Api", "sTime", "J")
    )
  }

  private fun classFile(
    name: String,
    superName: String = "java/lang/Object",
    methods: List<Pair<String, String>> = emptyList(),
    extra: (ClassWriter) -> Unit = {}
  ): Pair<String, ByteArray> {
    val cw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
    cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null, superName, null)
    methods.forEach { (method, desc) ->
      cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_ABSTRACT, method, desc, null, null).visitEnd()
    }
    extra(cw)
    cw.visitEnd()
    return "$name.class" to cw.toByteArray()
  }

  private fun jar(name: String, vararg entries: Pair<String, ByteArray>): File {
    val file = File(temp.root, name)
    ZipOutputStream(file.outputStream()).use { zip ->
      entries.forEach { (path, bytes) ->
        zip.putNextEntry(ZipEntry(path))
        zip.write(bytes)
        zip.closeEntry()
      }
    }
    return file
  }
}
