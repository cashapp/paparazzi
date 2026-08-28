/*
 * Copyright (C) 2024 Square, Inc.
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

package app.cash.paparazzi.internal.sandbox

import java.net.URL

/**
 * Decides, for every class name a [SandboxClassLoader] is asked to load, whether the sandbox
 * defines its own copy ("acquires" it) or delegates to the parent loader.
 *
 * This is the direct analogue of Robolectric's `InstrumentationConfiguration`. The rules matter a
 * great deal:
 *
 *  * **Acquired** classes get a fresh copy per sandbox, and therefore fresh static state. This is
 *    the entire point of the exercise — `Bridge.sJniLibLoaded`, `Choreographer.mLastFrameTimeNanos`,
 *    `Build.VERSION.SDK_INT` and friends stop leaking between tests.
 *  * **Delegated** classes are shared with the parent loader, so instances can cross the sandbox
 *    boundary without `ClassCastException`. Everything used to *talk to* the sandbox must be
 *    delegated.
 *
 * The default policy delegates `com.android.ide.common.rendering.api.*` (layoutlib-api), which is a
 * pure-Java SPI shipped in a separate artifact from layoutlib itself and references no `android.*`
 * types. That makes it the natural boundary interface: the host constructs `ILayoutLog`,
 * `LayoutlibCallback` and `SessionParams` instances and hands them to a sandboxed `Bridge`.
 *
 * Instances are value types so they can key [SandboxManager]'s cache.
 */
internal class SandboxConfiguration private constructor(
  val classpath: List<URL>,
  val acquiredPackages: Set<String>,
  val delegatedPackages: Set<String>,
  val acquiredClasses: Set<String>,
  val delegatedClasses: Set<String>
) {
  /**
   * True if the sandbox should define its own copy of [className].
   *
   * Exact class names win over package prefixes, and delegation wins over acquisition, so a caller
   * can punch a hole in a broad acquired package without restating the whole policy.
   */
  fun shouldAcquire(className: String): Boolean {
    if (className in delegatedClasses) return false
    if (className in acquiredClasses) return true
    if (delegatedPackages.any { className.startsWith(it) }) return false
    return acquiredPackages.any { className.startsWith(it) }
  }

  override fun equals(other: Any?): Boolean =
    this === other || (
      other is SandboxConfiguration &&
        classpath == other.classpath &&
        acquiredPackages == other.acquiredPackages &&
        delegatedPackages == other.delegatedPackages &&
        acquiredClasses == other.acquiredClasses &&
        delegatedClasses == other.delegatedClasses
      )

  override fun hashCode(): Int {
    var result = classpath.hashCode()
    result = 31 * result + acquiredPackages.hashCode()
    result = 31 * result + delegatedPackages.hashCode()
    result = 31 * result + acquiredClasses.hashCode()
    result = 31 * result + delegatedClasses.hashCode()
    return result
  }

  override fun toString(): String =
    "SandboxConfiguration(classpath=${classpath.size} entries, " +
      "acquired=${acquiredPackages.size} packages, delegated=${delegatedPackages.size} packages)"

  class Builder {
    private var classpath: List<URL> = emptyList()
    private val acquiredPackages = linkedSetOf<String>()
    private val delegatedPackages = linkedSetOf<String>()
    private val acquiredClasses = linkedSetOf<String>()
    private val delegatedClasses = linkedSetOf<String>()

    fun classpath(urls: List<URL>) = apply { classpath = urls.toList() }

    fun acquirePackages(vararg prefixes: String) = apply { acquiredPackages += prefixes }

    fun delegatePackages(vararg prefixes: String) = apply { delegatedPackages += prefixes }

    fun acquireClasses(vararg names: String) = apply { acquiredClasses += names }

    fun delegateClasses(vararg names: String) = apply { delegatedClasses += names }

    /**
     * Applies the policy that isolates layoutlib and the Android framework while keeping the JDK,
     * Kotlin, JUnit and the layoutlib-api SPI shared with the host.
     */
    fun withDefaults() =
      apply {
        // Must be shared: the JVM will not tolerate a second java.lang, and everything below is
        // either part of the host/sandbox conversation or stateless enough not to matter.
        delegatePackages(
          "java.",
          "javax.",
          "jdk.",
          "sun.",
          "com.sun.",
          "org.w3c.dom.",
          "org.xml.sax.",
          "kotlin.",
          "kotlinx.coroutines.",
          "org.jetbrains.annotations.",
          "org.junit.",
          "junit.",
          "org.hamcrest.",
          // layoutlib-api and the AOSP tooling libraries. Pure Java, no android.* references, and
          // they form the interface the host uses to drive a sandboxed Bridge.
          "com.android.ide.common.",
          "com.android.resources.",
          "com.android.utils.",
          "com.android.io.",
          "com.android.sdklib.",
          "com.android.support.",
          "com.android.ninepatch.",
          "com.android.SdkConstants",
          "com.android.annotations."
        )

        // Everything layoutlib.jar ships, plus AndroidX. AndroidX in particular *must* be isolated:
        // Compose resolves android.view.View at link time, so a shared androidx would bind to the
        // host's copy of the framework and fail with NoClassDefFoundError / ClassCastException.
        acquirePackages(
          "android.",
          "androidx.",
          "com.android.layoutlib.",
          "com.android.internal.",
          "com.android.tools.layoutlib.",
          "com.android.tools.idea.",
          "com.android.i18n.",
          "com.android.icu.",
          "com.android.org.",
          "com.android.libcore.",
          "com.android.server.",
          "com.android.modules.",
          "com.android.systemui.",
          "com.android.launcher3.",
          "com.android.net.",
          "com.android.os.",
          "com.android.apex.",
          "com.android.ims.",
          "com.android.telephony.",
          "com.google.android.",
          "com.google.ux.",
          "dalvik.",
          "libcore.",
          "org.apache.harmony.",
          "org.ccil.cowan.tagsoup.",
          "org.json.",
          "javax.microedition."
        )
      }

    fun build(): SandboxConfiguration {
      require(classpath.isNotEmpty()) { "Sandbox classpath must not be empty." }
      return SandboxConfiguration(
        classpath = classpath,
        acquiredPackages = acquiredPackages.toSet(),
        delegatedPackages = delegatedPackages.toSet(),
        acquiredClasses = acquiredClasses.toSet(),
        delegatedClasses = delegatedClasses.toSet()
      )
    }
  }

  companion object {
    /** The default policy over the current JVM's classpath. */
    fun default(classpath: List<URL> = Classpath.current()): SandboxConfiguration =
      Builder()
        .classpath(classpath)
        .withDefaults()
        .build()
  }
}
