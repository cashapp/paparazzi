import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  id("com.android.library")
  id("kotlin-android")
  id("app.cash.paparazzi")
}

android {
  namespace = "app.cash.paparazzi.plugin.test"
  compileSdk = libs.versions.compileSdk.get().toInt()

  defaultConfig {
    minSdk = libs.versions.minSdk.get().toInt()
  }

  compileOptions {
    sourceCompatibility =
      JavaVersion.toVersion(libs.versions.javaTarget.get())
    targetCompatibility =
      JavaVersion.toVersion(libs.versions.javaTarget.get())
  }

  kotlin {
    compilerOptions {
      jvmTarget.set(JvmTarget.fromTarget(libs.versions.javaTarget.get()))
    }
  }
}

dependencies {
  implementation(libs.androidx.appcompat)

  testImplementation("org.junit.jupiter:junit-jupiter-api:6.0.3")
  testImplementation("org.junit.jupiter:junit-jupiter-params:6.0.3")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.0.3")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")
}

tasks.withType<Test> {
  useJUnitPlatform()
}
