/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.proify.lyricon.lyric.model

import android.os.Parcelable
import io.github.proify.lyricon.lyric.model.extensions.deepCopy
import io.github.proify.lyricon.lyric.model.extensions.normalizeSortByTime
import io.github.proify.lyricon.lyric.model.interfaces.DeepCopyable
import io.github.proify.lyricon.lyric.model.interfaces.Normalize
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * 歌曲信息
 *
 * @property id 歌曲ID
 * @property name 歌曲名
 * @property artist 艺术家
 * @property duration 歌曲时长
 * @property metadata 元数据
 * @property lyrics 歌词列表
 */
@Serializable
@Parcelize
data class Song(
    var id: String? = null,
    var name: String? = null,
    var artist: String? = null,
    var duration: Long = 0,
    var metadata: LyricMetadata? = null,
    var lyrics: List<RichLyricLine>? = null,
) : Parcelable, DeepCopyable<Song>, Normalize<Song> {

    override fun deepCopy(): Song = copy(
        lyrics = lyrics?.deepCopy()
    )

    override fun normalize(): Song = deepCopy().apply {
        lyrics = lyrics
            ?.map { line ->
                if (line.duration <= 0) line.duration = line.end - line.begin
                line
            }
            ?.filter { line ->
                line.begin >= 0 && line.begin < line.end && line.duration > 0
            }
            ?.normalizeSortByTime()
    }
}