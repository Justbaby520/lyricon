/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.proify.lyricon.common.PackageNames
import io.github.proify.lyricon.xposed.hook.GeneralHooker
import io.github.proify.lyricon.xposed.logger.YLog
import io.github.proify.lyricon.xposed.systemui.SystemUIHooker

class ModuleEntry : XposedModule() {

    companion object {
        private const val TAG = "ModuleEntry"

        private val scopes = listOf(
            PackageNames.APPLICATION,
            PackageNames.SYSTEM_UI,
        )

        lateinit var instance: ModuleEntry
    }

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        instance = this
        YLog.init(this)
        YLog.info(
            TAG,
            "onModuleLoaded: isSystemServer=${param.isSystemServer}, processName=${param.processName}"
        )
    }

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        val packageName = param.packageName
        if (packageName !in scopes) return

        YLog.info(TAG, "onPackageLoaded: $packageName")

        GeneralHooker.hook(this, param)
        when (packageName) {
            PackageNames.SYSTEM_UI -> SystemUIHooker.hook(this, param)
        }
    }
}