package app.cash.paparazzi.internal.resources

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.TOOLS_URI
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

class NamespaceResolverTest {
  @Test
  fun test() {
    StringReader(SIMPLE_LAYOUT).use {
      val parser = KXmlParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        setInput(it)
      }

      var resolver = NamespaceResolver(parser)
      assertThat(resolver.namespaceCount).isEqualTo(0)
      assertThat(resolver.prefixToUri("newtools")).isNull()
      assertThat(resolver.prefixToUri("framework")).isNull()
      assertThat(resolver.uriToPrefix(TOOLS_URI)).isNull()
      assertThat(resolver.uriToPrefix(ANDROID_URI)).isNull()

      advance(parser)

      resolver = NamespaceResolver(parser)
      assertThat(resolver.namespaceCount).isEqualTo(1)
      assertThat(resolver.prefixToUri("framework")).isEqualTo(ANDROID_URI)
      assertThat(resolver.prefixToUri("newtools")).isNull()
      assertThat(resolver.uriToPrefix(ANDROID_URI)).isEqualTo("framework")
      assertThat(resolver.uriToPrefix(TOOLS_URI)).isNull()

      advance(parser)

      resolver = NamespaceResolver(parser)
      assertThat(resolver.namespaceCount).isEqualTo(2)
      assertThat(resolver.prefixToUri("framework")).isEqualTo(ANDROID_URI)
      assertThat(resolver.prefixToUri("newtools")).isEqualTo(TOOLS_URI)
      assertThat(resolver.uriToPrefix(ANDROID_URI)).isEqualTo("framework")
      assertThat(resolver.uriToPrefix(TOOLS_URI)).isEqualTo("newtools")

      advance(parser)

      resolver = NamespaceResolver(parser)
      assertThat(resolver.namespaceCount).isEqualTo(0)
      assertThat(resolver.prefixToUri("newtools")).isNull()
      assertThat(resolver.prefixToUri("framework")).isNull()
      assertThat(resolver.uriToPrefix(TOOLS_URI)).isNull()
      assertThat(resolver.uriToPrefix(ANDROID_URI)).isNull()
    }
  }

  private fun advance(parser: KXmlParser) {
    var event: Int
    do {
      event = parser.nextToken()
    } while (event != XmlPullParser.END_DOCUMENT && event != XmlPullParser.START_TAG)
  }

  companion object {
    val SIMPLE_LAYOUT = """
      |<?xml version="1.0" encoding="utf-8"?>
      |<LinearLayout xmlns:framework="http://schemas.android.com/apk/res/android"
      |  framework:orientation="vertical"
      |  framework:layout_width="fill_parent"
      |  framework:layout_height="fill_parent">
      |
      |  <TextView xmlns:newtools="http://schemas.android.com/tools"
      |    framework:layout_width="fill_parent"
      |    framework:layout_height="wrap_content"
      |    newtools:text="Hello World, MyActivity" />
      |
      |</LinearLayout>
    """.trimMargin()
  }
}
