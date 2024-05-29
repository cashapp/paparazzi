package app.cash.paparazzi.internal.resources

import com.android.ide.common.rendering.api.ResourceNamespace
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.StringReader

class ValueResourceXmlParserTest {
  @Test
  fun test() {
    StringReader(SIMPLE_LAYOUT).use {
      val parser = ValueResourceXmlParser().apply {
        setInput(it)
      }

      parser.nextToken()

      parser.assertNamespaceResolverCheckFails()
      assertThat(parser.namespaceResolverCache).isEmpty()
      assertThat(parser.resolverStack).isEmpty()

      parser.nextToken()

      var namespaceResolver = parser.namespaceResolver
      assertThat(namespaceResolver).isNotEqualTo(ResourceNamespace.Resolver.EMPTY_RESOLVER)
      assertThat((namespaceResolver as NamespaceResolver).namespaceCount).isEqualTo(1)
      assertThat(parser.namespaceResolverCache).hasSize(1)
      assertThat(parser.resolverStack).hasSize(1)

      parser.nextToken()

      parser.assertNamespaceResolverCheckFails()
      assertThat(parser.namespaceResolverCache).hasSize(1)
      assertThat(parser.resolverStack).hasSize(1)

      parser.nextToken()

      namespaceResolver = parser.namespaceResolver
      assertThat(namespaceResolver).isNotEqualTo(ResourceNamespace.Resolver.EMPTY_RESOLVER)
      assertThat((namespaceResolver as NamespaceResolver).namespaceCount).isEqualTo(2)
      assertThat(parser.namespaceResolverCache).hasSize(2)
      assertThat(parser.resolverStack).hasSize(2)

      parser.nextToken()

      parser.assertNamespaceResolverCheckFails()
      assertThat(parser.namespaceResolverCache).hasSize(2)
      assertThat(parser.resolverStack).hasSize(1)

      parser.nextToken()

      parser.assertNamespaceResolverCheckFails()
      assertThat(parser.namespaceResolverCache).hasSize(2)
      assertThat(parser.resolverStack).hasSize(1)

      parser.nextToken()

      parser.assertNamespaceResolverCheckFails()
      assertThat(parser.namespaceResolverCache).hasSize(2)
      assertThat(parser.resolverStack).hasSize(0)

      parser.nextToken()

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
