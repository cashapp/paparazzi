package app.cash.paparazzi.internal

import app.cash.paparazzi.Environment
import app.cash.paparazzi.PaparazziRenderer
import app.cash.paparazzi.VerifyMode
import com.android.ide.common.rendering.api.ResourceNamespace
import com.google.common.io.Files
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.net.URL
import java.net.URLClassLoader

private fun loadRClassFromResources(): Class<*> {
    val resourceRClassUrl = PaparazziBinaryResourcesCallbackTest::class.java.classLoader.getResource("sample-debug-R.jar").let {
        assertThat(it).isNotNull()
        it!!
    }.also {
        File(it.path).apply {
            assertThat(this)
                    .exists()
                    .canRead()
        }
    }

    val urls: Array<URL> = arrayOf(URL("jar:${resourceRClassUrl}!/"))
    val ucl = URLClassLoader(urls)
    return Class.forName("app.cash.paparazzi.sample.R", true,   ucl)
}

private class TestablePaparazziBinaryResourcesCallback(logger: PaparazziLogger, environment: Environment)
    : PaparazziApplicationCallback(logger, environment, readResources(logger, loadRClassFromResources(), ResourceNamespace.RES_AUTO)) {

    val revisedEnvironment: Environment
        get() = super.environment
}

class PaparazziBinaryResourcesCallbackTest {
    @Test
    fun testHappyPath() {
        val resourceApkFile = PaparazziBinaryResourcesCallbackTest::class.java.classLoader.getResource("sample-debug.apk").let {
            assertThat(it).isNotNull()
            File(it!!.path)
        }.also {
            assertThat(it)
                    .exists()
                    .canRead()
        }

        val binaryCallback = TestablePaparazziBinaryResourcesCallback(
                PaparazziLogger(),
                buildEnvironment(resourceApkFile))
        binaryCallback.initResources()
        assertThat(binaryCallback.namespace).isEqualTo("http://schemas.android.com/apk/res/app.cash.paparazzi.sample")
        assertThat(binaryCallback.createXmlParserForFile("${binaryCallback.revisedEnvironment.resDir}/layout/launch.xml")).isNotNull

        val expectedViews = mutableListOf("LinearLayout", "ImageView", "TextView")
        val expectedEndViews = mutableListOf("ImageView", "TextView", "LinearLayout")
        val expectedViewAttributesCount = mutableListOf(5, 3, 7)
        binaryCallback.createXmlParserForFile("${binaryCallback.revisedEnvironment.resDir}/layout/launch.xml")!!.apply {
            assertThat(eventType).isEqualTo(XmlPullParser.START_DOCUMENT)
            next()
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    assertThat(name).isEqualTo(expectedViews.first())
                    expectedViews.removeAt(0)
                    assertThat(attributeCount).isEqualTo(expectedViewAttributesCount.first())
                    expectedViewAttributesCount.removeAt(0)
                } else if (eventType == XmlPullParser.END_TAG) {
                    assertThat(name).isEqualTo(expectedEndViews.first())
                    expectedEndViews.removeAt(0)
                }
                next()
            }
            assertThat(expectedViews).isEmpty()
            assertThat(expectedEndViews).isEmpty()
            assertThat(expectedViewAttributesCount).isEmpty()
        }

    }

    private fun buildEnvironment(resourceApkFile: File) =
        Environment(
                renderer = PaparazziRenderer.Application,
                verifyMode = VerifyMode.VerifyAgainstGolden,
                reportDir = Files.createTempDir().absolutePath,
                platformDir = "",
                goldenImagesFolder = "",
                resDir = Files.createTempDir().absolutePath,
                packageName = "app.cash.paparazzi.sample",
                compileSdkVersion = 28,
                mergedResourceValueDir = Files.createTempDir().absolutePath,
                apkPath = resourceApkFile.absolutePath,
                assetsDir = Files.createTempDir().absolutePath)
}