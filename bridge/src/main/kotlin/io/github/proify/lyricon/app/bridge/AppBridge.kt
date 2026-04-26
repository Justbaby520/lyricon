/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lyricon.app.bridge

import android.content.Context
import android.os.Environment
import androidx.annotation.Keep
import io.github.proify.lyricon.common.Constants
import io.github.proify.lyricon.common.JsonSharedPreferences
import java.io.File

/**
 * 桥接模块
 * 用于给xposed环境hook
 */
object AppBridge {

    @Keep
    fun isModuleActive(): Boolean = false

    fun getSharedPreferences(context: Context, fileName: String): JsonSharedPreferences {
        return JsonSharedPreferences(getPreferenceFile(context, fileName))
    }

    fun getPreferenceFile(context: Context, fileName: String): File {
        return File(getPreferenceDirectory(context), fileName)
    }

    fun getPreferenceDirectory(context: Context): File {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            .resolve("lyricon/shared_prefs")
    }

    object LyricStylePrefs {
        const val DEFAULT_PACKAGE_NAME: String = Constants.APP_PACKAGE_NAME
        const val PREF_NAME_BASE_STYLE: String = "baseLyricStyle"
        const val PREF_PACKAGE_STYLE_MANAGER: String = "packageStyleManager"
        const val KEY_ENABLED_PACKAGES: String = "enables"

        fun getPackageStylePreferenceName(packageName: String): String =
            "package_style_${packageName.replace(".", "_")}"
    }
}