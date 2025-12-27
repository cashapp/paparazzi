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

import com.android.ide.common.resources.configuration.LocaleQualifier
import java.awt.image.BufferedImage
import java.io.Closeable
import java.util.Locale

public interface SnapshotHandler : Closeable {
  public fun newFrameHandler(snapshot: Snapshot, frameCount: Int, fps: Int): FrameHandler

  public interface FrameHandler : Closeable {
    public fun handle(image: BufferedImage)
  }
}

public fun detectMaxPercentDifferenceDefault(): Double =
  System.getProperty("app.cash.paparazzi.maxPercentDifferenceDefault")?.toDoubleOrNull() ?: 0.01

/**
 * Returns a LocaleQualifier for snapshots.
 *
 * Uses the provided `locale` if passed in, otherwise falls back to the
 * `app.cash.paparazzi.localeDefault` system property, and finally the
 * default `LocaleQualifier()` if neither are available.
 *
 * @param locale Optional Locale to use for snapshot tests.
 * @return LocaleQualifier for snapshot tests.
 */
internal fun detectLocaleQualifierDefault(locale: String?): LocaleQualifier {
  val propLocale = System.getProperty("app.cash.paparazzi.localeDefault")
    ?.takeIf { it.isNotEmpty() }
  return when {
    locale != null -> LocaleQualifier.getQualifier(locale)
    propLocale != null -> LocaleQualifier.getQualifier(propLocale)
    else -> LocaleQualifier()
  }
}
