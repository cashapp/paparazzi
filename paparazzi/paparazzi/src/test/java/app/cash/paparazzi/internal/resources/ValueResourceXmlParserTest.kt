package app.cash.paparazzi.internal.resources

import com.android.ide.common.rendering.api.ResourceNamespace
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

class ValueResourceXmlParserTest {
  @Test
  fun test() {
    StringReader(SIMPLE_LAYOUT).use {
      val parser = ValueResourceXmlParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        setInput(it)
      }

      parser.assertNamespaceResolverCheckFails()
      assertThat(parser.namespaceResolverCache).isEmpty()
      assertThat(parser.resolverStack).isEmpty()

      parser.next()

      var namespaceResolver = parser.namespaceResolver
      assertThat(namespaceResolver).isNotEqualTo(ResourceNamespace.Resolver.EMPTY_RESOLVER)
      assertThat((namespaceResolver as NamespaceResolver).namespaceCount).isEqualTo(1)
      assertThat(parser.namespaceResolverCache).hasSize(1)
      assertThat(parser.resolverStack).hasSize(1)

      parser.next()

      parser.assertNamespaceResolverCheckFails()
      assertThat(parser.namespaceResolverCache).hasSize(1)
      assertThat(parser.resolverStack).hasSize(1)

      parser.next()

      namespaceResolver = parser.namespaceResolver
      assertThat(namespaceResolver).isNotEqualTo(ResourceNamespace.Resolver.EMPTY_RESOLVER)
      assertThat((namespaceResolver as NamespaceResolver).namespaceCount).isEqualTo(2)
      assertThat(parser.namespaceResolverCache).hasSize(2)
      assertThat(parser.resolverStack).hasSize(2)

      parser.next()

      parser.assertNamespaceResolverCheckFails()
      assertThat(parser.namespaceResolverCache).hasSize(2)
      assertThat(parser.resolverStack).hasSize(1)

      parser.next()

      parser.assertNamespaceResolverCheckFails()
      assertThat(parser.namespaceResolverCache).hasSize(2)
      assertThat(parser.resolverStack).hasSize(1)

      parser.next()

      parser.assertNamespaceResolverCheckFails()
      assertThat(parser.namespaceResolverCache).hasSize(2)
      assertThat(parser.resolverStack).hasSize(0)

      parser.next()

      parser.assertNamespaceResolverCheckFails()
      assertThat(parser.namespaceResolverCache).hasSize(2)
      assertThat(parser.resolverStack).hasSize(0)
    }
  }

  private fun ValueResourceXmlParser.assertNamespaceResolverCheckFails() {
    try {
      namespaceResolver
    } catch (e: Exception) {
      assertThat(e).isInstanceOf(IllegalStateException::class.java)
      assertThat(e).hasMessageThat().isEqualTo("Check failed.")
    }
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
