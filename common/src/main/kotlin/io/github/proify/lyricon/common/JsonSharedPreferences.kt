/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.common

import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * @property storageFile 存储数据的 JSON 文件路径。
 */
class JsonSharedPreferences(private val storageFile: File) : SharedPreferences,
    StateSharedPreferences {

    /** 内部标记，用于表示 Editor 中的删除操作 */
    private object REMOVED

    /** 内存缓存快照 */
    @Volatile
    private var jsonCache: Map<String, JsonElement> = emptyMap()

    /** 读写锁，保证 getAll() 和 edit() 之间的原子性 */
    private val lock = ReentrantReadWriteLock()

    /** 变更监听器集合，使用线程安全的 Set */
    private val listeners =
        CopyOnWriteArraySet<SharedPreferences.OnSharedPreferenceChangeListener>()

    /** 异步写入使用的协程作用域 */
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** 配置 JSON 解析引擎 */
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    /** 标记数据是否已从磁盘加载 */
    @Volatile
    private var isLoaded = false

    /** 记录文件最后修改时间 */
    @Volatile
    private var lastModifiedTime = 0L

    /** 记录文件最后大小，增强 [hasChanged] 的严谨性 */
    @Volatile
    private var lastFileSize = 0L

    // ----------------------------------------------------------------
    // 类型转换扩展
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
    // 生命周期管理
    // ----------------------------------------------------------------

    /**
     * 检测外部进程或手动修改是否导致文件发生变化。
     * 结合修改时间与文件大小进行双重判定，防止时间戳精度丢失。
     */
    override fun hasChanged(): Boolean {
        val exists = storageFile.exists()
        if (!exists) {
            // 如果文件不存在但之前加载过数据，视为已改变
            return lastModifiedTime != 0L || lastFileSize != 0L
        }
        val ts = storageFile.lastModified()
        val size = storageFile.length()
        return ts != lastModifiedTime || size != lastFileSize
    }

    /**
     * 强制重新从磁盘读取数据并刷新缓存。
     */
    override fun reload(): Boolean {
        if (!hasChanged()) return false

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
        return true
    }

    /**
     * 懒加载检查，确保读取数据前缓存已就绪。
     */
    private fun ensureLoaded() {
        if (!isLoaded) {
            lock.write {
                if (!isLoaded) {
                    loadFromFileInternal()
                    isLoaded = true
                }
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun loadFromFileInternal() {
        runCatching {
            if (storageFile.exists() && storageFile.isFile) {
                storageFile.inputStream().buffered().use {
                    jsonCache = json.decodeFromStream<Map<String, JsonElement>>(it)
                }
                lastModifiedTime = storageFile.lastModified()
                lastFileSize = storageFile.length()
            } else {
                jsonCache = emptyMap()
                lastModifiedTime = 0L
                lastFileSize = 0L
            }
        }.onFailure {
            // 发生异常时回退到空配置，防止崩溃
            jsonCache = emptyMap()
            lastModifiedTime = 0L
            lastFileSize = 0L
        }
    }

    // ----------------------------------------------------------------
    // SharedPreferences 接口实现 (读取)
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
        override fun putStringSet(key: String, values: Set<String>?) =
            apply { mChanges[key] = values?.toSet() }

        override fun putInt(key: String, value: Int) = apply { mChanges[key] = value }
        override fun putLong(key: String, value: Long) = apply { mChanges[key] = value }
        override fun putFloat(key: String, value: Float) = apply { mChanges[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { mChanges[key] = value }
        override fun remove(key: String) = apply { mChanges[key] = REMOVED }
        override fun clear() = apply { mClear = true }

        override fun commit(): Boolean {
            val keys = applyToMemory()
            val success = runBlocking { saveToFileSync() }
            keys.forEach { notifyListeners(it) }
            return success
        }

        override fun apply() {
            val keys = applyToMemory()
            scope.launch {
                val success = saveToFileSync()
                // 虽然 apply 不关心返回值，但内部状态在 saveToFileSync 中已更新
            }
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

        private suspend fun saveToFileSync(): Boolean = withContext(Dispatchers.IO) {
            val tmpFile = File("${storageFile.absolutePath}.tmp")
            try {
                val content = lock.read { json.encodeToString(jsonCache) }

                storageFile.parentFile?.mkdirs()
                tmpFile.writeText(content, Charsets.UTF_8)

                val success = if (tmpFile.renameTo(storageFile)) {
                    true
                } else {
                    if (storageFile.exists() && !storageFile.delete()) {
                        false
                    } else {
                        tmpFile.renameTo(storageFile)
                    }
                }

                if (success) {
                    // 更新元数据以同步 hasChanged 状态
                    lastModifiedTime = storageFile.lastModified()
                    lastFileSize = storageFile.length()
                }
                success
            } catch (_: Exception) {
                if (tmpFile.exists()) tmpFile.delete()
                false
            }
        }
    }
}