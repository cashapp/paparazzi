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
package app.cash.paparazzi.internal

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LayoutlibCompatTest {
  @get:Rule
  val temp = TemporaryFolder()

  @Test
  fun currentVersionIsTracked() {
    val version = checkNotNull(LayoutlibCompat.version) { "paparazzi.layoutlib.version not set" }
    assertThat(LayoutlibCompat.ICU_DATA_FILES).containsKey(version.toString())
  }

  @Test
  fun currentRuntimeShipsTrackedIcuFile() {
    val runtimeRoot = File(System.getProperty("paparazzi.layoutlib.runtime.root"))
    val icu = LayoutlibCompat.icuDataFile(File(runtimeRoot, "data"))
    assertThat(icu.isFile).isTrue()
    assertThat(icu.name).isEqualTo(LayoutlibCompat.ICU_DATA_FILES[LayoutlibCompat.version.toString()])
  }

  @Test
  fun usesTableForKnownVersion() {
    val data = dataDirWith("icudt76l.dat", "icudt78l.dat")
    assertThat(LayoutlibCompat.icuDataFile(data, LayoutlibVersion.parse("16.2.4")).name).isEqualTo("icudt76l.dat")
    assertThat(LayoutlibCompat.icuDataFile(data, LayoutlibVersion.parse("17.0.1")).name).isEqualTo("icudt78l.dat")
  }

  @Test
  fun fallsBackToDetectionForUnknownVersion() {
    val data = dataDirWith("icudt80l.dat")
    assertThat(LayoutlibCompat.icuDataFile(data, LayoutlibVersion.parse("18.0.0")).name).isEqualTo("icudt80l.dat")
    assertThat(LayoutlibCompat.icuDataFile(data, null).name).isEqualTo("icudt80l.dat")
  }

  private fun dataDirWith(vararg icuFiles: String): File {
    val data = temp.newFolder("data")
    val icu = File(data, "icu").apply { mkdirs() }
    icuFiles.forEach { File(icu, it).writeText("") }
    return data
  }
}
