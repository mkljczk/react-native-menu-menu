package com.reactnativemenu

import android.content.Context
import android.content.res.Configuration
import android.util.TypedValue

object ThemeHelper {
  fun isNight(context: Context): Boolean {
    val mask = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    return mask == Configuration.UI_MODE_NIGHT_YES
  }

  private fun styleIdOrZero(className: String, fieldName: String): Int {
    return try {
      val cls = Class.forName(className) // exp: "com.google.android.material.R$style"
      val f = cls.getField(fieldName)    // exp: "ThemeOverlay_Material3_Dark"
      f.getInt(null)
    } catch (_: Throwable) {
      0
    }
  }

  fun isMaterial3Expressive(context: Context): Boolean {
    val attr = styleIdOrZero("com.google.android.material.R\$attr", "materialButtonStyle")
    val style = styleIdOrZero("com.google.android.material.R\$style", "Widget_Material3Expressive_Button")
    if (attr == 0 || style == 0) return false

    val value = TypedValue()
    return context.theme.resolveAttribute(attr, value, true) && value.resourceId == style
  }

  // Prefers DynamicColors variant, falls back to static one
  private fun overlay(prefix: String, dark: Boolean): Int {
    val variant = if (dark) "Dark" else "Light"
    val cls = "com.google.android.material.R\$style"
    return styleIdOrZero(cls, "${prefix}_DynamicColors_$variant").takeIf { it != 0 }
      ?: styleIdOrZero(cls, "${prefix}_$variant")
  }

  fun material3Expressive(dark: Boolean): Int {
    return overlay("ThemeOverlay_Material3Expressive", dark)
  }

  fun material3(dark: Boolean): Int {
    // Reflection ile: Material3 varsa al, yoksa 0 döner
    return overlay("ThemeOverlay_Material3", dark)
  }

  fun appcompat(dark: Boolean): Int {
    // AppCompat reflection (RN projelerinde zaten var)
    val name = if (dark) "ThemeOverlay_AppCompat_Dark" else "ThemeOverlay_AppCompat_Light"
    return styleIdOrZero("androidx.appcompat.R\$style", name)
  }
}
