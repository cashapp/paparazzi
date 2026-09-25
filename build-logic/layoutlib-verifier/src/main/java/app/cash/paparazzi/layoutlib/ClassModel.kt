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

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File
import java.util.zip.ZipFile

/** A class or member reference taken from a class file's constant pool. */
internal sealed interface Ref : Comparable<Ref> {
  val owner: String

  data class Type(override val owner: String) : Ref {
    override fun toString() = owner.replace('/', '.')
  }

  data class Member(val kind: Kind, override val owner: String, val name: String, val descriptor: String) : Ref {
    override fun toString() = "${owner.replace('/', '.')}#$name$descriptor"
  }

  enum class Kind { FIELD, METHOD }

  override fun compareTo(other: Ref): Int = toString().compareTo(other.toString())
}

internal data class ClassDecl(
  val superName: String?,
  val interfaces: List<String>,
  val members: Set<Pair<Ref.Kind, String>>
)

/** Declared classes of one or more jars, used to resolve [Ref]s. */
internal class ClassModel(val classes: Map<String, ClassDecl>) {
  operator fun contains(className: String) = className in classes

  operator fun plus(other: ClassModel) = ClassModel(classes + other.classes)

  /** References in [refs] that don't resolve against this model. */
  fun missing(refs: Collection<Ref>): List<Ref> =
    refs.mapNotNull { ref ->
      when {
        ref.owner !in classes -> Ref.Type(ref.owner)
        ref is Ref.Member && !resolves(ref.owner, ref) -> ref
        else -> null
      }
    }.distinct().sorted()

  private fun resolves(className: String, ref: Ref.Member): Boolean {
    val decl = classes[className] ?: return false
    if ((ref.kind to ref.name + ref.descriptor) in decl.members) return true
    return (listOfNotNull(decl.superName) + decl.interfaces).any { parent ->
      if (parent in classes) {
        resolves(parent, ref)
      } else {
        // Only trust JDK supertypes for members they actually provide (Object, Enum, AutoCloseable...).
        JDK_PACKAGES.any(parent::startsWith) && ref.name in JDK_INHERITED
      }
    }
  }

  companion object {
    private val JDK_PACKAGES = listOf("java/", "javax/", "jdk/", "sun/")
    private val JDK_INHERITED = setOf(
      "<init>", "equals", "hashCode", "toString", "getClass", "clone", "finalize", "notify", "notifyAll", "wait",
      "name", "ordinal", "compareTo", "getDeclaringClass", "valueOf", "describeConstable",
      "close", "iterator", "forEach", "spliterator"
    )

    fun of(vararg jars: File, include: (String) -> Boolean = { true }): ClassModel {
      val classes = mutableMapOf<String, ClassDecl>()
      jars.forEach { jar ->
        jar.forEachClass { entry, bytes ->
          if (!include(entry)) return@forEachClass
          val collector = DeclarationCollector()
          ClassReader(bytes).accept(
            collector,
            ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES
          )
          classes[collector.name] = ClassDecl(collector.superName, collector.interfaces, collector.members)
        }
      }
      return ClassModel(classes)
    }
  }
}

/** Class and member references in [jar] whose owner satisfies [ownerFilter]. */
internal fun references(
  jar: File,
  ownerFilter: (String) -> Boolean,
  skipEntries: (String) -> Boolean = { false }
): Set<Ref> {
  val refs = mutableSetOf<Ref>()
  jar.forEachClass { entry, bytes ->
    if (skipEntries(entry)) return@forEachClass
    val reader = ClassReader(bytes)
    val buffer = CharArray(reader.maxStringLength)
    for (i in 1 until reader.itemCount) {
      val offset = reader.getItem(i)
      if (offset == 0) continue // second slot of a long/double
      when (reader.readByte(offset - 1)) {
        CONSTANT_CLASS -> {
          val name = reader.readUTF8(offset, buffer).trimStart('[').let {
            if (it.startsWith("L")) {
              it.substring(
                1,
                it.length - 1
              )
            } else {
              it
            }
          }
          if (ownerFilter(name)) refs += Ref.Type(name)
        }
        CONSTANT_FIELDREF, CONSTANT_METHODREF, CONSTANT_INTERFACE_METHODREF -> {
          val owner = reader.readClass(offset, buffer)
          if (!ownerFilter(owner)) continue
          val nameAndType = reader.getItem(reader.readUnsignedShort(offset + 2))
          val kind = if (reader.readByte(offset - 1) == CONSTANT_FIELDREF) Ref.Kind.FIELD else Ref.Kind.METHOD
          refs +=
            Ref.Member(kind, owner, reader.readUTF8(nameAndType, buffer), reader.readUTF8(nameAndType + 2, buffer))
        }
      }
    }
  }
  return refs
}

private fun File.forEachClass(action: (entry: String, bytes: ByteArray) -> Unit) {
  ZipFile(this).use { zip ->
    zip.entries().asSequence()
      .filter { !it.isDirectory && it.name.endsWith(".class") && !it.name.startsWith("META-INF/") }
      .forEach { entry -> action(entry.name, zip.getInputStream(entry).use { it.readBytes() }) }
  }
}

private class DeclarationCollector : ClassVisitor(Opcodes.ASM9) {
  lateinit var name: String
  var superName: String? = null
  var interfaces: List<String> = emptyList()
  val members = mutableSetOf<Pair<Ref.Kind, String>>()

  override fun visit(
    version: Int,
    access: Int,
    name: String,
    signature: String?,
    superName: String?,
    interfaces: Array<out String>?
  ) {
    this.name = name
    this.superName = superName
    this.interfaces = interfaces?.toList().orEmpty()
  }

  override fun visitField(
    access: Int,
    name: String,
    descriptor: String,
    signature: String?,
    value: Any?
  ): FieldVisitor? {
    members += Ref.Kind.FIELD to name + descriptor
    return null
  }

  override fun visitMethod(
    access: Int,
    name: String,
    descriptor: String,
    signature: String?,
    exceptions: Array<out String>?
  ): MethodVisitor? {
    members += Ref.Kind.METHOD to name + descriptor
    return null
  }
}

private const val CONSTANT_CLASS = 7
private const val CONSTANT_FIELDREF = 9
private const val CONSTANT_METHODREF = 10
private const val CONSTANT_INTERFACE_METHODREF = 11
