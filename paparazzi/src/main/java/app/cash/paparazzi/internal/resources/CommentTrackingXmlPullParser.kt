/*
 * Copyright (C) 2023 Square, Inc.
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
package app.cash.paparazzi.internal.resources

import com.android.SdkConstants.TAG_EAT_COMMENT
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.InputStream
import java.io.Reader

/**
 * Ported from: [CommentTrackingXmlPullParser.java](https://cs.android.com/android-studio/platform/tools/base/+/47d204001bf0cb6273d8b135c7eece3a982cf0e0:resource-repository/main/java/com/android/resources/base/CommentTrackingXmlPullParser.java)
 *
 * An [XmlPullParser] that keeps track of the last comment preceding an XML tag and special comments
 * that are used in the framework resource files for describing groups of "attr" resources. Here is
 * an example of an "attr" group comment:
 * <pre>
 * &lt;!-- =========== --&gt;
 * &lt;!-- Text styles --&gt;
 * &lt;!-- =========== --&gt;
 * &lt;eat-comment/&gt;
</pre> *
 */
open class CommentTrackingXmlPullParser : KXmlParser() {
  /**
   * Returns the last encountered comment that is not an ASCII art.
   */
  var lastComment: String? = null
  private var tagEncounteredAfterComment: Boolean = false
  private val attrGroupCommentStack = ArrayList<String?>(4)

  /**
   * Initializes the parser. XML namespaces are supported by default.
   */
  init {
    try {
      setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
    } catch (e: XmlPullParserException) {
      throw Error(e) // KXmlParser is guaranteed to support FEATURE_PROCESS_NAMESPACES.
    }
  }

  /**
   * Returns the name of the current "attr" group, e.g. "Button Styles" group for "buttonStyleSmall" "attr" tag.
   */
  val attrGroupComment: String?
    get() = attrGroupCommentStack[attrGroupCommentStack.size - 1]

  @Throws(XmlPullParserException::class, IOException::class)
  override fun nextToken(): Int {
    val token = super.nextToken()
    processToken(token)
    return token
  }

  @Throws(XmlPullParserException::class, IOException::class)
  override operator fun next(): Int {
    throw UnsupportedOperationException("Use nextToken() instead of next() for comment tracking to work")
  }

  private fun processToken(token: Int) {
    when (token) {
      XmlPullParser.START_TAG -> {
        if (tagEncounteredAfterComment) {
          lastComment = null
        }
        tagEncounteredAfterComment = true
        // Duplicate the last element in attrGroupCommentStack.
        attrGroupCommentStack += attrGroupCommentStack.last()
        assert(attrGroupCommentStack.size == depth + 1)

        if (TAG_EAT_COMMENT == name && prefix == null) {
          // The framework attribute file follows a special convention where related attributes are grouped together,
          // and there is always a set of comments that indicate these sections which look like this:
          //     <!-- =========== -->
          //     <!-- Text styles -->
          //     <!-- =========== -->
          //     <eat-comment/>
          // These section headers are always immediately followed by an <eat-comment>. Not all <eat-comment/> sections are
          // actually attribute headers, some are comments. We identify these by looking at the line length; category comments
          // are short, and descriptive comments are longer.
          if (lastComment != null && lastComment!!.length <= ATTR_GROUP_MAX_CHARACTERS && !lastComment!!.startsWith("TODO:")) {
            var attrGroupComment = lastComment!!
            if (attrGroupComment.endsWith(".")) {
              // Strip the trailing period.
              attrGroupComment = attrGroupComment.substring(0, attrGroupComment.length - 1)
            }
            // Replace the second to last element in attrGroupCommentStack.
            attrGroupCommentStack[attrGroupCommentStack.size - 2] = attrGroupComment
          }
        }
      }

      XmlPullParser.END_TAG -> {
        lastComment = null
        attrGroupCommentStack.removeLast()
      }

      XmlPullParser.COMMENT -> {
        val commentText = text.trim()
        if (!isEmptyOrAsciiArt(commentText)) {
          lastComment = commentText
          tagEncounteredAfterComment = false
        }
      }
    }
  }

  @Throws(XmlPullParserException::class)
  override fun setInput(reader: Reader) {
    super.setInput(reader)
    lastComment = null
    attrGroupCommentStack.clear()
    attrGroupCommentStack.add(null)
  }

  @Throws(XmlPullParserException::class)
  override fun setInput(inputStream: InputStream, encoding: String?) {
    super.setInput(inputStream, encoding)
    lastComment = null
    attrGroupCommentStack.clear()
    attrGroupCommentStack.add(null)
  }

  companion object {
    // Used for parsing group of attributes, used heuristically to skip long comments before <eat-comment/>.
    private const val ATTR_GROUP_MAX_CHARACTERS = 40

    private fun isEmptyOrAsciiArt(commentText: String): Boolean =
      commentText.isEmpty() || commentText[0] == '*' || commentText[0] == '='
  }
}
