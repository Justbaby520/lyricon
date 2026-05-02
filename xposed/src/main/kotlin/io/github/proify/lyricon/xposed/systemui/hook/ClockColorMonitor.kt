/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui.hook

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.view.View
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import io.github.libxposed.api.XposedModule
import io.github.proify.lyricon.xposed.systemui.util.OnColorChangeListener
import java.util.WeakHashMap

object ClockColorMonitor {

    private const val INVALID_ID = -1
    private const val CLOCK_ID_NAME = "clock"
    private const val CLOCK_ID_TYPE = "id"

    private val listeners = WeakHashMap<View, OnColorChangeListener>()
    private val luminanceCache = HashMap<Int, Float>()

    @Volatile
    private var hooked = false

    @Volatile
    private var clockId: Int = INVALID_ID

    fun setListener(view: View, listener: OnColorChangeListener?) {
        if (listener == null) {
            listeners.remove(view)
        } else {
            listeners[view] = listener
        }
    }

    fun hook(module: XposedModule, classLoader: ClassLoader) {
        if (hooked) return
        hooked = true

        val classTextView = classLoader.loadClass("android.widget.TextView")
        val methods = arrayOf(
            classTextView.getDeclaredMethod(
                "setTextColor",
                ColorStateList::class.java
            ),
            classTextView.getDeclaredMethod(
                "setTextColor",
                Int::class.javaPrimitiveType
            )
        )

        methods.forEach { method ->
            module.hook(method).intercept { chain ->
                chain.proceed()
                // 后置处理
                val tv = chain.thisObject as? TextView
                if (tv != null) {
                    afterSetColor(tv)
                }
                null
            }
        }
    }

    @SuppressLint("DiscouragedApi")
    private fun afterSetColor(tv: TextView) {
        if (clockId == INVALID_ID) {
            clockId = tv.resources.getIdentifier(
                CLOCK_ID_NAME,
                CLOCK_ID_TYPE,
                tv.context.packageName
            )
            if (clockId == INVALID_ID) return
        }

        if (tv.id != clockId) return

        val listener = listeners[tv] ?: return

        val color = tv.currentTextColor
        val luminance = luminanceCache.getOrPut(color) {
            ColorUtils.calculateLuminance(color).toFloat()
        }

        listener.onColorChanged(color, luminance)
    }
}
