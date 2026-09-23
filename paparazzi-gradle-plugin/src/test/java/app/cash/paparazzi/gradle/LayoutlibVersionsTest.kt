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
import org.junit.Test

class LayoutlibVersionsTest {
  @Test
  fun defaultVersionIsKnown() {
    assertThat(LayoutlibVersions.isKnown(NATIVE_LIB_VERSION)).isTrue()
  }

  @Test
  fun knownMinimums() {
    assertThat(LayoutlibVersions.minLayoutlibApiFor("16.2.1")).isNull()
    assertThat(LayoutlibVersions.minLayoutlibApiFor("17.0.0")).isNull()
    assertThat(LayoutlibVersions.minLayoutlibApiFor("17.0.1")).isEqualTo("32.3.0")
  }

  @Test
  fun unknownVersionsInheritClosestLowerRequirement() {
    assertThat(LayoutlibVersions.minLayoutlibApiFor("17.0.2")).isEqualTo("32.3.0")
    assertThat(LayoutlibVersions.minLayoutlibApiFor("18.0.0")).isEqualTo("32.3.0")
    assertThat(LayoutlibVersions.minLayoutlibApiFor("16.2.9")).isNull()
    assertThat(LayoutlibVersions.minLayoutlibApiFor("15.1.2")).isNull()
  }

  @Test
  fun neverDowngrades() {
    assertThat(LayoutlibVersions.upgradeTo("32.0.1", "32.3.0")).isEqualTo("32.3.0")
    assertThat(LayoutlibVersions.upgradeTo("32.4.1", "32.3.0")).isNull()
    assertThat(LayoutlibVersions.upgradeTo("32.0.1", null)).isNull()
  }

  @Test
  fun bundledSdk() {
    assertThat(LayoutlibVersions.bundledSdkFor("16.2.3")).isEqualTo(36)
    assertThat(LayoutlibVersions.bundledSdkFor("17.0.3")).isEqualTo(37)
    // Unknown versions assume the closest known version at or below, else the oldest known.
    assertThat(LayoutlibVersions.bundledSdkFor("16.2.9")).isEqualTo(36)
    assertThat(LayoutlibVersions.bundledSdkFor("18.0.0")).isEqualTo(37)
    assertThat(LayoutlibVersions.bundledSdkFor("15.1.2")).isEqualTo(36)
    assertThat(LayoutlibVersions.bundledSdkFor("not-a-version")).isEqualTo(36)
  }

  @Test
  fun everyKnownVersionHasBundledSdk() {
    assertThat(LayoutlibVersions.bundledSdk.keys).containsExactlyElementsIn(LayoutlibVersions.knownVersions)
  }

  @Test
  fun resolveTargetSdk() {
    // compileSdk is capped at the bundled framework level.
    assertThat(resolve(explicit = null, compileSdk = 37, bundled = 36)).isEqualTo(36)
    assertThat(resolve(explicit = null, compileSdk = 35, bundled = 36)).isEqualTo(35)
    // No compileSdk: the bundled level.
    assertThat(resolve(explicit = null, compileSdk = null, bundled = 37)).isEqualTo(37)
    // An explicit testOptions.targetSdk is honored even above the bundled level.
    assertThat(resolve(explicit = 37, compileSdk = 37, bundled = 36)).isEqualTo(37)
    assertThat(resolve(explicit = 30, compileSdk = 37, bundled = 36)).isEqualTo(30)
  }

  private fun resolve(explicit: Int?, compileSdk: Int?, bundled: Int) =
    LayoutlibVersions.resolveTargetSdk(explicit, compileSdk, bundled)
}
