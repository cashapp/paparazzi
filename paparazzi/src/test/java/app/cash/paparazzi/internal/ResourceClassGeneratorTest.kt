package app.cash.paparazzi.internal

import app.cash.paparazzi.internal.renderresources.FrameworkResourceIdsProvider
import app.cash.paparazzi.internal.renderresources.ResourceClassGenerator
import app.cash.paparazzi.internal.renderresources.ResourceIdManagerModelModule
import app.cash.paparazzi.internal.renderresources.TempIdManager
import app.cash.paparazzi.internal.resources.AAR_LIBRARY_NAME
import app.cash.paparazzi.internal.resources.AarSourceResourceRepository
import app.cash.paparazzi.internal.resources.LocalResourceRepository
import app.cash.paparazzi.internal.resources.MultiResourceRepository
import app.cash.paparazzi.internal.resources.TEST_DATA_DIR
import app.cash.paparazzi.internal.resources.makeAarRepositoryFromExplodedAar
import app.cash.paparazzi.internal.resources.resolveProjectPath
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceNamespace.RES_AUTO
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.resources.ResourceVisitor
import com.android.ide.common.resources.SingleNamespaceResourceRepository
import com.android.ide.common.resources.TestResourceRepository
import com.android.resources.ResourceType
import com.google.common.collect.ListMultimap
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.lang.reflect.Modifier

class ResourceClassGeneratorTest {
  @get:Rule
  val resourceFixture = ResourceRepositoryFixture()

  private val idManager = TempIdManager(
    module = ResourceIdManagerModelModule.noNamespacingApp(),
    searchFrameworkIds = false,
    frameworkResourceIdsProvider = FrameworkResourceIdsProvider.getInstance()
  )

  @Test
  fun testGenerate() {
    val testResourceRepository = resourceFixture.generateTestResources()
    val generator = ResourceClassGenerator.create(idManager, testResourceRepository, RES_AUTO)

    val name1 = "my.test.pkg.R"
    val clz1 = generateClass(generator, name1)
    assertThat(clz1.name).isEqualTo(name1)
    assertThat(Modifier.isPublic(clz1.modifiers)).isTrue()
    assertThat(Modifier.isFinal(clz1.modifiers)).isTrue()
    assertThat(Modifier.isInterface(clz1.modifiers)).isFalse()
    val r = clz1.getDeclaredConstructor().newInstance()
    assertThat(r).isNotNull()

    val name2 = "my.test.pkg.R\$string"
    val clz2 = generateClass(generator, name2)
    assertThat(clz2.name).isEqualTo(name2)
    assertThat(Modifier.isPublic(clz2.modifiers)).isTrue()
    assertThat(Modifier.isFinal(clz2.modifiers)).isTrue()
    assertThat(Modifier.isInterface(clz2.modifiers)).isFalse()

    val field1 = clz2.getField("menu_wallpaper")
    val value1 = field1[null]
    assertThat(field1.type).isSameInstanceAs(Integer.TYPE)
    assertThat(value1).isNotNull()
    assertThat(clz2.fields.size).isEqualTo(2)
    val field2 = clz2.getField("show_all_apps")
    assertThat(field2).isNotNull()
    assertThat(field2.type).isSameInstanceAs(Integer.TYPE)
    assertThat(Modifier.isPublic(field2.modifiers)).isTrue()
    assertThat(Modifier.isFinal(field2.modifiers)).isTrue()
    assertThat(Modifier.isStatic(field2.modifiers)).isTrue()
    assertThat(Modifier.isSynchronized(field2.modifiers)).isFalse()
    assertThat(Modifier.isTransient(field2.modifiers)).isFalse()
    assertThat(Modifier.isStrict(field2.modifiers)).isFalse()
    assertThat(Modifier.isVolatile(field2.modifiers)).isFalse()
    val r2 = clz2.getDeclaredConstructor().newInstance()
    assertThat(r2).isNotNull()

    // Make sure the ids match what we've dynamically allocated in the resource repository
    val resource = idManager.findById(clz2.getField("menu_wallpaper").get(null) as Int)!!
    assertEquals(ResourceType.STRING, resource.resourceType)
    assertEquals("menu_wallpaper", resource.name)
    assertEquals(
      clz2.getField("menu_wallpaper").get(null),
      idManager.getOrGenerateId(ResourceReference(RES_AUTO, ResourceType.STRING, "menu_wallpaper"))
    )
    assertEquals(
      clz2.getField("show_all_apps").get(null),
      idManager.getOrGenerateId(ResourceReference(RES_AUTO, ResourceType.STRING, "show_all_apps"))
    )

    // Test attr class!
    val name3 = "my.test.pkg.R\$attr"
    val clz3 = generateClass(generator, name3)
    assertNotNull(clz3)
    assertEquals(name3, clz3.name)
    assertTrue(Modifier.isPublic(clz3.modifiers))
    assertTrue(Modifier.isFinal(clz3.modifiers))
    assertFalse(Modifier.isInterface(clz3.modifiers))
    assertEquals(2, clz3.fields.size)
    val field3 = clz3.getField("layout_gravity")
    assertNotNull(field3)
    val gravityValue = field3.get(null) as Int
    val layoutColumnSpanValue = clz3.getField("layout_columnSpan").get(null)

    // Test style class
    styleTest(generator)

    // Run the same test to check caching.
    styleTest(generator)

    // Test styleable class!
    styleableTest(generator, gravityValue, layoutColumnSpanValue)

    // Run the same test again to ensure that caching is working as expected.
    styleableTest(generator, gravityValue, layoutColumnSpanValue)

    val name4 = "my.test.pkg.R\$id"
    val clz4 = generateClass(generator, name4)
    clz4.getDeclaredConstructor().newInstance()
    assertEquals(name4, clz4.name)
    // getEnclosingClass() results in generating all R classes. So, this should be called at the end
    // so that tests for caching work as expected.
    val enclosingClass = clz4.enclosingClass
    assertNotNull(enclosingClass)
  }

