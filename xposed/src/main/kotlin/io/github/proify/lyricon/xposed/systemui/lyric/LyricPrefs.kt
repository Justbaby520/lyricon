/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui.lyric

import android.R.attr.name
import android.content.SharedPreferences
import android.os.Looper
import android.util.Log
import io.github.proify.lyricon.app.bridge.AppBridge
import io.github.proify.lyricon.common.StateSharedPreferences
import io.github.proify.lyricon.lyric.style.BasicStyle
import io.github.proify.lyricon.lyric.style.LyricStyle
import io.github.proify.lyricon.lyric.style.PackageStyle
import io.github.proify.lyricon.xposed.ModuleEntry

object LyricPrefs {
    private const val TAG = "LyricPrefs"

    private val prefsCache = mutableMapOf<String, StateSharedPreferences>()
    private val packageStyleCache = mutableMapOf<String, PackageStyleCache>()

    @Volatile
    var activePackageName: String? = null

    private val globalSharedPreferenceChangeListener: SharedPreferences.OnSharedPreferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            LyricViewController.applyConfigurationUpdate(getLyricStyle())
            Log.i(TAG, "global prefs changed")
        }
    /* ---------------- base style ---------------- */

    private val baseStylePrefs: StateSharedPreferences =
        createPrefs(AppBridge.LyricStylePrefs.PREF_NAME_BASE)

    val baseStyle: BasicStyle = BasicStyle().apply {
        load(baseStylePrefs)
    }
        get() {
            if (baseStylePrefs.hasChanged()) {
                baseStylePrefs.reload()
                field.load(baseStylePrefs)
            }
            return field
        }

    /* ---------------- default package style ---------------- */

    private val defaultPackageStylePrefs: StateSharedPreferences by lazy {
        getPackagePrefs(
            AppBridge.LyricStylePrefs.DEFAULT_PACKAGE_NAME
        )
    }

    val defaultPackageStyle: PackageStyle = PackageStyle().apply {
        load(defaultPackageStylePrefs)
    }
        get() {
            if (defaultPackageStylePrefs.hasChanged()) {
                defaultPackageStylePrefs.reload()
                field.load(defaultPackageStylePrefs)
            }
            return field
        }

    /* ---------------- package manager ---------------- */

    private val packageStyleManagerPrefs: StateSharedPreferences =
        createPrefs(AppBridge.LyricStylePrefs.PREF_NAME_PACKAGE_MANAGER)
        get() {
            return field.ensureLatest()
        }

    val activePackageStyle: PackageStyle
        get() = run {
            val pkg = activePackageName
            if (pkg != null && isPackageEnabled(pkg)) {
                getPackageStyle(pkg)
            } else {
                defaultPackageStyle
            }
        }

    private fun isPackageEnabled(packageName: String): Boolean {
        packageStyleManagerPrefs.ensureLatest()
        return runCatching {
            packageStyleManagerPrefs
                .getStringSet(
                    AppBridge.LyricStylePrefs.KEY_ENABLED_PACKAGES,
                    emptySet()
                )
                ?.contains(packageName) ?: false
        }.getOrDefault(false)
    }

    /* ---------------- prefs cache ---------------- */

    private fun getPackagePrefName(packageName: String): String =
        AppBridge.LyricStylePrefs.getPackageStylePrefName(packageName)

    private fun getPackagePrefs(packageName: String): StateSharedPreferences {
        val prefName = getPackagePrefName(packageName)
        return prefsCache.getOrPut(prefName) {
            createPrefs(prefName)
        }
    }

    private fun createPrefs(name: String): StateSharedPreferences {
        val service = ModuleEntry.instance
        val remotePrefs = service.getRemotePreferences(name)

        Log.i(TAG, "remotePrefs: $name, $remotePrefs")
        return StateSharedPreferencesWrapper(
            remotePrefs,
            globalSharedPreferenceChangeListener
        )
    }

    /* ---------------- package style cache ---------------- */

    private class PackageStyleCache(
        private val prefs: StateSharedPreferences,
        private val style: PackageStyle
    ) {
        fun getStyle(): PackageStyle {
            if (prefs.hasChanged()) {
                prefs.reload()
                style.load(prefs)
            }
            return style
        }
    }

    fun getPackageStyle(packageName: String): PackageStyle {
        return packageStyleCache.getOrPut(packageName) {
            val prefs = getPackagePrefs(packageName)
            val style = PackageStyle().apply {
                load(prefs)
            }
            PackageStyleCache(prefs, style)
        }.getStyle()
    }

    /* ---------------- lyric style ---------------- */

    fun getLyricStyle(packageName: String? = null): LyricStyle {
        if (packageName.isNullOrBlank()) {
            return LyricStyle(baseStyle, activePackageStyle)
        }
        return LyricStyle(
            baseStyle,
            getPackageStyle(packageName)
        )
    }

    /* ---------------- helper classes ---------------- */

    private class StateSharedPreferencesWrapper(
        private val prefs: SharedPreferences,
        private val orderPrefChangeListener: SharedPreferences.OnSharedPreferenceChangeListener
    ) : StateSharedPreferences {

        private var isChanged = false

        init {
            prefs.registerOnSharedPreferenceChangeListener { p, key ->
                Log.i(
                    TAG,
                    "prefs changed: $name, key=$key, ${Thread.currentThread()},${Looper.myLooper()}"
                )
                isChanged = true
                orderPrefChangeListener.onSharedPreferenceChanged(p, key)
                Log.i(TAG, "called listener $orderPrefChangeListener")
            }
        }

        override fun hasChanged(): Boolean = isChanged
        override fun reload(): Boolean {
            if (isChanged) {
                isChanged = false
                return true
            }
            return false
        }

        override fun getAll(): Map<String, *> = prefs.all
        override fun getString(key: String?, defValue: String?): String? =
            prefs.getString(key, defValue)

        override fun getStringSet(
            key: String?,
            defValues: MutableSet<String>?
        ): MutableSet<String>? = prefs.getStringSet(key, defValues)

        override fun getInt(key: String?, defValue: Int): Int = prefs.getInt(key, defValue)
        override fun getLong(key: String?, defValue: Long): Long = prefs.getLong(key, defValue)
        override fun getFloat(key: String?, defValue: Float): Float = prefs.getFloat(key, defValue)
        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            prefs.getBoolean(key, defValue)

        override fun contains(key: String?): Boolean = prefs.contains(key)
        override fun edit(): SharedPreferences.Editor = prefs.edit()
        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) {
            prefs.registerOnSharedPreferenceChangeListener(listener)
        }

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    private fun StateSharedPreferences.ensureLatest(): StateSharedPreferences {
        if (hasChanged()) reload()
        return this
    }
}