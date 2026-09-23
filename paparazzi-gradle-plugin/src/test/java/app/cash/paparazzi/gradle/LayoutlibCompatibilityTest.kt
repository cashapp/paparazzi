/*
 * Copyright (C) 2026 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package app.cash.paparazzi.gradle

import com.google.common.truth.Truth.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import java.io.File

/**
 * Renders against every layoutlib version listed in `gradle/layoutlib-compat.properties`, so any
 * version added by `scripts/verify-layoutlib-version` is covered from then on.
 *
 * Restrict to specific versions with `-Dpaparazzi.layoutlib.versionsUnderTest=17.0.3,16.2.4`
 * (forwarded by the plugin module's Test task).
 */
@RunWith(Parameterized::class)
class LayoutlibCompatibilityTest(private val version: String) {
  @Test
  fun resolvesAlignedArtifacts() {
    val runtimeClasspath = dependencies("debugUnitTestRuntimeClasspath")
    val escaped = Regex.escape(version)
    assertThat(runtimeClasspath).containsMatch("$LAYOUTLIB:layoutlib:(\\S+ -> )?$escaped\\b")

    val minApi = LayoutlibVersions.minLayoutlibApiFor(version)
    if (minApi != null) {
      assertThat(runtimeClasspath).containsMatch("$LAYOUTLIB:layoutlib-api:[\\d.]+ -> ${Regex.escape(minApi)}\\b")
    } else {
      // Normal conflict resolution may still apply; just ensure no table-driven upgrade happened.
      LayoutlibVersions.minLayoutlibApi.values.toSet().forEach {
        assertThat(runtimeClasspath).doesNotContainMatch("layoutlib-api:[\\d.]+ -> ${Regex.escape(it)}\\b")
      }
    }

    assertThat(dependencies("layoutlibRuntime")).containsMatch("$LAYOUTLIB:layoutlib-runtime:$escaped\\b")
    assertThat(dependencies("layoutlibResources")).containsMatch("$LAYOUTLIB:layoutlib-resources:$escaped\\b")
  }

  /** Static, SHRINK and multi-frame gif snapshots (see LayoutlibVersionTest in the fixture). */
  @Test
  fun renders() {
    runner("testDebugUnitTest", "--rerun").build()
  }

  private fun dependencies(configuration: String): String =
    runner("dependencies", "--configuration", configuration).build().output

  private fun runner(vararg tasks: String): GradleRunner {
    ensureFixtureFiles()
    return GradleRunner.create()
      .withPluginClasspath()
      .withProjectDir(FIXTURE)
      .withArguments(*tasks, "-PtestLayoutlibVersion=$version", "--stacktrace")
  }

  companion object {
    private const val LAYOUTLIB = "com\\.android\\.tools\\.layoutlib"
    private val FIXTURE = File("src/test/projects/layoutlib-version-override")
    private val COMPAT_FILE = File("../gradle/layoutlib-compat.properties")

    @JvmStatic
    @Parameters(name = "layoutlib {0}")
    fun versions(): List<String> {
      val filter = System.getProperty("paparazzi.layoutlib.versionsUnderTest")
        ?.split(',')?.map(String::trim)?.filter(String::isNotEmpty)
      if (!filter.isNullOrEmpty()) return filter
      check(COMPAT_FILE.isFile) { "Missing $COMPAT_FILE" }
      return LayoutlibVersions.knownVersions
    }

    private fun ensureFixtureFiles() {
      File(FIXTURE, "settings.gradle").takeUnless { it.exists() }?.apply {
        writeText("apply from: \"../test.settings.gradle\"")
        deleteOnExit()
      }
      File(FIXTURE, "gradle.properties").takeUnless { it.exists() }?.apply {
        writeText("android.dependencyResolutionAtConfigurationTime.disallow=true\n")
        deleteOnExit()
      }
    }
  }
}
