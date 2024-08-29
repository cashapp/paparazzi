package app.cash.paparazzi.gradle.reporting

import java.io.IOException
import java.io.Writer
import java.util.ArrayDeque
import java.util.Deque

/**
 * Custom SimpleMarkupWriter based on Gradle's SimpleMarkupWriter
 */
internal open class SimpleMarkupWriter protected constructor(
  private val output: Writer,
  private val indent: String?
) : Writer() {
  private val elements: Deque<String> = ArrayDeque()
  private var context: Context
  private var squareBrackets = 0

  init {
    context = Context.Outside
  }

  @Throws(IOException::class)
  override fun write(chars: CharArray, offset: Int, length: Int) {
    characters(chars, offset, length)
  }

  @Throws(IOException::class)
  override fun flush() {
    output.flush()
  }

  @Throws(IOException::class)
  override fun close() = Unit

  @Throws(IOException::class)
  fun characters(characters: CharArray): SimpleMarkupWriter =
    characters(characters, 0, characters.size)

  @Throws(IOException::class)
  fun characters(
    characters: CharArray,
    start: Int,
    count: Int
  ): SimpleMarkupWriter {
    if (context == Context.CData) {
      writeCDATA(characters, start, count)
    } else {
      maybeStartText()
      writeXmlEncoded(characters, start, count)
    }

    return this
  }

  @Throws(IOException::class)
  fun characters(characters: CharSequence): SimpleMarkupWriter {
    if (context == Context.CData) {
      writeCDATA(characters)
    } else {
      maybeStartText()
      writeXmlEncoded(characters)
    }

    return this
  }

  @Throws(IOException::class)
  private fun maybeStartText() {
    check(
      context != Context.Outside
    ) { "Cannot write text, as there are no started elements." }
    if (context == Context.StartTag) {
      writeRaw(">")
    }

    context = Context.Text
  }

  @Throws(IOException::class)
  private fun maybeFinishStartTag() {
    if (context == Context.StartTag) {
      writeRaw(">")
      context = Context.ElementContent
    }
  }

  @Throws(IOException::class)
  open fun startElement(name: String): SimpleMarkupWriter {
    require(isValidXmlName(name)) {
      String.format("Invalid element name: '%s'", name)
    }
    check(context != Context.CData) {
      "Cannot start element, as current CDATA node has not been closed."
    }
    maybeFinishStartTag()
    if (indent != null) {
      writeRaw(LINE_SEPARATOR)

      for (i in elements.indices) {
        writeRaw(indent)
      }
    }

    context = Context.StartTag
    elements.add(name)
    writeRaw("<")
    writeRaw(name)
    return this
  }

  @Throws(IOException::class)
  fun endElement(): SimpleMarkupWriter {
    check(context != Context.Outside) {
      "Cannot end element, as there are no started elements."
    }
    check(context != Context.CData) {
      "Cannot end element, as current CDATA node has not been closed."
    }
    if (context == Context.StartTag) {
      writeRaw("/>")
      elements.removeLast()
    } else {
      if (context != Context.Text && indent != null) {
        writeRaw(LINE_SEPARATOR)

        for (i in 1 until elements.size) {
          writeRaw(indent)
        }
      }

      writeRaw("</")
      writeRaw(elements.removeLast())
      writeRaw(">")
    }

    if (elements.isEmpty()) {
      if (indent != null) {
        writeRaw(LINE_SEPARATOR)
      }

      output.flush()
      context = Context.Outside
    } else {
      context = Context.ElementContent
    }

    return this
  }

  @Throws(IOException::class)
  private fun writeCDATA(cdata: CharArray, offset: Int, count: Int) {
    val end = offset + count
    var i = offset

    while (i < end) {
      val codePoint = Character.codePointAt(cdata, i)
      i += Character.charCount(codePoint)
      writeCDATA(codePoint)
    }
  }

  @Throws(IOException::class)
  private fun writeCDATA(cdata: CharSequence) {
    val len = cdata.length
    var i = 0

    while (i < len) {
      val codePoint = Character.codePointAt(cdata, i)
      i += Character.charCount(codePoint)
      writeCDATA(codePoint)
    }
  }

  @Throws(IOException::class)
  private fun writeCDATA(ch: Int) {
    if (needsCDATAEscaping(ch)) {
      writeRaw("]]><![CDATA[>")
    } else if (!isValidNameChar(ch.toChar())) {
      writeRaw('?')
    } else if ((
        ch > 65535 ||
          !isRestrictedCharacter(
            ch.toChar()
          )
        ) &&
      Character.charCount(ch) != 2
    ) {
      writeRaw(ch.toChar())
    } else {
      writeRaw("]]>")
      writeCharacterReference(ch)
      writeRaw("<![CDATA[")
    }
  }

  @Throws(IOException::class)
  private fun writeCharacterReference(ch: Int) {
    writeRaw("&#x")
    writeRaw(Integer.toHexString(ch))
    writeRaw(";")
  }

  private fun needsCDATAEscaping(ch: Int): Boolean {
    when (ch) {
      62 -> {
        if (squareBrackets >= 2) {
          squareBrackets = 0
          return true
        }

        return false
      }

      93 -> {
        ++squareBrackets
        return false
      }

      else -> {
        squareBrackets = 0
        return false
      }
    }
  }

  @Throws(IOException::class)
  fun startCDATA(): SimpleMarkupWriter {
    check(
      context != Context.CData
    ) { "Cannot start CDATA node, as current CDATA node has not been closed." }
    maybeFinishStartTag()
    writeRaw("<![CDATA[")
    context = Context.CData
    squareBrackets = 0
    return this
  }

  @Throws(IOException::class)
  fun endCDATA(): SimpleMarkupWriter {
    check(context == Context.CData) { "Cannot end CDATA node, as not currently in a CDATA node." }
    writeRaw("]]>")
    context = Context.Text
    return this
  }

  @Throws(IOException::class)
  open fun comment(comment: String): SimpleMarkupWriter {
    require(!comment.contains("--")) { "'--' is invalid inside an XML comment: $comment" }
    maybeFinishStartTag()
    if (indent != null) {
      writeRaw(LINE_SEPARATOR)

      for (i in elements.indices) {
        writeRaw(indent)
      }
    }

    writeRaw("<!--")
    if (comment.substring(0, 1).isNotBlank()) {
      writeRaw(" ")
    }

    writeSafeCharacters(comment)
    if (comment.substring(comment.length - 1).isNotBlank()) {
      writeRaw(" ")
    }

    writeRaw("-->")
    return this
  }

  @Throws(IOException::class)
  open fun attribute(name: String, value: String): SimpleMarkupWriter {
    require(isValidXmlName(name)) {
      String.format(
        "Invalid attribute name: '%s'", name
      )
    }
    check(
      context == Context.StartTag
    ) { "Cannot write attribute [$name:$value]. You should write start element first." }
    writeRaw(" ")
    writeRaw(name)
    writeRaw("=\"")
    writeXmlAttributeEncoded(value)
    writeRaw("\"")
    return this
  }

  @Throws(IOException::class)
  private fun writeRaw(c: Char) {
    output.write(c.code)
  }

  @Throws(IOException::class)
  protected fun writeRaw(message: String) {
    output.write(message)
  }

  @Throws(IOException::class)
  private fun writeXmlEncoded(
    message: CharArray,
    offset: Int,
    count: Int
  ) {
    val end = offset + count
    var i = offset

    while (i < end) {
      val codePoint = Character.codePointAt(message, i)
      i += Character.charCount(codePoint)
      writeXmlEncoded(codePoint)
    }
  }

  @Throws(IOException::class)
  private fun writeXmlAttributeEncoded(message: CharSequence) {
    val len = message.length
    var i = 0

    while (i < len) {
      val codePoint = Character.codePointAt(message, i)
      i += Character.charCount(codePoint)
      writeXmlAttributeEncoded(codePoint)
    }
  }

  @Throws(IOException::class)
  private fun writeXmlAttributeEncoded(ch: Int) {
    if (ch == 9) {
      writeRaw("&#9;")
    } else if (ch == 10) {
      writeRaw("&#10;")
    } else if (ch == 13) {
      writeRaw("&#13;")
    } else {
      writeXmlEncoded(ch)
    }
  }

  @Throws(IOException::class)
  private fun writeXmlEncoded(message: CharSequence) {
    checkNotNull(message)

    val len = message.length
    var i = 0

    while (i < len) {
      val codePoint = Character.codePointAt(message, i)
      i += Character.charCount(codePoint)
      writeXmlEncoded(codePoint)
    }
  }

  @Throws(IOException::class)
  private fun writeSafeCharacters(message: CharSequence) {
    checkNotNull(message)

    val len = message.length
    var i = 0

    while (i < len) {
      val codePoint = Character.codePointAt(message, i)
      i += Character.charCount(codePoint)
      writeSafeCharacter(codePoint)
    }
  }

  @Throws(IOException::class)
  private fun writeXmlEncoded(ch: Int) {
    if (ch == 60) {
      writeRaw("&lt;")
    } else if (ch == 62) {
      writeRaw("&gt;")
    } else if (ch == 38) {
      writeRaw("&amp;")
    } else if (ch == 34) {
      writeRaw("&quot;")
    } else {
      writeSafeCharacter(ch)
    }
  }

  @Throws(IOException::class)
  private fun writeSafeCharacter(ch: Int) {
    if (!isLegalCharacter(ch.toChar())) {
      writeRaw('?')
    } else if ((
        ch > 65535 ||
          !isRestrictedCharacter(
            ch.toChar()
          )
        ) &&
      Character.charCount(ch) != 2
    ) {
      writeRaw(ch.toChar())
    } else {
      writeCharacterReference(ch)
    }
  }

  private enum class Context {
    Outside,
    Text,
    CData,
    StartTag,
    ElementContent
  }

  companion object {
    private val LINE_SEPARATOR: String = System.lineSeparator()

    private fun isLegalCharacter(c: Char): Boolean {
      if (c.code == 0) {
        return false
      } else if (c.code <= 0xD7FF) {
        return true
      } else if (c.code < 0xE000) {
        return false
      } else if (c.code <= 0xFFFD) {
        return true
      }
      return false
    }

    private fun isRestrictedCharacter(c: Char): Boolean {
      if (c.code == 0x9 || c.code == 0xA || c.code == 0xD || c.code == 0x85) {
        return false
      } else if (c.code <= 0x1F) {
        return true
      } else if (c.code < 0x7F) {
        return false
      } else if (c.code <= 0x9F) {
        return true
      }
      return false
    }

    private fun isValidXmlName(name: String): Boolean {
      val length = name.length
      if (length == 0) {
        return false
      }
      var ch = name[0]
      if (!isValidNameStartChar(ch)) {
        return false
      }
      for (i in 1 until length) {
        ch = name[i]
        if (!isValidNameChar(ch)) {
          return false
        }
      }
      return true
    }

    private fun isValidNameChar(ch: Char): Boolean {
      if (isValidNameStartChar(ch)) {
        return true
      }
      if (ch in '0'..'9') {
        return true
      }
      if (ch == '-' || ch == '.' || ch == '\u00b7') {
        return true
      }
      if (ch in '\u0300'..'\u036f') {
        return true
      }
      return ch in '\u203f'..'\u2040'
    }

    private fun isValidNameStartChar(ch: Char): Boolean {
      if (ch in 'A'..'Z') {
        return true
      }
      if (ch in 'a'..'z') {
        return true
      }
      if (ch == ':' || ch == '_') {
        return true
      }
      if (ch in '\u00c0'..'\u00d6') {
        return true
      }
      if (ch in '\u00d8'..'\u00f6') {
        return true
      }
      if (ch in '\u00f8'..'\u02ff') {
        return true
      }
      if (ch in '\u0370'..'\u037d') {
        return true
      }
      if (ch in '\u037f'..'\u1fff') {
        return true
      }
      if (ch in '\u200c'..'\u200d') {
        return true
      }
      if (ch in '\u2070'..'\u218f') {
        return true
      }
      if (ch in '\u2c00'..'\u2fef') {
        return true
      }
      if (ch in '\u3001'..'\ud7ff') {
        return true
      }
      if (ch in '\uf900'..'\ufdcf') {
        return true
      }
      return ch in '\ufdf0'..'\ufffd'
    }
  }
}
