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
package app.cash.paparazzi.layoutlib

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Inflater

/** Minimal zip record parsing for [GoogleMaven.readEntry]. Zip64 archives aren't supported. */
internal object ZipRecords {
  /** Local header (30 bytes) plus the largest possible name and extra fields. */
  const val LOCAL_HEADER_SLACK = 30 + 0xFFFF * 2

  private const val CENTRAL_SIGNATURE = 0x02014b50
  private const val LOCAL_SIGNATURE = 0x04034b50

  class Record(val method: Int, val compressedSize: Int, val size: Int, val localHeaderOffset: Long)

  fun centralDirectoryEntry(bytes: ByteArray, name: String): Record? {
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    val target = name.toByteArray(Charsets.UTF_8)
    for (i in 0..bytes.size - 46) {
      if (buffer.getInt(i) != CENTRAL_SIGNATURE) continue
      val nameLength = buffer.getShort(i + 28).toInt() and 0xFFFF
      if (nameLength != target.size || i + 46 + nameLength > bytes.size) continue
      if (!bytes.copyOfRange(i + 46, i + 46 + nameLength).contentEquals(target)) continue
      return Record(
        method = buffer.getShort(i + 10).toInt() and 0xFFFF,
        compressedSize = buffer.getInt(i + 20),
        size = buffer.getInt(i + 24),
        localHeaderOffset = buffer.getInt(i + 42).toLong() and 0xFFFFFFFFL
      )
    }
    return null
  }

  /** [bytes] starts at the entry's local header. */
  fun readLocalEntry(bytes: ByteArray, record: Record): ByteArray {
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    if (buffer.getInt(0) != LOCAL_SIGNATURE) throw VerificationException("bad zip local header")
    val start = 30 + (buffer.getShort(26).toInt() and 0xFFFF) + (buffer.getShort(28).toInt() and 0xFFFF)
    val data = bytes.copyOfRange(start, start + record.compressedSize)
    return when (record.method) {
      0 -> data
      8 -> {
        val inflater = Inflater(true)
        try {
          inflater.setInput(data)
          ByteArray(record.size).also { out ->
            var read = 0
            while (read < out.size) {
              val n = inflater.inflate(out, read, out.size - read)
              if (n == 0 && (inflater.finished() || inflater.needsInput())) break
              read += n
            }
            if (read != out.size) throw VerificationException("truncated zip entry")
          }
        } finally {
          inflater.end()
        }
      }
      else -> throw VerificationException("unsupported zip compression method ${record.method}")
    }
  }
}