  @Test
  fun testStyleableMerge() {
    val repositoryA = resourceFixture.createTestResources(
      RES_AUTO,
      mapOf(
        "values/styles.xml" to
          """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <attr name="app_declared_attr" />
                <declare-styleable name="Styleable1">
                </declare-styleable>
                <declare-styleable name="Styleable.with.dots">
                    <attr name="app_declared_attr" />
                    <attr name="some_attr" />
                    <attr name="android:layout_height" />
                </declare-styleable>
                <declare-styleable name="AppStyleable">
                </declare-styleable></resources>
            """.trimIndent()
      )
    )
    val resourcesA = LocalResourceRepositoryDelegate("A", repositoryA)
    val aarPath = resolveProjectPath("$TEST_DATA_DIR/my_aar_lib/res")
    val libraryRepository = AarSourceResourceRepository.create(aarPath, AAR_LIBRARY_NAME)
    val combinedResources = TestMultiResourceRepository(listOf(resourcesA), listOf(libraryRepository))

    // 3 declared in the library, 3 declared in the "project", 2 of them are duplicated so:
    //
    //    1 unique styleable from the app
    //    1 unique styleable from the library
    //    2 styles declared in both
    //------------------------------------------
    //    4 total styles
    assertEquals(4, combinedResources.getResourceNames(RES_AUTO, ResourceType.STYLEABLE).size)

    val generator = ResourceClassGenerator.create(idManager, combinedResources, RES_AUTO)
    val name1 = "my.test.pkg.R"
    val clz1 = generateClass(generator, name1)
    assertEquals(name1, clz1.getName())

    clz1.getDeclaredConstructor().newInstance()

    val name2 = "my.test.pkg.R\$styleable"
    val clz2 = generateClass(generator, name2)
    assertEquals(name2, clz2.getName())
    val rStyleable = clz2.getDeclaredFields().map { it.toString() }
    assertThat(rStyleable).containsExactly(
      "public static final int[] my.test.pkg.R\$styleable.Styleable_with_underscore",
      "public static final int my.test.pkg.R\$styleable.Styleable_with_underscore_app_attr1",
      "public static final int my.test.pkg.R\$styleable.Styleable_with_underscore_app_attr2",
      "public static final int my.test.pkg.R\$styleable.Styleable_with_underscore_app_attr3",
      "public static final int my.test.pkg.R\$styleable.Styleable_with_underscore_android_colorForeground",
      "public static final int my.test.pkg.R\$styleable.Styleable_with_underscore_android_icon",
      "public static final int[] my.test.pkg.R\$styleable.Styleable_with_dots",
      "public static final int my.test.pkg.R\$styleable.Styleable_with_dots_app_declared_attr",
      "public static final int my.test.pkg.R\$styleable.Styleable_with_dots_some_attr",
      "public static final int my.test.pkg.R\$styleable.Styleable_with_dots_android_layout_height",
      "public static final int[] my.test.pkg.R\$styleable.AppStyleable",
      "public static final int[] my.test.pkg.R\$styleable.Styleable1",
      "public static final int my.test.pkg.R\$styleable.Styleable1_some_attr",
    )
    assertNotNull(clz2.getDeclaredConstructor().newInstance())

    val name3 = "my.test.pkg.R\$attr"
    val clz3 = generateClass(generator, name3)
    assertEquals(name3, clz3.getName())
    val rAttrFields = clz3.getDeclaredFields().map { it.toString() }
    assertThat(rAttrFields).containsExactly(
      "public static final int my.test.pkg.R\$attr.some_attr",
      "public static final int my.test.pkg.R\$attr.app_attr2",
      "public static final int my.test.pkg.R\$attr.app_declared_attr"
    )
    assertNotNull(clz3.getDeclaredConstructor().newInstance())
  }

