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

import com.android.ide.common.rendering.api.ArrayResourceValue
import com.android.ide.common.rendering.api.PluralsResourceValue
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceValue
import com.android.ide.common.resources.KnownNamespacesMap
import com.android.ide.common.resources.ResourceValueMap
import com.android.ide.common.resources.configuration.LocaleQualifier
import com.android.resources.ResourceType
import com.google.common.collect.Table
import com.google.common.collect.Tables
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.EnumMap

class PseudolocaleGeneratorTest {
  private val resFolderRoot = resolveProjectPath("src/test/resources/folders/res")

  private val repository = ResourceFolderRepository(
    resourceDir = resFolderRoot.toFile(),
    namespace = ResourceNamespace.TODO()
  )

  @Test
  fun accent() {
    val table = createResourceTable()
    table.pseudolocalizeIfNeeded(LocaleQualifier.getQualifier("en-rXA"))

    // STRING
    with(table[ResourceNamespace.TODO(), ResourceType.STRING]!!) {
      assertThat(size).isEqualTo(4)
      assertThat(getValue("string_name").value).isEqualTo("[Ţéšţ Šţŕîñĝ one two]")
      assertThat(getValue("string_name_xliff").value).isEqualTo("[Ţéšţ Šţŕîñĝ »{0}« ŵîţĥ šûƒƒîх one two three]")
      assertThat(getValue("string_name_html").value).isEqualTo("[Ţĥîš Ţéšţ Šţŕîñĝ one two three]")
      assertThat(getValue("string_name_html").rawXmlValue).isEqualTo("[Ţĥîš <b>Ţéšţ</b> Šţŕîñĝ one two three]")
      assertThat(getValue("string_not_translate").value).isEqualTo("Not Translatable String")
    }

    // ARRAY
    with(table[ResourceNamespace.TODO(), ResourceType.ARRAY]!!) {
      assertThat(size).isEqualTo(1)
      with(getValue("string_array_name") as ArrayResourceValue) {
        assertThat(elementCount).isEqualTo(2)
        assertThat(getElement(0)).isEqualTo("[Fîŕšţ Ţéšţ Šţŕîñĝ one two three]")
        assertThat(getElement(1)).isEqualTo("[Šéçöñð Ţéšţ Šţŕîñĝ one two three]")
      }
    }

    // PLURALS
    with(table[ResourceNamespace.TODO(), ResourceType.PLURALS]!!) {
      assertThat(size).isEqualTo(1)
      with(getValue("plural_name") as PluralsResourceValue) {
        assertThat(pluralsCount).isEqualTo(2)
        assertThat(getValue(0)).isEqualTo("[Ñöţĥîñĝ one two]")
        assertThat(getValue(1)).isEqualTo("[Öñé Šţŕîñĝ one two]")
      }
    }
  }

  @Test
  @Suppress("ktlint:standard:max-line-length")
  fun bidi() {
    val table = createResourceTable()
    table.pseudolocalizeIfNeeded(LocaleQualifier.getQualifier("ar-rXB"))

    // STRING
    with(table[ResourceNamespace.TODO(), ResourceType.STRING]!!) {
      assertThat(size).isEqualTo(4)
      assertThat(getValue("string_name").value).isEqualTo("${bidiWordStart}Test$bidiWordEnd ${bidiWordStart}String$bidiWordEnd")
      assertThat(getValue("string_name_xliff").value).isEqualTo("${bidiWordStart}Test$bidiWordEnd ${bidiWordStart}String$bidiWordEnd $bidiWordStart{0}$bidiWordEnd ${bidiWordStart}with$bidiWordEnd ${bidiWordStart}suffix$bidiWordEnd")
      assertThat(getValue("string_name_html").value).isEqualTo("${bidiWordStart}This$bidiWordEnd ${bidiWordStart}Test$bidiWordEnd ${bidiWordStart}String$bidiWordEnd")
      assertThat(getValue("string_name_html").rawXmlValue).isEqualTo("${bidiWordStart}This$bidiWordEnd $bidiWordStart<b>Test</b>$bidiWordEnd ${bidiWordStart}String$bidiWordEnd")
      assertThat(getValue("string_not_translate").value).isEqualTo("Not Translatable String")
    }

    // ARRAY
    with(table[ResourceNamespace.TODO(), ResourceType.ARRAY]!!) {
      assertThat(size).isEqualTo(1)
      with(getValue("string_array_name") as ArrayResourceValue) {
        assertThat(elementCount).isEqualTo(2)
        assertThat(getElement(0)).isEqualTo("${bidiWordStart}First$bidiWordEnd ${bidiWordStart}Test$bidiWordEnd ${bidiWordStart}String$bidiWordEnd")
        assertThat(getElement(1)).isEqualTo("${bidiWordStart}Second$bidiWordEnd ${bidiWordStart}Test$bidiWordEnd ${bidiWordStart}String$bidiWordEnd")
      }
    }

    // PLURALS
    with(table[ResourceNamespace.TODO(), ResourceType.PLURALS]!!) {
      assertThat(size).isEqualTo(1)
      with(getValue("plural_name") as PluralsResourceValue) {
        assertThat(pluralsCount).isEqualTo(2)
        assertThat(getValue(0)).isEqualTo("${bidiWordStart}Nothing$bidiWordEnd")
        assertThat(getValue(1)).isEqualTo("${bidiWordStart}One$bidiWordEnd ${bidiWordStart}String$bidiWordEnd")
      }
    }
  }

