package app.cash.paparazzi.internal.interceptors

// Sampled from https://cs.android.com/android/platform/superproject/+/master:external/robolectric-shadows/shadows/framework/src/main/java/org/robolectric/shadows/ShadowOpenGLMatrix.java;l=69-121
object MatrixVectorMultiplicationInterceptor {
  @Suppress("unused")
  @JvmStatic
  fun intercept(
    resultVec: FloatArray,
    resultVecOffset: Int,
    lhsMat: FloatArray,
    lhsMatOffset: Int,
    rhsVec: FloatArray,
    rhsVecOffset: Int
  ) {
    require(resultVecOffset + 4 <= resultVec.size) { "resultOffset + 4 > result.length" }
    require(lhsMatOffset + 16 <= lhsMat.size) { "lhsOffset + 16 > lhs.length" }
    require(rhsVecOffset + 4 <= rhsVec.size) { "rhsOffset + 4 > rhs.length" }
    val x = rhsVec[rhsVecOffset + 0]
    val y = rhsVec[rhsVecOffset + 1]
    val z = rhsVec[rhsVecOffset + 2]
    val w = rhsVec[rhsVecOffset + 3]
    val o = lhsMatOffset
    resultVec[resultVecOffset + 0] =
      lhsMat[index(0, 0, o)] * x + lhsMat[index(1, 0, o)] * y + lhsMat[index(2, 0, o)] * z + lhsMat[index(3, 0, o)] * w
    resultVec[resultVecOffset + 1] =
      lhsMat[index(0, 1, o)] * x + lhsMat[index(1, 1, o)] * y + lhsMat[index(2, 1, o)] * z + lhsMat[index(3, 1, o)] * w
    resultVec[resultVecOffset + 2] =
      lhsMat[index(0, 2, o)] * x + lhsMat[index(1, 2, o)] * y + lhsMat[index(2, 2, o)] * z + lhsMat[index(3, 2, o)] * w
    resultVec[resultVecOffset + 3] =
      lhsMat[index(0, 3, o)] * x + lhsMat[index(1, 3, o)] * y + lhsMat[index(2, 3, o)] * z + lhsMat[index(3, 3, o)] * w
  }

  private fun index(i: Int, j: Int, offset: Int): Int =
    // #define I(_i, _j) ((_j)+ 4*(_i))
    offset + j + 4 * i
}
