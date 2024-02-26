package androidx.databinding

import android.annotation.SuppressLint
import app.cash.paparazzi.plugin.test.DataBinderMapperImpl

@SuppressLint("RestrictedApi")
public class DataBinderMapperImpl : MergedDataBinderMapper() {
  init { addMapper(DataBinderMapperImpl()) }
}