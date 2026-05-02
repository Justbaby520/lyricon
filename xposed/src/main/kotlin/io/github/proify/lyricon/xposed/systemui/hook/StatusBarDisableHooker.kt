/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lyricon.xposed.systemui.hook

import io.github.libxposed.api.XposedModule
import io.github.proify.lyricon.xposed.logger.YLog
import java.util.concurrent.CopyOnWriteArraySet

/**
 * 监听状态栏禁用事件
 */
object StatusBarDisableHooker {

    private const val TAG = "StatusBarDisableHooker"

    // 状态标志位定义
    private const val FLAG_DISABLE_SYSTEM_INFO = 0x00800000

    private val listeners = CopyOnWriteArraySet<OnStatusBarDisableListener>()

    /**
     * 外部注册监听器
     */
    fun addListener(listener: OnStatusBarDisableListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    /**
     * 外部移除监听器
     */
    fun removeListener(listener: OnStatusBarDisableListener) {
        listeners.remove(listener)
    }

    fun inject(module: XposedModule, classLoader: ClassLoader) {
        try {
            val clazz = Class.forName(
                "com.android.systemui.statusbar.phone.fragment.CollapsedStatusBarFragment",
                true,
                classLoader
            )
            val method = clazz.getDeclaredMethod(
                "disable",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType
            )

            module.hook(method).intercept { chain ->
                chain.proceed()
                val state1 = chain.args[1] as Int
                val animate = chain.args[3] as Boolean

                val shouldHide = (state1 and FLAG_DISABLE_SYSTEM_INFO != 0)

                listeners.forEach {
                    try {
                        it.onDisableStateChanged(shouldHide, animate)
                    } catch (e: Exception) {
                        YLog.error(TAG, "分发监听失败", e)
                    }
                }
                null
            }
        } catch (e: Throwable) {
            YLog.error(TAG, " -> Hook 注入失败: ")
        }
    }

    interface OnStatusBarDisableListener {
        fun onDisableStateChanged(shouldHide: Boolean, animate: Boolean)
    }
}
