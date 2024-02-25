package androidx.databinding

import android.annotation.SuppressLint

/**
 * Running instrumented tests with Robolectric on Data Binding enabled library modules throws [NoClassDefFoundError] for
 * [DataBinderMapperImpl]. For some reason Data Binding compiler is not generating the [DataBinderMapperImpl] class
 * while running local JVM tests. There is already a [bug][https://issuetracker.google.com/issues/126775542] reported
 * for this behaviour.
 *
 * This class provides temporary solution by registering library module's generated `com.mylibrary.DataBinderMapperImpl`
 * to `androidx.databinding.DataBinderMapperImpl` via reflection.
 */
@SuppressLint("RestrictedApi")
class DataBinderMapperImpl : MergedDataBinderMapper() {
  init { addMapper("app.cash.paparazzi.plugin.test") }
}
