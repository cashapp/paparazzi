/*
 * Copyright (C) 2023 Square, Inc.
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

package app.cash.paparazzi.internal.apng

import okio.ByteString.Companion.toByteString

internal object PngConstants {

  val PNG_SIG = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10).toByteString()

  const val PNG_COLOR_TYPE_RGB: Byte = 2
  const val PNG_COLOR_TYPE_RGBA: Byte = 6
  const val PNG_BITS_PER_PIXEL: Byte = 8

  enum class Header(val bytes: ByteArray) {
    IHDR("IHDR".toByteArray()),
    ACTL("acTL".toByteArray()),
    FCTL("fcTL".toByteArray()),
    IDAT("IDAT".toByteArray()),
    FDAT("fdAT".toByteArray()),
    IEND("IEND".toByteArray())
  }
}
