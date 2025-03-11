package app.cash.paparazzi.internal

import com.android.ide.common.rendering.api.ResourceNamespace.RES_AUTO
import com.android.ide.common.resources.TestResourceRepository
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.lang.reflect.Modifier

class ResourceClassGeneratorTest {
  @get:Rule
  val resourceFixture = ResourceRepositoryFixture()

  private val idManager = DynamicResourceIdManager()

  @Test
  fun testGenerate() {
    val testResourceRepository = resourceFixture.generateTestResources()
    val generator = ResourceClassGenerator.create(idManager, testResourceRepository, RES_AUTO)

    val name1 = "my.test.pkg.R"
    val clz1 = generateClass(generator, name1)!!
    assertThat(clz1.name).isEqualTo(name1)
    assertThat(Modifier.isPublic(clz1.modifiers)).isTrue()
    assertThat(Modifier.isFinal(clz1.modifiers)).isTrue()
    assertThat(Modifier.isInterface(clz1.modifiers)).isFalse()
    val r = clz1.getDeclaredConstructor().newInstance()
    assertThat(r).isNotNull()

    val name2 = "my.test.pkg.R\$string"
    val clz2 = generateClass(generator, name2)!!
    assertThat(clz2.name).isEqualTo(name2)
    assertThat(Modifier.isPublic(clz2.modifiers)).isTrue()
    assertThat(Modifier.isFinal(clz2.modifiers)).isTrue()
    assertThat(Modifier.isInterface(clz2.modifiers)).isFalse()
  }

  private fun generateClass(generator: ResourceClassGenerator, name: String): Class<*>? {
    val classLoader: ClassLoader = object : ClassLoader(ResourceClassGeneratorTest::class.java.classLoader) {
      override fun loadClass(s: String): Class<*> {
        if (!s.startsWith("java")) { // Don't try to load super class
          val data = generator.generate(s)
          if (data != null) {
            return defineClass(null, data, 0, data.size)
          }
        }
        return super.loadClass(s)
      }
    }
    return classLoader.loadClass(name)
  }

  private fun ResourceRepositoryFixture.generateTestResources(): TestResourceRepository {
    return createTestResources(
      namespace = RES_AUTO,
      pathToContents = mapOf(
        "layout/layout1.xml" to "<!--contents doesn't matter-->",
        "layout-land/layout1.xml" to "<!--contents doesn't matter-->",
        "values/styles.xml" to
          """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <style name="MyTheme.Dark" parent="android:Theme.Light">
                    <item name="android:textColor">#999999</item>
                    <item name="foo">?android:colorForeground</item>
                </style>
                <declare-styleable name="GridLayout_Layout">
                    <attr name="android:layout_width" />
                    <attr name="android:layout_height" />
                    <attr name="layout_columnSpan" format="integer" min="1" />
                    <attr name="layout_gravity">
                        <flag name="top" value="0x30" />
                        <flag name="bottom" value="0x50" />
                        <flag name="center_vertical" value="0x10" />
                    </attr>
                </declare-styleable>
            </resources>
          """.trimIndent(),
        "values/strings.xml" to
          """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <item type="id" name="action_bar_refresh" />
                <item type="dimen" name="dialog_min_width_major">45%</item>
                <string name="show_all_apps">All</string>
                <string name="menu_wallpaper">Wallpaper</string>
            </resources>
          """.trimIndent(),
        "values-es/strings.xml" to
          """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="show_all_apps">Todo</string>
            </resources>
          """.trimIndent()
      )
    )
  }
}
