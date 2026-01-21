/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lyricon.xposed

import android.content.Context
import com.highcapable.yukihookapi.hook.log.YLog
import de.robv.android.xposed.XSharedPreferences
import io.github.proify.lyricon.common.PackageNames
import java.io.File

object Dirs {
    private lateinit var moduleDataDir: File
    private lateinit var tempDir: File
    private lateinit var packageDir: File

    val preferenceDirectory: File? by lazy {
        XSharedPreferences(
            PackageNames.APPLICATION,
            "114514"
        ).file.parentFile
    }

    fun initialize(appInfo: Context) {
        val dataDir = appInfo.filesDir
        moduleDataDir = File(dataDir, "lyricon")
        tempDir = File(moduleDataDir, ".temp")
        packageDir = File(moduleDataDir, "packages")
        YLog.debug("Lyricon map directory: $moduleDataDir")
    }

    fun getDataFile(name: String): File = File(moduleDataDir, name)
    fun getTempFile(name: String): File = File(tempDir, name)
    fun getPackageDataDir(packageName: String): File = File(packageDir, packageName)
    fun getPackageDataFile(packageName: String, name: String): File =
        File(getPackageDataDir(packageName), name)
}