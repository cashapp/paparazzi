package app.cash.paparazzi.internal.resources

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.StringReader

class CommentTrackingXmlPullParserTest {
  @Test
  fun test() {
    StringReader(SIMPLE_RES).use {
      val parser = CommentTrackingXmlPullParser().apply {
        setInput(it)
      }

      // <resources>
      parser.nextToken()
      parser.hasAttributes(
        name = "resources",
        lastComment = null,
        attrGroupComment = null
      )

      // new line
      parser.nextToken()
      parser.hasAttributes(
        name = null,
        lastComment = null,
        attrGroupComment = null
      )

      // <!-- These are the standard attributes that make up a complete theme. -->
      parser.nextToken()
      parser.hasAttributes(
        name = null,
        lastComment = "These are the standard attributes that make up a complete theme.",
        attrGroupComment = null
      )

      // new line
      parser.nextToken()
      parser.hasAttributes(
        name = null,
        lastComment = "These are the standard attributes that make up a complete theme.",
        attrGroupComment = null
      )

      // <declare-styleable name="Theme">
      parser.nextToken()
      parser.hasAttributes(
        name = "declare-styleable",
        lastComment = "These are the standard attributes that make up a complete theme.",
        attrGroupComment = null
      )

      // new line
      parser.nextToken()
      parser.hasAttributes(
        name = null,
        lastComment = "These are the standard attributes that make up a complete theme.",
        attrGroupComment = null
      )

      // <!-- ============= -->
      parser.nextToken()
      parser.hasAttributes(
        name = null,
        lastComment = "These are the standard attributes that make up a complete theme.",
        attrGroupComment = null
      )

      // new line
      parser.nextToken()
      parser.hasAttributes(
        name = null,
        lastComment = "These are the standard attributes that make up a complete theme.",
        attrGroupComment = null
      )

      // <!-- Button styles -->
      parser.nextToken()
      parser.hasAttributes(
        name = null,
        lastComment = "Button styles",
        attrGroupComment = null
      )

      // new line
      parser.nextToken()
      parser.hasAttributes(
        name = null,
        lastComment = "Button styles",
        attrGroupComment = null
      )

      // <!-- ============= -->
      parser.nextToken()
      parser.hasAttributes(
        name = null,
        lastComment = "Button styles",
        attrGroupComment = null
      )

      // new line
      parser.nextToken()
      parser.hasAttributes(
        name = null,
        lastComment = "Button styles",
        attrGroupComment = null
      )

      // <eat-comment /> start
      parser.nextToken()
      parser.hasAttributes(
        name = "eat-comment",
        lastComment = "Button styles",
        attrGroupComment = null
      )

      // <eat-comment /> end
      parser.nextToken()
      parser.hasAttributes(
        name = "eat-comment",
        lastComment = null,
        attrGroupComment = "Button styles"
      )

      // new line
      parser.nextToken()
      parser.hasAttributes(
        name = null,
        lastComment = null,
        attrGroupComment = "Button styles"
      )

      // <!-- Normal Button style. -->
      parser.nextToken()
      parser.hasAttributes(
        name = null,
        lastComment = "Normal Button style.",
        attrGroupComment = "Button styles"
      )

      // new line
      parser.nextToken()
      parser.hasAttributes(
        name = null,
        lastComment = "Normal Button style.",
        attrGroupComment = "Button styles"
      )

      // <attr name="buttonStyle" format="reference" /> start
      parser.nextToken()
      parser.hasAttributes(
        name = "attr",
        lastComment = "Normal Button style.",
        attrGroupComment = "Button styles"
      )

      // <attr name="buttonStyle" format="reference" /> end
      parser.nextToken()
      parser.hasAttributes(
        name = "attr",
        lastComment = null,
        attrGroupComment = "Button styles"
      )

      // new line
      parser.nextToken()
      parser.hasAttributes(
        name = null,
        lastComment = null,
        attrGroupComment = "Button styles"
      )

      // </declare-styleable>
      parser.nextToken()
      parser.hasAttributes(
        name = "declare-styleable",
        lastComment = null,
        attrGroupComment = null
      )

      // new line
      parser.nextToken()
      parser.hasAttributes(
        name = null,
        lastComment = null,
        attrGroupComment = null
      )

      // </resources>
      parser.nextToken()
      parser.hasAttributes(
        name = "resources",
        lastComment = null,
        attrGroupComment = null
      )
    }
  }

  private fun CommentTrackingXmlPullParser.hasAttributes(
    name: String?,
    lastComment: String?,
    attrGroupComment: String?
  ) {
    assertThat(this.name).isEqualTo(name)
    assertThat(this.lastComment).isEqualTo(lastComment)
    assertThat(attrGroupComment).isEqualTo(attrGroupComment)
  }

  companion object {
    val SIMPLE_RES = """
      |<resources>
      |    <!-- These are the standard attributes that make up a complete theme. -->
      |    <declare-styleable name="Theme">
      |        <!-- ============= -->
      |        <!-- Button styles -->
      |        <!-- ============= -->
      |        <eat-comment />
      |        <!-- Normal Button style. -->
      |        <attr name="buttonStyle" format="reference" />
      |    </declare-styleable>
      |</resources>
    """.trimMargin()
  }
}
