/*
 * Copyright (C) 2019 Square, Inc.
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
package app.cash.paparazzi

import app.cash.paparazzi.internal.ImageUtilsTest
import com.android.utils.ILogger
import org.junit.Assert
import org.junit.AssumptionViolatedException
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.InputStream
import java.util.*
import java.util.concurrent.atomic.AtomicReference

class PaparazziMediaVerifierTest {
    @Rule
    @JvmField
    var temporaryFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var environment: Environment
    private lateinit var generatedImagesFolder: File
    private val snapshot = Snapshot(name = "this is a snapshot test",
            testName = TestName("com.package", "classname", "method_name"),
            file = null,
            tags = listOf("tag1"),
            timestamp = Date())

    private lateinit var logger: FakeLogger
    private lateinit var underTest: PaparazziMediaVerifier

    @Before
    fun setup() {
        generatedImagesFolder = temporaryFolder.newFolder()
        environment = Environment(
                renderer = PaparazziRenderer.Application,
                reportDir = "/tmp/report",
                goldenImagesFolder = temporaryFolder.newFolder("golden").absolutePath,
                packageName = "com.package",
                mergedResourceValueDir = "/tmp/resources",
                resDir = "/tmp/res",
                compileSdkVersion = 25,
                assetsDir = "/tmp/assets",
                apkPath = "/tmp/apk.apk",
                platformDir = "/sdk/platform/"
        )
        logger = FakeLogger()
        underTest = PaparazziMediaVerifier(environment, logger)
    }

    @Test
    fun testGoldenImageFile() {
        Assert.assertEquals(
                File("${environment.goldenImagesFolder}/com.package.classname/method_name/this_is_a_snapshot_test.png"),
                getGoldenImagePath(environment, snapshot)
        )
    }

    @Test
    fun testGenericImageFile() {
        Assert.assertEquals(
                File("/tmp/diff/com.package.classname/method_name/this_is_a_snapshot_test.png"),
                getGenericImagePath("/tmp/diff/", snapshot)
        )
    }

    @Test
    fun testDoesNotVerifyNonPngFiles() {
        val generatedImage = copyToGeneratedFolder("golden-image.png").let {
            File(it.absolutePath.replaceAfterLast(".", "mov")).also {  target ->
                it.renameTo(target)
            }
        }

        underTest.verify(snapshot, generatedImage)
        Assert.assertTrue(logger.warnings.first().contains(" Only supporting PNG snapshot verifications."))
    }

    @Test
    fun testCopyToGoldenImagesIfMissingGoldenImage() {
        val generatedImage = copyToGeneratedFolder("golden-image.png")
        val expectedGoldenImage = getGoldenImagePath(environment, snapshot)

        Assert.assertFalse(expectedGoldenImage.exists())

        val assumptions = AtomicReference<String>(null)
        try {
            underTest.verify(snapshot, generatedImage)
        } catch (assumption: AssumptionViolatedException) {
            //this is great!
            assumptions.set(assumption.message)
        }

        val warningMessage = logger.warnings.first()
        Assert.assertTrue(warningMessage.contains(" was missing. Copied the generated image to "))
        Assert.assertEquals(warningMessage, assumptions.get())

        Assert.assertTrue(expectedGoldenImage.exists())
        Assert.assertArrayEquals(
                generatedImage.readBytes(),
                expectedGoldenImage.readBytes())
    }

    @Test
    fun testVerifyMatchingImages() {
        val generatedImage = copyToGeneratedFolder("golden-image.png")
        copyToGoldenFolder("golden-image.png")

        underTest.verify(snapshot, generatedImage)
    }

    @Test
    fun testVerifyNonMatchingImages() {
        val generatedImage = copyToGeneratedFolder("generated-0dot11-percent-diff.png")
        copyToGoldenFolder("golden-image.png")

        val gotAssertion = AtomicReference<String>(null)
        try {
            underTest.verify(snapshot, generatedImage)
        } catch (assertion: AssertionError) {
            //this is great!
            gotAssertion.set(assertion.message)
        }

        Assert.assertTrue(gotAssertion.get().contains("DOES NOT match golden-image"))
    }

    private fun loadTestImage(imageFileName: String): InputStream =
            ImageUtilsTest::class.java.classLoader.getResource(imageFileName)!!
                    .openStream()

    private fun copyToGoldenFolder(file: String): File {
        return getGoldenImagePath(environment, snapshot).also { outputFile ->
            loadTestImage(file).use { input ->
                outputFile.parentFile.mkdirs()
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    private fun copyToGeneratedFolder(file: String): File {
        return File(generatedImagesFolder, file).also { outputFile ->
            loadTestImage(file).use { input ->
                outputFile.parentFile.mkdirs()
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }
}

private class FakeLogger : ILogger {
    val errors = mutableListOf<String>()
    val warnings = mutableListOf<String>()
    val infos = mutableListOf<String>()
    val verboses = mutableListOf<String>()

    override fun error(t: Throwable?, msgFormat: String?, vararg args: Any?) {
        errors += msgFormat!!
    }

    override fun warning(msgFormat: String?, vararg args: Any?) {
        warnings += msgFormat!!
    }

    override fun info(msgFormat: String?, vararg args: Any?) {
        infos += msgFormat!!
    }

    override fun verbose(msgFormat: String?, vararg args: Any?) {
        verboses += msgFormat!!
    }
}