/*
 * Copyright 2026 Proify
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

@file:Suppress("MemberVisibilityCanBePrivate")

package io.github.proify.lyricon.lyric.view.line.model

import android.graphics.Paint
import io.github.proify.lyricon.lyric.model.interfaces.ILyricTiming
import io.github.proify.lyricon.lyric.view.Constances
import io.github.proify.lyricon.lyric.view.util.isChinese

/**
 * 表示歌词中的单词及其相关位置信息、时间信息和字符偏移。
 *
 * @property begin 单词开始时间，单位毫秒
 * @property end 单词结束时间，单位毫秒
 * @property duration 单词持续时间，单位毫秒
 * @property text 单词文本内容
 * @property previous 前一个单词模型，可为 null
 * @property next 下一个单词模型，可为 null
 * @property textWidth 单词文本的总宽度
 * @property startPosition 单词起始绘制位置
 * @property endPosition 单词结束绘制位置
 * @property chars 单词拆分后的字符数组
 * @property charWidths 各字符宽度数组
 * @property charStartPositions 各字符起始绘制位置数组
 * @property charEndPositions 各字符结束绘制位置数组
 * @property charOffsetMode 是否启用字符偏移模式（中文字符启用）
 * @property charOffsetY 字符垂直偏移信息
 */
data class WordModel(
    override var begin: Long,
    override var end: Long,
    override var duration: Long,
    val text: String,
) : ILyricTiming {

    /** 前一个单词 */
    var previous: WordModel? = null

    /** 下一个单词 */
    var next: WordModel? = null

    /** 单词文本总宽度 */
    var textWidth: Float = 0f
        private set

    /** 单词起始绘制位置 */
    var startPosition: Float = 0f
        private set

    /** 单词结束绘制位置 */
    var endPosition: Float = 0f
        private set

    /** 拆分后的字符数组 */
    val chars: CharArray = text.toCharArray()

    /** 各字符宽度数组 */
    val charWidths: FloatArray = FloatArray(text.length)

    /** 各字符起始绘制位置数组 */
    val charStartPositions: FloatArray = FloatArray(text.length)

    /** 各字符结束绘制位置数组 */
    val charEndPositions: FloatArray = FloatArray(text.length)

    /** 是否启用字符偏移模式 */
    val charOffsetMode: Boolean = chars.any { it.isChinese() }

    var dropDistance: Float = 0f
        private set

    /** 字符垂直偏移 */
    var charOffsetY: Float = 0f

    private var firstApplyCharOffsetY = true

    /**
     * 更新单词及其字符的尺寸和位置信息
     *
     * @param previous 上一个单词模型
     * @param paint 绘制文本的 Paint 对象
     */
    fun updateSizes(previous: WordModel?, paint: Paint) {
        paint.getTextWidths(chars, 0, chars.size, charWidths)
        textWidth = charWidths.sum()

        if (firstApplyCharOffsetY) {
            dropDistance = paint.textSize * Constances.WORD_DROP_ANIMATION_OFFSET_RATIO
            charOffsetY = dropDistance
            firstApplyCharOffsetY = false
        }

        startPosition = previous?.endPosition ?: 0f
        endPosition = startPosition + textWidth

        var currentPosition = startPosition
        for (i in chars.indices) {
            charStartPositions[i] = currentPosition
            currentPosition += charWidths[i]
            charEndPositions[i] = currentPosition
        }
    }

    fun resetOffset() {
        charOffsetY = dropDistance
    }
}

internal fun List<WordModel>.toText(): String = joinToString("") { it.text }