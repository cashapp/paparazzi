package app.cash.paparazzi.internal.interceptors

// Sampled from https://cs.android.com/android/platform/superproject/+/master:external/robolectric-shadows/shadows/framework/src/main/java/org/robolectric/shadows/ShadowOpenGLMatrix.java;l=10-67
object MatrixMatrixMultiplicationInterceptor {
  @Suppress("unused", "LocalVariableName", "ktlint:standard:property-naming")
  @JvmStatic
  fun intercept(
    result: FloatArray,
    resultOffset: Int,
    lhs: FloatArray,
    lhsOffset: Int,
    rhs: FloatArray,
    rhsOffset: Int
  ) {
    require(resultOffset + 16 <= result.size) { "resultOffset + 16 > result.length" }
    require(lhsOffset + 16 <= lhs.size) { "lhsOffset + 16 > lhs.length" }
    require(rhsOffset + 16 <= rhs.size) { "rhsOffset + 16 > rhs.length" }
    for (i in 0..3) {
      val rhs_i0 = rhs[index(i, 0, rhsOffset)]
      var r_i0 = lhs[index(0, 0, lhsOffset)] * rhs_i0
      var r_i1 = lhs[index(0, 1, lhsOffset)] * rhs_i0
      var r_i2 = lhs[index(0, 2, lhsOffset)] * rhs_i0
      var r_i3 = lhs[index(0, 3, lhsOffset)] * rhs_i0
      for (j in 1..3) {
        val rhs_ij = rhs[index(i, j, rhsOffset)]
        r_i0 += lhs[index(j, 0, lhsOffset)] * rhs_ij
        r_i1 += lhs[index(j, 1, lhsOffset)] * rhs_ij
        r_i2 += lhs[index(j, 2, lhsOffset)] * rhs_ij
        r_i3 += lhs[index(j, 3, lhsOffset)] * rhs_ij
      }
      result[index(i, 0, resultOffset)] = r_i0
      result[index(i, 1, resultOffset)] = r_i1
      result[index(i, 2, resultOffset)] = r_i2
      result[index(i, 3, resultOffset)] = r_i3
    }
  }

  private fun index(i: Int, j: Int, offset: Int): Int =
    // #define I(_i, _j) ((_j)+ 4*(_i))
    offset + j + 4 * i
}
