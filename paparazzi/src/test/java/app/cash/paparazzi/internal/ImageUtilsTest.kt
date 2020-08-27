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
package app.cash.paparazzi.internal

import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

class ImageUtilsTest {

    @Rule
    @JvmField
    var temporaryFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun testSameImage() {
        val diffImageFile = temporaryFolder.newFile("diff.png")
        diffImageFile.delete()
        ImageUtils.assertImageSimilar(
                loadTestImage("golden-image.png"),
                loadTestImage("golden-image.png"),
                0.01,
                diffImageFile)

        Assert.assertFalse(diffImageFile.exists())
    }

    @Test
    fun testImageDiffBelowThreshold() {
        val diffImageFile = temporaryFolder.newFile("diff.png")
        diffImageFile.delete()
        ImageUtils.assertImageSimilar(
                loadTestImage("golden-image.png"),
                loadTestImage("generated-less-than-0dot1-percent-diff.png"),
                0.1,
                diffImageFile)

        Assert.assertFalse(diffImageFile.exists())
    }

    @Test
    fun testImageDiffAboveThreshold() {
        val diffImageFile = temporaryFolder.newFile("diff.png")
        diffImageFile.delete()
        ImageUtils.assertImageSimilar(
                loadTestImage("golden-image.png"),
                loadTestImage("generated-0dot11-percent-diff.png"),
                0.1,
                diffImageFile)

        Assert.assertTrue(diffImageFile.exists())

        diffImageFile.delete()
        ImageUtils.assertImageSimilar(
                loadTestImage("golden-image.png"),
                loadTestImage("generated-0dot11-percent-diff.png"),
                0.2,
                diffImageFile)

        Assert.assertFalse(diffImageFile.exists())
    }

    private fun loadTestImage(imageFileName: String): BufferedImage =
            ImageUtilsTest::class.java.classLoader.getResource(imageFileName)!!
                    .openStream()
                    .let { ImageIO.read(it) }
}