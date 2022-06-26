package app.cash.paparazzi.internal

import sun.misc.Unsafe
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.security.AccessController
import java.security.PrivilegedAction

/**
 * Giant pile of hacks to allow overwriting a [Field] using reflection. Implementation taken from
 * [Powermock](https://github.com/powermock/powermock)'s `WhiteboxImpl` class.
 */
object ReflectionUtils {

  fun getField(fieldName: String, where: Class<*>?): Field? {
    requireNotNull(where) { "where cannot be null" }
    val field: Field?
    try {
      field = where.getDeclaredField(fieldName)
      field.isAccessible = true
    } catch (e: NoSuchFieldException) {
      throw RuntimeException("Field '$fieldName' was not found in class ${where.name}.")
    }
    return field
  }

  fun setField(obj: Any, value: Any, field: Field) {
    val isStatic = (field.modifiers and Modifier.STATIC) == Modifier.STATIC
    if (isStatic) {
      setStaticFieldUsingUnsafe(field, value)
    } else {
      setFieldUsingUnsafe(field, obj, value)
    }
  }

  private fun setStaticFieldUsingUnsafe(field: Field, newValue: Any) {
    try {
      field.isAccessible = true
      val fieldModifiersMask = field.modifiers
      val isFinalModifierPresent = fieldModifiersMask and Modifier.FINAL == Modifier.FINAL
      if (isFinalModifierPresent) {
        AccessController.doPrivileged(
          PrivilegedAction<Any?> {
            try {
              val unsafe = getUnsafe()
              val offset = unsafe.staticFieldOffset(field)
              unsafe.putObject(unsafe.staticFieldBase(field), offset, newValue)
              null
            } catch (t: Throwable) {
              throw RuntimeException(t)
            }
          }
        )
      } else {
        field.set(null, newValue)
      }
    } catch (ex: SecurityException) {
      throw RuntimeException(ex)
    } catch (ex: IllegalAccessException) {
      throw RuntimeException(ex)
    } catch (ex: IllegalArgumentException) {
      throw RuntimeException(ex)
    }
  }

  private fun setFieldUsingUnsafe(field: Field, obj: Any, newValue: Any) {
    try {
      field.isAccessible = true
      val fieldModifiersMask = field.modifiers
      val isFinalModifierPresent = fieldModifiersMask and Modifier.FINAL == Modifier.FINAL
      if (isFinalModifierPresent) {
        AccessController.doPrivileged(
          PrivilegedAction<Any?> {
            try {
              val unsafe = getUnsafe()
              val offset = unsafe.objectFieldOffset(field)
              setFieldUsingUnsafe(obj, field.type, offset, newValue, unsafe)
              null
            } catch (t: Throwable) {
              throw RuntimeException(t)
            }
          }
        )
      } else {
        try {
          field.set(obj, newValue)
        } catch (ex: IllegalAccessException) {
          throw RuntimeException(ex)
        }
      }
    } catch (ex: SecurityException) {
      throw RuntimeException(ex)
    }
  }

  private fun setFieldUsingUnsafe(
    base: Any,
    type: Class<*>,
    offset: Long,
    newValue: Any,
    unsafe: Unsafe
  ) {
    when (type) {
      Int::class.java -> {
        unsafe.putInt(base, offset, (newValue as Int))
      }
      Short::class.java -> {
        unsafe.putShort(base, offset, (newValue as Short))
      }
      Long::class.java -> {
        unsafe.putLong(base, offset, (newValue as Long))
      }
      Byte::class.java -> {
        unsafe.putByte(base, offset, (newValue as Byte))
      }
      Boolean::class.java -> {
        unsafe.putBoolean(base, offset, (newValue as Boolean))
      }
      Float::class.java -> {
        unsafe.putFloat(base, offset, (newValue as Float))
      }
      Double::class.java -> {
        unsafe.putDouble(base, offset, (newValue as Double))
      }
      Char::class.java -> {
        unsafe.putChar(base, offset, (newValue as Char))
      }
      else -> {
        unsafe.putObject(base, offset, newValue)
      }
    }
  }

  private fun getUnsafe(): Unsafe {
    val field = Unsafe::class.java.getDeclaredField("theUnsafe")
    field.isAccessible = true
    return field.get(null) as Unsafe
  }
}
