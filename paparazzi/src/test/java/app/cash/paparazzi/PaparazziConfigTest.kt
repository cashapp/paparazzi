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

import android.widget.TextView
import app.cash.paparazzi.FileSubject.Companion.assertThat
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PaparazziConfigTest {
  @get:Rule
  val folder = TemporaryFolder()

  @Test
  fun recordModeWritesGoldenToConfiguredScreenshotsPath() {
    val screenshotsDir = folder.newFolder("screenshots")
    val golden = File(
      screenshotsDir,
      "images/app.cash.paparazzi_PaparazziConfigTest_recordModeWritesGoldenToConfiguredScreenshotsPath.png"
    )
    assertThat(golden).doesNotExist()

    runSnapshots(
      Paparazzi.Config(mode = Paparazzi.Mode.RECORD, screenshotsPath = screenshotsDir.path),
      "recordModeWritesGoldenToConfiguredScreenshotsPath"
    ) { paparazzi ->
      paparazzi.snapshot(TextView(paparazzi.context).apply { text = "hello" })
    }

    assertThat(golden).exists()
  }

  @Test
  fun verifyModePassesWhenSnapshotsMatchGolden() {
    val screenshotsDir = folder.newFolder("screenshots")
    val failuresDir = folder.newFolder("failures")

    runSnapshots(
      Paparazzi.Config(mode = Paparazzi.Mode.RECORD, screenshotsPath = screenshotsDir.path),
      "verifyModePassesWhenSnapshotsMatchGolden"
    ) { paparazzi ->
      paparazzi.snapshot(TextView(paparazzi.context).apply { text = "hello" })
    }

    // Verify the same view against the golden recorded above; must not throw.
    runSnapshots(
      Paparazzi.Config(
        mode = Paparazzi.Mode.VERIFY,
        screenshotsPath = screenshotsDir.path,
        failuresPath = failuresDir.path
      ),
      "verifyModePassesWhenSnapshotsMatchGolden"
    ) { paparazzi ->
      paparazzi.snapshot(TextView(paparazzi.context).apply { text = "hello" })
    }
  }

  @Test
  fun verifyModeWritesDeltaToConfiguredFailuresPath() {
    val screenshotsDir = folder.newFolder("screenshots")
    val failuresDir = folder.newFolder("failures")

    runSnapshots(
      Paparazzi.Config(mode = Paparazzi.Mode.RECORD, screenshotsPath = screenshotsDir.path),
      "verifyModeWritesDeltaToConfiguredFailuresPath"
    ) { paparazzi ->
      paparazzi.snapshot(TextView(paparazzi.context).apply { text = "hello" })
    }

    val delta = File(
      failuresDir,
      "delta-app.cash.paparazzi_PaparazziConfigTest_verifyModeWritesDeltaToConfiguredFailuresPath.png"
    )
    assertThat(delta).doesNotExist()

    assertThrows(AssertionError::class.java) {
      runSnapshots(
        Paparazzi.Config(
          mode = Paparazzi.Mode.VERIFY,
          screenshotsPath = screenshotsDir.path,
          failuresPath = failuresDir.path
        ),
        "verifyModeWritesDeltaToConfiguredFailuresPath"
      ) { paparazzi ->
        paparazzi.snapshot(TextView(paparazzi.context).apply { text = "goodbye" })
      }
    }

    assertThat(delta).exists()
  }

  @Test
  fun recordModeOverridesVerifySystemProperty() {
    val oldVerify = System.getProperty("paparazzi.test.verify")
    try {
      // Even when the Gradle plugin sets verify mode, code-level config must win.
      System.setProperty("paparazzi.test.verify", "true")
      val screenshotsDir = folder.newFolder("screenshots")
      val golden = File(
        screenshotsDir,
        "images/app.cash.paparazzi_PaparazziConfigTest_recordModeOverridesVerifySystemProperty.png"
      )

      runSnapshots(
        Paparazzi.Config(mode = Paparazzi.Mode.RECORD, screenshotsPath = screenshotsDir.path),
        "recordModeOverridesVerifySystemProperty"
      ) { paparazzi ->
        paparazzi.snapshot(TextView(paparazzi.context).apply { text = "hello" })
      }

      assertThat(golden).exists()
    } finally {
      if (oldVerify == null) {
        System.clearProperty("paparazzi.test.verify")
      } else {
        System.setProperty("paparazzi.test.verify", oldVerify)
      }
    }
  }

  @Test
  fun emptyConfigFallsBackToSystemProperties() {
    val oldVerify = System.getProperty("paparazzi.test.verify")
    val oldSnapshotDir = System.getProperty("paparazzi.snapshot.dir")
    try {
      // An empty config must behave exactly like the default Paparazzi constructor.
      val screenshotsDir = folder.newFolder("screenshots")
      System.setProperty("paparazzi.test.verify", "true")
      System.setProperty("paparazzi.snapshot.dir", screenshotsDir.path)

      assertThrows(AssertionError::class.java) {
        runSnapshots(Paparazzi.Config(), "emptyConfigFallsBackToSystemProperties") { paparazzi ->
          paparazzi.snapshot(TextView(paparazzi.context).apply { text = "hello" })
        }
      }
    } finally {
      if (oldVerify == null) {
        System.clearProperty("paparazzi.test.verify")
      } else {
        System.setProperty("paparazzi.test.verify", oldVerify)
      }
      if (oldSnapshotDir == null) {
        System.clearProperty("paparazzi.snapshot.dir")
      } else {
        System.setProperty("paparazzi.snapshot.dir", oldSnapshotDir)
      }
    }
  }

  private fun runSnapshots(config: Paparazzi.Config, testName: String, block: (Paparazzi) -> Unit) {
    val paparazzi = Paparazzi(config)
    try {
      paparazzi.setup(TestName("app.cash.paparazzi", "PaparazziConfigTest", testName))
      block(paparazzi)
    } finally {
      paparazzi.teardown()
    }
  }
}
