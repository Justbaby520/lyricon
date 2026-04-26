/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.common

import android.content.SharedPreferences
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * 工业级 JsonSharedPreferences 实现。
 * 增强了类型自动转换能力，支持跨类型读取（如 String 转 Int，Long 转 Int 等）。
 */
class JsonSharedPreferences(private val storageFile: File) : SharedPreferences {

    private object REMOVED

    @Volatile
    private var jsonCache: Map<String, JsonElement> = emptyMap()
    private val lock = ReentrantReadWriteLock()
    private val listeners =
        CopyOnWriteArraySet<SharedPreferences.OnSharedPreferenceChangeListener>()

    private val writerExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "JsonSP-Writer").also { it.isDaemon = true }
    }

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        coerceInputValues = true // 强制输入值转换
    }

    @Volatile
    private var isLoaded = false

    @Volatile
    private var lastModifiedTime = 0L

    // ----------------------------------------------------------------
    // 内部类型转换辅助函数 (Core Improvements)
    // ----------------------------------------------------------------

    private fun JsonElement?.asString(): String? = when (this) {
        is JsonPrimitive -> content
        is JsonNull -> null
        else -> this?.toString()
    }

    private fun JsonElement?.asInt(def: Int): Int = when (this) {
        is JsonPrimitive -> intOrNull ?: content.toIntOrNull() ?: def
        else -> def
    }

    private fun JsonElement?.asLong(def: Long): Long = when (this) {
        is JsonPrimitive -> longOrNull ?: content.toLongOrNull() ?: def
        else -> def
    }

    private fun JsonElement?.asFloat(def: Float): Float = when (this) {
        is JsonPrimitive -> floatOrNull ?: content.toFloatOrNull() ?: def
        else -> def
    }

    private fun JsonElement?.asBoolean(def: Boolean): Boolean = when (this) {
        is JsonPrimitive -> booleanOrNull ?: content.toBooleanStrictOrNull() ?: def
        else -> def
    }

    private fun JsonElement?.asStringSet(def: Set<String>?): Set<String>? = when (this) {
        is JsonArray -> this.mapNotNull { it.asString() }.toSet()
        else -> def
    }

    // ----------------------------------------------------------------
    // 核心生命周期
    // ----------------------------------------------------------------

    fun hasFileChanged(): Boolean {
        val ts = if (storageFile.exists()) storageFile.lastModified() else 0L
        return ts != lastModifiedTime
    }

    fun reload() {
        val changedKeys: Set<String>
        lock.write {
            val oldCache = jsonCache
            loadFromFileInternal()
            isLoaded = true
            changedKeys = (oldCache.keys + jsonCache.keys).filter { key ->
                oldCache[key] != jsonCache[key]
            }.toSet()
        }
        changedKeys.forEach { notifyListeners(it) }
    }

    private fun ensureLoaded() {
        if (!isLoaded) {
            lock.write {
                if (!isLoaded) {
                    loadFromFileInternal(); isLoaded = true
                }
            }
        }
    }

    private fun loadFromFileInternal() {
        runCatching {
            if (storageFile.exists()) {
                val content = storageFile.readText()
                jsonCache = if (content.isNotBlank()) {
                    json.decodeFromString<Map<String, JsonElement>>(content)
                } else emptyMap()
                lastModifiedTime = storageFile.lastModified()
            } else {
                jsonCache = emptyMap()
                lastModifiedTime = 0L
            }
        }.onFailure {
            jsonCache = emptyMap()
            lastModifiedTime = 0L
        }
    }

    // ----------------------------------------------------------------
    // 读操作 (符合 SP 标准契约，增强转换)
    // ----------------------------------------------------------------

    override fun getAll(): Map<String, *> {
        ensureLoaded()
        return lock.read {
            jsonCache.mapValues { (_, value) ->
                when (value) {
                    is JsonPrimitive -> {
                        if (value.isString) value.content
                        else value.booleanOrNull ?: value.longOrNull ?: value.doubleOrNull
                        ?: value.content
                    }

                    is JsonArray -> value.mapNotNull { it.asString() }.toSet()
                    else -> value.toString()
                }
            }
        }
    }

    override fun getString(key: String?, defValue: String?): String? {
        ensureLoaded()
        return lock.read { jsonCache[key].asString() ?: defValue }
    }

    override fun getStringSet(key: String?, defValues: Set<String>?): Set<String>? {
        ensureLoaded()
        return lock.read { jsonCache[key].asStringSet(defValues) }
    }

    override fun getInt(key: String?, defValue: Int): Int {
        ensureLoaded()
        return lock.read { jsonCache[key].asInt(defValue) }
    }

    override fun getLong(key: String?, defValue: Long): Long {
        ensureLoaded()
        return lock.read { jsonCache[key].asLong(defValue) }
    }

    override fun getFloat(key: String?, defValue: Float): Float {
        ensureLoaded()
        return lock.read { jsonCache[key].asFloat(defValue) }
    }

    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        ensureLoaded()
        return lock.read { jsonCache[key].asBoolean(defValue) }
    }

    override fun contains(key: String?): Boolean {
        ensureLoaded()
        return lock.read { key != null && jsonCache.containsKey(key) }
    }

    override fun edit(): SharedPreferences.Editor = JsonEditor()

    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {
        l?.let { listeners.add(it) }
    }

    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {
        listeners.remove(l)
    }

    private fun notifyListeners(key: String) {
        listeners.forEach { it.onSharedPreferenceChanged(this, key) }
    }

    // ----------------------------------------------------------------
    // Editor 实现
    // ----------------------------------------------------------------

    private inner class JsonEditor : SharedPreferences.Editor {
        private val mChanges = mutableMapOf<String, Any?>()
        private var mClear = false

        override fun putString(key: String, value: String?) = apply { mChanges[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) =
            apply { mChanges[key] = values?.toSet() }

        override fun putInt(key: String, value: Int) = apply { mChanges[key] = value }
        override fun putLong(key: String, value: Long) = apply { mChanges[key] = value }

        // 修正：不再使用 Bits 存储，改用原始数值以提升 JSON 可读性
        override fun putFloat(key: String, value: Float) = apply { mChanges[key] = value }

        override fun putBoolean(key: String, value: Boolean) = apply { mChanges[key] = value }
        override fun remove(key: String) = apply { mChanges[key] = REMOVED }
        override fun clear() = apply { mClear = true }

        override fun commit(): Boolean {
            val keys = applyToMemory()
            val success = saveToFileSync()
            keys.forEach { notifyListeners(it) }
            return success
        }

        override fun apply() {
            val keys = applyToMemory()
            writerExecutor.execute { saveToFileSync() }
            keys.forEach { notifyListeners(it) }
        }

        private fun applyToMemory(): Set<String> {
            ensureLoaded()
            val changedKeys = mutableSetOf<String>()
            lock.write {
                val workingMap = if (mClear) {
                    changedKeys.addAll(jsonCache.keys)
                    mutableMapOf()
                } else {
                    jsonCache.toMutableMap()
                }
                mClear = false

                mChanges.forEach { (k, v) ->
                    if (v === REMOVED) {
                        if (workingMap.remove(k) != null) changedKeys.add(k)
                    } else {
                        val element = wrapValue(v)
                        if (workingMap[k] != element) {
                            workingMap[k] = element
                            changedKeys.add(k)
                        }
                    }
                }
                jsonCache = workingMap
                mChanges.clear()
            }
            return changedKeys
        }

        private fun wrapValue(value: Any?): JsonElement = when (value) {
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Set<*> -> JsonArray(value.map { JsonPrimitive(it.toString()) })
            else -> JsonNull
        }

        private fun saveToFileSync(): Boolean {
            val tmpFile = File("${storageFile.absolutePath}.tmp")
            return try {
                val content = lock.read { json.encodeToString(jsonCache) }
                storageFile.parentFile?.mkdirs()
                tmpFile.writeBytes(content.toByteArray(Charsets.UTF_8))
                if (tmpFile.renameTo(storageFile)) {
                    lastModifiedTime = storageFile.lastModified()
                    true
                } else {
                    if (storageFile.delete()) tmpFile.renameTo(storageFile)
                    lastModifiedTime = storageFile.lastModified()
                    true
                }
            } catch (_: Exception) {
                tmpFile.delete()
                false
            }
        }
    }
}