/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lyricon.lyric.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
class LyricMetadata(
    private val map: Map<String, String?> = emptyMap(),
) : Map<String, String?> by map, Parcelable {

    fun getDouble(key: String, default: Double): Double = map[key]?.toDoubleOrNull() ?: default
    fun getBoolean(key: String, default: Boolean): Boolean = map[key]?.toBoolean() ?: default
    fun getFloat(key: String, default: Float): Float = map[key]?.toFloatOrNull() ?: default
    fun getLong(key: String, default: Long): Long = map[key]?.toLongOrNull() ?: default
    fun getInt(key: String, default: Int): Int = map[key]?.toIntOrNull() ?: default
    fun getString(key: String, default: String?): String? = map[key] ?: default
}

fun lyricMetadataOf(vararg pairs: Pair<String, String?>): LyricMetadata =
    LyricMetadata(mapOf(*pairs))