  @Test
  fun doesNotPseudolocalizeForOtherLocales() {
    val result = createResourceTable()
    result.pseudolocalizeIfNeeded(LocaleQualifier.getQualifier("en"))

    // STRING
    with(result[ResourceNamespace.TODO(), ResourceType.STRING]!!) {
      assertThat(size).isEqualTo(4)
      assertThat(getValue("string_name").value).isEqualTo("Test String")
      assertThat(getValue("string_name_xliff").value).isEqualTo("Test String {0} with suffix")
      assertThat(getValue("string_name_html").value).isEqualTo("This Test String")
      assertThat(getValue("string_name_html").rawXmlValue).isEqualTo("This <b>Test</b> String")
      assertThat(getValue("string_not_translate").value).isEqualTo("Not Translatable String")
    }

    // ARRAY
    with(result[ResourceNamespace.TODO(), ResourceType.ARRAY]!!) {
      assertThat(size).isEqualTo(1)
      with(getValue("string_array_name") as ArrayResourceValue) {
        assertThat(elementCount).isEqualTo(2)
        assertThat(getElement(0)).isEqualTo("First Test String")
        assertThat(getElement(1)).isEqualTo("Second Test String")
      }
    }

    // PLURALS
    with(result[ResourceNamespace.TODO(), ResourceType.PLURALS]!!) {
      assertThat(size).isEqualTo(1)
      with(getValue("plural_name") as PluralsResourceValue) {
        assertThat(pluralsCount).isEqualTo(2)
        assertThat(getValue(0)).isEqualTo("Nothing")
        assertThat(getValue(1)).isEqualTo("One String")
      }
    }
  }

  @Test
  fun doesNotPseudolocalizeOtherResourceTypes() {
    val table = createResourceTable()

    listOf("en-rXA", "ar-rXB").forEach { segment ->
      table.pseudolocalizeIfNeeded(LocaleQualifier.getQualifier(segment))
      // BOOL
      with(table[ResourceNamespace.TODO(), ResourceType.BOOL]!!) {
        assertThat(size).isEqualTo(2)
        assertThat(getValue("screen_small").value).isEqualTo("true")
        assertThat(getValue("adjust_view_bounds").value).isEqualTo("false")
      }

      // COLOR
      with(table[ResourceNamespace.TODO(), ResourceType.COLOR]!!) {
        assertThat(size).isEqualTo(3)
        assertThat(getValue("test_color").value).isEqualTo("#ffffffff")
        assertThat(getValue("test_color_2").value).isEqualTo("#00000000")
        assertThat(getValue("color_selector").value).isEqualTo("$resFolderRoot/color/color_selector.xml")
      }

      // DIMEN
      with(table[ResourceNamespace.TODO(), ResourceType.DIMEN]!!) {
        assertThat(size).isEqualTo(2)
        assertThat(getValue("textview_height").value).isEqualTo("25dp")
        assertThat(getValue("textview_width").value).isEqualTo("150dp")
      }
    }
  }

  private fun createResourceTable(): Table<ResourceNamespace, ResourceType, ResourceValueMap> {
    val table = Tables.newCustomTable(KnownNamespacesMap<Map<ResourceType, ResourceValueMap>>()) {
      EnumMap(ResourceType::class.java)
    }
    repository.allResources.groupBy { it.type }.forEach { (type, items) ->
      table.put(
        ResourceNamespace.TODO(),
        type,
        ResourceValueMap.create().apply {
          items.forEach { put(it.name, it as ResourceValue) }
        }
      )
    }

    return table
  }

  private val ResourceValueMap.size: Int
    get() = values().size

  private fun ResourceValueMap.getValue(name: String) = get(name)!!
}
