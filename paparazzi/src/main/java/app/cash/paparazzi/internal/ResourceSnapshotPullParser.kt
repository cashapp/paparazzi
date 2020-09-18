/*
 * Copyright (C) 2020 Square, Inc.
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

import app.cash.paparazzi.internal.ResourceSnapshot.TagSnapshot
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.io.Reader

internal open class ResourceSnapshotPullParser(val resourceSnapshot: ResourceSnapshot) :
    XmlPullParser {

  private val nextCommands = mutableListOf<NextCommand>()

  private var currentDepth: Int = 0
  private var currentTag: TagSnapshot? = null

  init {
    nextCommands.add(NextCommand(XmlPullParser.START_DOCUMENT, null, 0))
    initializeCommands(resourceSnapshot.rootSnapshot, 0)
    nextCommands.add(NextCommand(XmlPullParser.END_DOCUMENT, null, 0))
  }

  private fun initializeCommands(
    snapshot: TagSnapshot,
    depth: Int
  ) {
    nextCommands.add(NextCommand(XmlPullParser.START_TAG, snapshot, depth + 1))
    snapshot.children.forEach {
      initializeCommands(it, depth + 1)
    }
    nextCommands.add(NextCommand(XmlPullParser.END_TAG, snapshot, depth + 1))
  }

  override fun getName(): String {
    return currentTag!!.name
  }

  override fun next(): Int {
    val next = nextCommands.removeAt(0)
    currentTag = next.tag
    currentDepth = next.depth
    return next.command
  }

  override fun getDepth(): Int {
    return currentDepth
  }

  override fun getAttributeCount(): Int {
    return currentTag!!.attributes.size
  }

  override fun getAttributeNamespace(index: Int): String {
    return currentTag!!.attributes[index].namespace
  }

  override fun getAttributeName(index: Int): String {
    return currentTag!!.attributes[index].name
  }

  override fun getAttributeValue(index: Int): String {
    return currentTag!!.attributes[index].value
  }

  override fun getAttributeValue(
    namespace: String?,
    name: String?
  ): String? {
    return currentTag!!.attributes
        .find { it.namespace == namespace && it.name == name }
        ?.value
  }

  override fun getNamespace(prefix: String?): String {
    return currentTag?.namespaces?.get(prefix) ?: ""
  }

  override fun getPositionDescription(): String {
    throw UnsupportedOperationException("Minimum parser methods are supported.")
  }

  override fun setInput(p0: Reader?) {
    throw UnsupportedOperationException("Minimum parser methods are supported.")
  }

  override fun setInput(
    p0: InputStream?,
    p1: String?
  ) {
    throw UnsupportedOperationException("Minimum parser methods are supported.")
  }

  override fun getFeature(p0: String?): Boolean {
    throw UnsupportedOperationException("Minimum parser methods are supported.")
  }

  override fun isWhitespace(): Boolean {
    throw UnsupportedOperationException("Minimum parser methods are supported.")
  }

  override fun getAttributePrefix(p0: Int): String {
    throw UnsupportedOperationException("Minimum parser methods are supported.")
  }

  override fun getNamespacePrefix(p0: Int): String {
    throw UnsupportedOperationException("Minimum parser methods are supported.")
  }

  override fun getText(): String {
    throw UnsupportedOperationException("Minimum parser methods are supported.")
  }

  override fun getProperty(p0: String?): Any {
    throw UnsupportedOperationException("Minimum parser methods are supported.")
  }

  override fun getColumnNumber(): Int {
    throw UnsupportedOperationException("Minimum parser methods are supported.")
  }

  override fun getAttributeType(p0: Int): String {
    throw UnsupportedOperationException("Minimum parser methods are supported.")
  }

  override fun getEventType(): Int {
    throw UnsupportedOperationException("Minimum parser methods are supported.")
  }

  override fun nextTag(): Int {
    throw UnsupportedOperationException("Minimum parser methods are supported.")
  }

  override fun getNamespaceUri(p0: Int): String {
    throw UnsupportedOperationException("Minimum parser methods are supported.")
  }

  override fun getInputEncoding(): String {
    throw UnsupportedOperationException("Minimum parser methods are supported.")
  }

  override fun setProperty(
    p0: String?,
    p1: Any?
  ) {
    throw UnsupportedOperationException("Minimum parser methods are supported.")
  }

  override fun getNamespaceCount(p0: Int): Int {
    throw UnsupportedOperationException("Minimum parser methods are supported.")
  }

  override fun getLineNumber(): Int {
    throw UnsupportedOperationException("Minimum parser methods are supported.")
  }

  override fun getTextCharacters(p0: IntArray?): CharArray {
    throw UnsupportedOperationException("Minimum parser methods are supported.")
  }

  override fun nextToken(): Int {
    throw UnsupportedOperationException("Minimum parser methods are supported.")
  }

  override fun require(
    p0: Int,
    p1: String?,
    p2: String?
  ) {
    throw UnsupportedOperationException("Minimum parser methods are supported.")
  }

  override fun isEmptyElementTag(): Boolean {
    throw UnsupportedOperationException("Minimum parser methods are supported.")
  }

  override fun nextText(): String {
    throw UnsupportedOperationException("Minimum parser methods are supported.")
  }

  override fun isAttributeDefault(p0: Int): Boolean {
    throw UnsupportedOperationException("Minimum parser methods are supported.")
  }

  override fun setFeature(
    p0: String?,
    p1: Boolean
  ) {
    throw UnsupportedOperationException("Minimum parser methods are supported.")
  }

  override fun getNamespace(): String {
    throw UnsupportedOperationException("Minimum parser methods are supported.")
  }

  override fun getPrefix(): String {
    throw UnsupportedOperationException("Minimum parser methods are supported.")
  }

  override fun defineEntityReplacementText(
    p0: String?,
    p1: String?
  ) {
    throw UnsupportedOperationException("Minimum parser methods are supported.")
  }

  data class NextCommand(
    val command: Int,
    val tag: TagSnapshot?,
    val depth: Int
  )
}