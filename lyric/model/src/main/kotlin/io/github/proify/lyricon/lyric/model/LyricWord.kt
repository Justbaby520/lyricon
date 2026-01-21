/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.model

import android.os.Parcelable
import io.github.proify.lyricon.lyric.model.interfaces.DeepCopyable
import io.github.proify.lyricon.lyric.model.interfaces.ILyricWord
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * 歌词单词
 *
 * @property begin 开始时间
 * @property end 结束时间
 * @property duration 持续时间
 * @property text 文本
 * @property metadata 元数据
 */
@Serializable
@Parcelize
data class LyricWord(
    override var begin: Long = 0,
    override var end: Long = 0,
    override var duration: Long = end - begin,
    override var text: String? = null,
    override var metadata: LyricMetadata? = null,
) : ILyricWord, Parcelable, DeepCopyable<LyricWord> {

    override fun deepCopy(): LyricWord = copy()
}