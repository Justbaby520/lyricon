/*
 * Copyright 2026 Proify, Tomakino
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:Suppress("unused")

package io.github.proify.lyricon.lyric.view.line.model

import android.graphics.Paint
import io.github.proify.lyricon.lyric.model.LyricLine
import io.github.proify.lyricon.lyric.model.LyricWord
import io.github.proify.lyricon.lyric.model.extensions.TimingNavigator
import io.github.proify.lyricon.lyric.view.util.isChinese

data class LyricModel(
    val begin: Long = 0,
    val end: Long = 0,
    val duration: Long = 0,
    val text: String,
    val words: List<WordModel>,
    val isAlignedRight: Boolean = false,
) {
    var width: Float = 0f
        private set

    val wordText: String by lazy { words.toText() }
    val wordTimingNavigator: TimingNavigator<WordModel> by lazy { TimingNavigator(words.toTypedArray()) }
    val isPlainText: Boolean = words.isEmpty()

    /** 如果所有单词都为中文，则禁用字符偏移动画 */
    val isDisabledOffsetAnimation: Boolean = words.all { word ->
        word.chars.all { it.isWhitespace() || it.isChinese() }
    }

    fun updateSizes(paint: Paint) {
        width = paint.measureText(text)
        var previous: WordModel? = null
        words.forEach { word ->
            word.updateSizes(previous, paint)
            previous = word
        }
    }
}

internal fun emptyLyricModel(): LyricModel = LyricModel(
    words = emptyList(),
    text = ""
)

/**
 * 将 LyricLine 转换为 LyricModel
 */
internal fun LyricLine.createModel(): LyricModel = LyricModel(
    begin = begin,
    end = end,
    duration = duration,
    text = text.orEmpty(),
    words = words?.toWordModels() ?: emptyList(),
    isAlignedRight = isAlignedRight
)

/**
 * 将 LyricWord 列表转换为 WordModel 列表，并建立前后引用关系
 */
private fun List<LyricWord>.toWordModels(): List<WordModel> {
    val models = mutableListOf<WordModel>()
    var previousModel: WordModel? = null

    forEach { word ->
        val model = WordModel(
            begin = word.begin,
            end = word.end,
            duration = word.duration,
            text = word.text.orEmpty()
        )

        model.previous = previousModel
        previousModel?.next = model

        models.add(model)
        previousModel = model
    }
    return models
}