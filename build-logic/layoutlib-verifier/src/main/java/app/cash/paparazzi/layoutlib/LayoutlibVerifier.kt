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
import java.util.zip.ZipFile

internal class VerificationException(message: String) : RuntimeException(message)

/**
 * Static compatibility checks of a layoutlib release against Paparazzi. Rendering is verified
 * separately by `LayoutlibCompatibilityTest`: static checks can't see behavioral changes (e.g.
 * 17.0.3 dropping `BridgeRenderSession.getImage()` while the API default still exists).
 */
internal class LayoutlibVerifier(
  private val maven: GoogleMaven,
  private val defaultVersion: String,
  private val pinnedApiVersion: String,
  private val apiFloor: String,
  private val paparazziJar: File,
  private val log: (step: String, message: String) -> Unit
) {
  fun verify(version: String): CompatEntry {
    checkArtifacts(version)
    val icu = icuDataFile(version)
    val sdk = bundledSdk(version)
    val layoutlibJar = maven.download(maven.url("layoutlib", version))
    val minApi = minimumApi(version, layoutlibJar)
    checkLinkage(version, layoutlibJar, minApi ?: pinnedApiVersion)
    checkResources(version)
    return CompatEntry(icu, sdk, minApi)
  }

  private fun checkArtifacts(version: String) {
    val urls = listOf(maven.url("layoutlib", version), maven.url("layoutlib-resources", version)) +
      CLASSIFIERS.map { maven.url("layoutlib-runtime", version, it) }
    urls.forEach { if (!maven.exists(it)) throw VerificationException("missing artifact $it") }
    log("artifacts", "layoutlib, layoutlib-resources, layoutlib-runtime [${CLASSIFIERS.joinToString()}] present")
  }

  private fun icuDataFile(version: String): String {
    val byClassifier = CLASSIFIERS.associateWith { classifier ->
      val tail = maven.tail(maven.url("layoutlib-runtime", version, classifier), bytes = 1_000_000).readBytes()
      val found = ICU_ENTRY.findAll(String(tail, Charsets.ISO_8859_1)).map { it.groupValues[1] }.toSortedSet()
      found.singleOrNull()
        ?: throw VerificationException(
          "expected exactly one ICU data file in layoutlib-runtime $classifier, found $found"
        )
    }
    val names = byClassifier.values.toSet()
    val icu =
      names.singleOrNull() ?: throw VerificationException("ICU data file differs across classifiers: $byClassifier")
    log("icu", "$icu (all classifiers)")
    return icu
  }

  /** The framework API level layoutlib bundles (`ro.build.version.sdk` in layoutlib-runtime's build.prop). */
  private fun bundledSdk(version: String): Int {
    val byClassifier = CLASSIFIERS.associateWith { classifier ->
      val prop = maven.readEntry(maven.url("layoutlib-runtime", version, classifier), "build.prop")
        ?: throw VerificationException("layoutlib-runtime $classifier has no build.prop")
      SDK_PROPERTY.find(String(prop, Charsets.UTF_8))?.groupValues?.get(1)?.toInt()
        ?: throw VerificationException("layoutlib-runtime $classifier build.prop has no ro.build.version.sdk")
    }
    val sdk = byClassifier.values.toSet().singleOrNull()
      ?: throw VerificationException("bundled SDK differs across classifiers: $byClassifier")
    log("sdk", "API $sdk (all classifiers)")
    return sdk
  }

  /** Returns the minimum layoutlib-api, or `null` when [pinnedApiVersion] already suffices. */
  private fun minimumApi(version: String, layoutlibJar: File): String? {
    val refs = references(layoutlibJar, ownerFilter = ::isApiClass, skipEntries = ::isApiClass)
    val candidates = maven.versions("layoutlib-api")
      .filter { VersionOrder.isStable(it) && VersionOrder.compare(it, apiFloor) >= 0 }
      .filter { VersionOrder.compare(it, pinnedApiVersion) >= 0 }
    var pinnedGaps = emptyList<Ref>()
    var lastGaps = emptyList<Ref>()
    for (api in candidates) {
      lastGaps = ClassModel.of(maven.download(maven.url("layoutlib-api", api))).missing(refs)
      if (api == pinnedApiVersion) pinnedGaps = lastGaps
      if (lastGaps.isEmpty()) {
        if (api == pinnedApiVersion) {
          log("api", "pinned layoutlib-api $pinnedApiVersion satisfies all ${refs.size} references")
          return null
        }
        val lacks = pinnedGaps.take(5).joinToString()
        log("api", "requires layoutlib-api >= $api (pinned $pinnedApiVersion lacks: $lacks)")
        return api
      }
    }
    throw VerificationException(
      "no layoutlib-api release satisfies layoutlib $version; newest still lacks: ${lastGaps.take(10).joinToString()}"
    )
  }

  private fun checkLinkage(version: String, layoutlibJar: File, api: String) {
    val apiModel = ClassModel.of(maven.download(maven.url("layoutlib-api", api)))
    // layoutlib classes extend layoutlib-api ones (e.g. layoutlib Bridge -> api Bridge).
    val target = apiModel + ClassModel.of(layoutlibJar)
    val baseline = ClassModel.of(maven.download(maven.url("layoutlib", defaultVersion)))
    // Only references that resolve against the default layoutlib: that's what Paparazzi links against.
    val refs = references(paparazziJar, ownerFilter = { it in baseline && !isApiClass(it) })
    val gaps = target.missing(refs)
    if (gaps.isNotEmpty()) {
      throw VerificationException(
        "Paparazzi links against layoutlib members missing in $version (route them through LayoutlibCompat):\n    " +
          gaps.joinToString("\n    ")
      )
    }
    val apiGaps = apiModel.missing(references(paparazziJar, ownerFilter = ::isApiClass))
    if (apiGaps.isNotEmpty()) {
      throw VerificationException("Paparazzi references missing from layoutlib-api $api: ${apiGaps.joinToString()}")
    }
    log("linkage", "${refs.size} direct layoutlib references and layoutlib-api $api resolve")
  }

  private fun checkResources(version: String) {
    val candidate = resourceShape(maven.download(maven.url("layoutlib-resources", version)))
    val baseline = resourceShape(maven.download(maven.url("layoutlib-resources", defaultVersion)))
    if (!candidate.hasAttrs) {
      throw VerificationException("layoutlib-resources has no res/values/attrs.xml (required by Bridge.init)")
    }
    val addedTypes = candidate.types - baseline.types
    val addedTags = candidate.tags - baseline.tags
    if (addedTypes.isNotEmpty() || addedTags.isNotEmpty()) {
      throw VerificationException(
        "layoutlib-resources $version adds resource types ${addedTypes.sorted()} / value tags ${addedTags.sorted()} " +
          "vs $defaultVersion; verify FrameworkResourceRepository handles them (or add a resource hook)"
      )
    }
    val notes = listOfNotNull(
      (candidate.qualifiers - baseline.qualifiers).takeIf { it.isNotEmpty() }?.let { "+qualifiers ${it.sorted()}" },
      (baseline.qualifiers - candidate.qualifiers).takeIf { it.isNotEmpty() }?.let { "-qualifiers ${it.sorted()}" },
      (baseline.types - candidate.types).takeIf { it.isNotEmpty() }?.let { "-types ${it.sorted()}" }
    )
    log(
      "resources",
      "same shape as default $defaultVersion" +
        notes.takeIf { it.isNotEmpty() }?.joinToString("; ", " (", ")").orEmpty()
    )
  }

  private class ResourceShape(
    val types: Set<String>,
    val qualifiers: Set<String>,
    val tags: Set<String>,
    val hasAttrs: Boolean
  )

  private fun resourceShape(jar: File): ResourceShape {
    val types = mutableSetOf<String>()
    val qualifiers = mutableSetOf<String>()
    val tags = mutableSetOf<String>()
    var hasAttrs = false
    ZipFile(jar).use { zip ->
      zip.entries().asSequence().filter { !it.isDirectory && it.name.startsWith("res/") }.forEach { entry ->
        val parts = entry.name.split('/')[1].split('-')
        types += parts[0]
        qualifiers += parts.drop(1)
        if (entry.name == "res/values/attrs.xml") hasAttrs = true
        if (parts[0] == "values") {
          val text = zip.getInputStream(entry).use { it.readBytes() }.toString(Charsets.UTF_8).replace(XML_COMMENT, "")
          TAG.findAll(text).forEach { tags += it.groupValues[1] }
        }
      }
    }
    return ResourceShape(types, qualifiers, tags, hasAttrs)
  }

  private fun isApiClass(name: String) = API_PACKAGES.any(name::startsWith)

  companion object {
    val CLASSIFIERS = listOf("linux", "win", "mac", "mac-arm")
    private val API_PACKAGES = listOf("com/android/ide/common/rendering/api/", "com/android/resources/")
    private val SDK_PROPERTY = Regex("""(?m)^ro\.build\.version\.sdk=(\d+)$""")
    private val ICU_ENTRY = Regex("""data/icu/(icudt\d+l\.dat)""")
    private val XML_COMMENT = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
    private val TAG = Regex("""<([A-Za-z][\w-]*)[\s>/]""")
  }
}