  @Test
  fun testIndexOverflow() {
    val repository = resourceFixture.createTestResources(
      RES_AUTO,
      mapOf(
        "values/styles.xml" to
          """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
              <declare-styleable name="AppStyleable">
                ${(0..999).joinToString { """<attr name="overflow_$it" />""" }}
              </declare-styleable>
            </resources>
          """.trimIndent()
      )
    )
    assertEquals(1, repository.getResources(RES_AUTO, ResourceType.STYLEABLE).size())

    val generator = ResourceClassGenerator.create(idManager, repository, RES_AUTO)

    val name = "my.test.pkg.R\$styleable"
    val clz = generateClass(generator, name)
    assertEquals(name, clz.name)
    val rClass = clz.getDeclaredConstructor().newInstance()
    val iArray = clz.getDeclaredField("AppStyleable").get(rClass) as IntArray
    assertEquals(1000, iArray.size)
  }

  @Test
  fun testWithAars() {
    val aarRepo = makeAarRepositoryFromExplodedAar("my_aar_lib")
    val generator = ResourceClassGenerator.create(idManager, aarRepo, RES_AUTO)

    val clz = generateClass(generator, "pkg.R\$id")
    clz.getDeclaredConstructor().newInstance()
    val declaredFields = clz.getDeclaredFields()
    assertThat(declaredFields.map { it.name }).containsExactly("id1", "id2", "id3")
    styleableTestWithAars(generator)
    // Run same test again to ensure that caching is working as expected.
    styleableTestWithAars(generator)
  }

