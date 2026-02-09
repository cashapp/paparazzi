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
    sourceCompatibility = JavaVersion.toVersion(libs.versions.javaTarget.get())
    targetCompatibility = JavaVersion.toVersion(libs.versions.javaTarget.get())
  }

  kotlin {
    compilerOptions {
      jvmTarget.set(JvmTarget.fromTarget(libs.versions.javaTarget.get()))
    }
  }
}

dependencies {
  implementation(libs.androidx.appcompat)

  testImplementation("io.kotest:kotest-framework-engine:5.6.2")
  testImplementation("io.kotest:kotest-runner-junit5:5.6.2")
  testImplementation("io.kotest:kotest-assertions-core:5.6.2")
}

tasks.withType<Test> {
  useJUnitPlatform()
}
