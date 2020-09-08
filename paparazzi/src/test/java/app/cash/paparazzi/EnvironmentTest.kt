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

import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EnvironmentTest {
    @Rule
    @JvmField
    var temporaryFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun fullRoundTest() {
        val environment = Environment(
                renderer = PaparazziRenderer.Library,
                packageName = "test.example",
                platformDir = "/tmp/platform",
                compileSdkVersion = 22,
                resDir = "/tmp/res/folder",
                assetsDir = "/tmp/assets/folder",
                reportDir = "/tmp/reports/paparazzi",
                goldenImagesFolder = "/tmp/golden/images",
                apkPath = "/tmp/apk/path",
                mergedResourceValueDir = "/tmp/build/resources"
        )

      temporaryFolder.newFile("test_environment.txt").also { environmentFile ->
        environmentFile.bufferedWriter().use {
          dumpEnvironment(environment, it)
        }

        val readEnvironment = detectEnvironment(environmentFile.absolutePath)

        Assert.assertEquals(environment, readEnvironment)
      }
    }
}

