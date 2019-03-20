package com.squareup.cash.screenshot.jvm

data class Environment(
  val platformDir: String,
  val appTestDir: String
) {
  val testResDir: String = "$appTestDir/app/build/intermediates/classes/production/release/"
  val appClassesLocation = "$appTestDir/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes"
  val assetsDir = "$appTestDir/src/main/assets/"
  val resDir = "$appTestDir/src/main/res"
}

fun detectEnvironment(): Environment {
  // TODO: detect platformDir by finding the highest SDK in ANDROID_HOME.
  val userDir = System.getProperty("user.dir")
  val platformDir = "/Users/jwilson/Library/Android/sdk/platforms/android-28/"
  return Environment(platformDir, userDir)
}
