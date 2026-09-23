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

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Duration

/** Minimal client for Google's Maven repository with an on-disk cache. */
internal class GoogleMaven(
  private val cacheDir: File,
  private val baseUrl: String = "https://dl.google.com/android/maven2"
) {
  private val client = HttpClient.newBuilder()
    .followRedirects(HttpClient.Redirect.NORMAL)
    .connectTimeout(Duration.ofSeconds(60))
    .build()

  fun url(artifact: String, version: String, classifier: String? = null): String {
    val suffix = classifier?.let { "-$it" }.orEmpty()
    return "$baseUrl/${GROUP_PATH}/$artifact/$version/$artifact-$version$suffix.jar"
  }

  fun exists(url: String): Boolean {
    val request = HttpRequest.newBuilder(URI(url)).method("HEAD", HttpRequest.BodyPublishers.noBody())
      .timeout(Duration.ofSeconds(60)).build()
    return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200
  }

  /** Downloads [url] (cached). */
  fun download(url: String): File = cached(url, suffix = "") { request -> request }

  /**
   * Downloads the last [bytes] of [url] (cached). A zip's central directory sits at its end, so
   * this lists entries of large archives (e.g. ~80MB layoutlib-runtime jars) cheaply.
   */
  fun tail(url: String, bytes: Int): File =
    cached(url, suffix = ".tail$bytes") { request -> request.header("Range", "bytes=-$bytes") }

  /** Published versions of [artifact] in the layoutlib group, in ascending order. */
  fun versions(artifact: String): List<String> {
    val metadata = download("$baseUrl/$GROUP_PATH/$artifact/maven-metadata.xml").readText()
    return Regex("<version>([^<]+)</version>").findAll(metadata).map { it.groupValues[1] }.toList()
      .sortedWith(VersionOrder)
  }

  /** Always refetches (metadata changes as new versions publish). */
  fun versionsFresh(artifact: String): List<String> {
    val url = "$baseUrl/$GROUP_PATH/$artifact/maven-metadata.xml"
    cacheFile(url, "").delete()
    return versions(artifact)
  }

  private fun cached(url: String, suffix: String, customize: (HttpRequest.Builder) -> HttpRequest.Builder): File {
    val target = cacheFile(url, suffix)
    if (target.isFile) return target
    target.parentFile.mkdirs()
    val request = customize(HttpRequest.newBuilder(URI(url)).timeout(Duration.ofMinutes(10))).GET().build()
    val partial = File(target.path + ".part")
    val response = client.send(request, HttpResponse.BodyHandlers.ofFile(partial.toPath()))
    if (response.statusCode() !in 200..299) {
      partial.delete()
      throw VerificationException("download failed (HTTP ${response.statusCode()}): $url")
    }
    Files.move(partial.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    return target
  }

  private fun cacheFile(url: String, suffix: String): File =
    File(cacheDir, url.removePrefix(baseUrl).trimStart('/').replace(Regex("[^\\w./-]"), "_") + suffix)

  companion object {
    const val GROUP_PATH = "com/android/tools/layoutlib"
  }
}