  companion object {
    private fun styleTest(generator: ResourceClassGenerator) {
      val name = "my.test.pkg.R\$style"
      val clz = generateClass(generator, name)
      clz.getDeclaredConstructor().newInstance()
      assertEquals(name, clz.getName())
      assertThat(Modifier.isPublic(clz.modifiers)).isTrue()
      assertThat(Modifier.isFinal(clz.modifiers)).isTrue()
      assertThat(Modifier.isInterface(clz.modifiers)).isFalse()
    }

    private fun styleableTest(generator: ResourceClassGenerator, gravityValue: Int, layoutColumnSpanValue: Any) {
      val name = "my.test.pkg.R\$styleable"
      val clz = generateClass(generator, name)
      val r = clz.getDeclaredConstructor().newInstance()
      assertEquals(name, clz.getName())
      assertTrue(Modifier.isPublic(clz.modifiers))
      assertTrue(Modifier.isFinal(clz.modifiers))
      assertFalse(Modifier.isInterface(clz.modifiers))

      val field1 = clz.getField("GridLayout_Layout")
      val value1 = field1[null]
      assertEquals("[I", field1.type.name)
      assertNotNull(value1)
      assertEquals(5, clz.getFields().size)
      val field2 = clz.getField("GridLayout_Layout_android_layout_height")
      assertNotNull(field2)
      assertNotNull(clz.getField("GridLayout_Layout_android_layout_width"))
      assertNotNull(clz.getField("GridLayout_Layout_layout_columnSpan"))
      assertThat(field2.type).isSameInstanceAs(Integer.TYPE)
      assertTrue(Modifier.isPublic(field2.modifiers))
      assertTrue(Modifier.isFinal(field2.modifiers))
      assertTrue(Modifier.isStatic(field2.modifiers))
      assertFalse(Modifier.isSynchronized(field2.modifiers))
      assertFalse(Modifier.isTransient(field2.modifiers))
      assertFalse(Modifier.isStrict(field2.modifiers))
      assertFalse(Modifier.isVolatile(field2.modifiers))

      val indices = clz.getField("GridLayout_Layout").get(r) as IntArray

      val layoutColumnSpanIndex = clz.getField("GridLayout_Layout_layout_columnSpan").get(null) as Int
      assertEquals(indices[layoutColumnSpanIndex], layoutColumnSpanValue)

      val gravityIndex = clz.getField("GridLayout_Layout_layout_gravity").get(null) as Int
      assertEquals(indices[gravityIndex], gravityValue)

      // The exact source order of attributes must be matched such that array indexing of the styleable arrays
      // reaches the right elements. For this reason, we use a LinkedHashMap in StyleableResourceValue.
      // Without this, using the v7 GridLayout widget and putting app:layout_gravity="left" on a child will
      // give value conversion errors.
      assertEquals(2, layoutColumnSpanIndex)
      assertEquals(3, gravityIndex)
    }

    private fun styleableTestWithAars(generator: ResourceClassGenerator) {
      val clz = generateClass(generator, "pkg.R\$styleable")
      clz.getDeclaredConstructor().newInstance()

      assertNotNull(clz.getDeclaredField("Styleable_with_dots"))
      val styleable3 = clz.getDeclaredField("Styleable_with_underscore")
      assertSame(IntArray::class.java, styleable3.type)
      val array = styleable3.get(null) as IntArray
      var idx = clz.getDeclaredField("Styleable_with_underscore_android_icon").get(null) as Int
      assertEquals(0x01010002, array[idx])
      idx = clz.getDeclaredField("Styleable_with_underscore_android_colorForeground").get(null) as Int
      assertEquals(0x01010030, array[idx])
    }

    private fun generateClass(generator: ResourceClassGenerator, name: String): Class<*> {
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
}

private class TestMultiResourceRepository(
  locals: List<LocalResourceRepository>,
  aars: List<AarSourceResourceRepository>
) : MultiResourceRepository("test repository") {
  init {
    setChildren(locals, aars)
  }
}

private class LocalResourceRepositoryDelegate(
  displayName: String,
  private val myDelegate: TestResourceRepository
) : LocalResourceRepository(displayName), SingleNamespaceResourceRepository {
  override fun getMap(namespace: ResourceNamespace, type: ResourceType): ListMultimap<String, ResourceItem>? =
    myDelegate.getMap(namespace, type)

  override fun getNamespace(): ResourceNamespace = myDelegate.namespace

  override fun getPackageName(): String? = myDelegate.packageName

  override fun accept(visitor: ResourceVisitor): ResourceVisitor.VisitResult = myDelegate.accept(visitor)

  override fun getNamespaces(): Set<ResourceNamespace> = myDelegate.namespaces

  override fun getLeafResourceRepositories(): Collection<SingleNamespaceResourceRepository> =
    myDelegate.leafResourceRepositories
}
