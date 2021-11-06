// Note this test needs to use Kotlin DSL, for some reason the ordering matters here more.
plugins {
  id("app.cash.paparazzi")
  id("com.android.library")
  id("kotlin-android")
}

repositories {
  maven {
    setUrl("file://${projectDir.absolutePath}/../../../../../build/localMaven")
  }
  mavenCentral()
  google()
}

android {
  compileSdkVersion(29)
  defaultConfig {
    minSdkVersion(25)
  }
}
