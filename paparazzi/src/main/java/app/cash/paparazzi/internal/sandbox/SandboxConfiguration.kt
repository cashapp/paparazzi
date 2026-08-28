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
 * This is the analogue of Robolectric's `InstrumentationConfiguration`, with one important
 * difference: **the default is to acquire.** Only the explicitly delegated prefixes are shared with
 * the host.
 *
 * ### Why acquire by default
 *
 * The obvious policy — list the packages to isolate — is wrong, and fails in a way that is very
 * hard to diagnose. Anything that links against `android.*` must live on the same side of the
 * boundary as the framework, and the set of such libraries is neither small nor guessable:
 * layoutlib.jar alone ships over a thousand packages including `com.android.adservices`, and
 * `kotlinx.coroutines.android` links `android.os.Handler` from a completely different artifact.
 *
 * Getting that list wrong produces failures far from their cause. A missed package loads in the
 * host and then calls a framework method whose JNI natives were registered against the sandbox's
 * copy, yielding `UnsatisfiedLinkError` from unrelated code; or the JVM catches it later as
 * `LinkageError: loader constraint violation`.
 *
 * Inverting the default turns that whole class of bug into its opposite: forgetting to *delegate*
 * something fails immediately and legibly, with a `ClassCastException` at the exact boundary
 * crossing. So the delegate list below is short, deliberate, and each entry has a reason.
 *
 * Rules are matched longest-prefix-first, so a specific rule refines a general one — `javax.` is
 * delegated while `javax.microedition.`, which layoutlib ships, is not.
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
   * Exact class names win over package prefixes. Among prefixes the longest match wins, so a
   * narrower rule always refines a broader one regardless of declaration order. Anything unmatched
   * is acquired.
   */
  fun shouldAcquire(className: String): Boolean {
    if (className in delegatedClasses) return false
    if (className in acquiredClasses) return true

    val longestDelegated = delegatedPackages.filter { className.startsWith(it) }.maxOfOrNull { it.length } ?: -1
    val longestAcquired = acquiredPackages.filter { className.startsWith(it) }.maxOfOrNull { it.length } ?: -1

    return longestAcquired >= longestDelegated
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
      "delegated=${delegatedPackages.size} prefixes, acquire-by-default)"

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
     * Shares with the host only what has to be shared. Everything else — the Android framework,
     * layoutlib, AndroidX, and any library that links against them — is acquired.
     */
    fun withDefaults() =
      apply {
        delegatePackages(
          // The JVM will not tolerate a second java.lang, and the JDK cannot reference android.*
          // anyway.
          "java.",
          "javax.",
          "jdk.",
          "sun.",
          "com.sun.",
          "org.w3c.dom.",
          "org.xml.sax.",
          // The Kotlin standard library is framework-independent and appears in signatures that
          // cross the boundary.
          "kotlin.",
          "org.jetbrains.annotations.",
          // JUnit must be shared or the runner could not build Statements for the sandbox, and
          // failures could not propagate back to the host's reporting.
          "org.junit.",
          "junit.",
          "org.hamcrest.",
          // Gradle's test worker infrastructure calls into the runner from the host side.
          "org.gradle.",
          "worker.org.gradle.",
          // ByteBuddy drives redefinition through the JVM-wide instrumentation agent; a second copy
          // would fight the first over agent installation.
          "net.bytebuddy.",
          // layoutlib-api and the AOSP tooling libraries: pure Java, no android.* references, and
          // the interface through which the host configures a sandboxed Bridge.
          "com.android.ide.common.",
          "com.android.resources.",
          "com.android.utils.",
          "com.android.io.",
          "com.android.sdklib.",
          "com.android.ninepatch.",
          "com.android.annotations.",
          "com.android.SdkConstants",
          // Appear in the signatures of the layoutlib-api types above, so they cross the boundary
          // too: ResourceRepository exposes Guava collections and ILayoutPullParser extends
          // XmlPullParser.
          "com.google.common.",
          "org.xmlpull."
        )

        // layoutlib ships these under otherwise-delegated prefixes. Longest-match makes them win.
        acquirePackages("javax.microedition.")
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
    /**
     * The policy for a host that drives a sandboxed `Bridge` reflectively.
     *
     * Paparazzi itself stays on the host side, talking to the sandbox through layoutlib-api.
     */
    fun default(classpath: List<URL> = Classpath.current()): SandboxConfiguration =
      Builder()
        .classpath(classpath)
        .withDefaults()
        .delegatePackages("app.cash.paparazzi.")
        .build()

    /**
     * The policy used by `PaparazziRunner`, where the relationship inverts.
     *
     * The test class is reloaded *inside* the sandbox, so its `@Rule Paparazzi` field, the
     * `PaparazziSdk` it creates, and that SDK's companion statics must all be the sandbox's own.
     * Only the sandbox machinery itself stays on the host side — it is what builds sandboxes and
     * has no business being duplicated inside one.
     */
    fun forRunner(classpath: List<URL> = Classpath.current()): SandboxConfiguration =
      Builder()
        .classpath(classpath)
        .withDefaults()
        .delegatePackages("app.cash.paparazzi.internal.sandbox.")
        // Exact, not a prefix: a prefix would also delegate any class merely named like the
        // runner, silently un-sandboxing it.
        .delegateClasses("app.cash.paparazzi.PaparazziRunner")
        .build()
  }
}
