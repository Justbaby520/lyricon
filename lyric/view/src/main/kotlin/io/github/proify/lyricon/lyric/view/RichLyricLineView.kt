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

package io.github.proify.lyricon.lyric.view

import android.animation.LayoutTransition
import android.annotation.SuppressLint
import android.content.Context
import android.widget.LinearLayout
import io.github.proify.lyricon.lyric.model.LyricLine
import io.github.proify.lyricon.lyric.model.LyricWord
import io.github.proify.lyricon.lyric.model.interfaces.ILyricTiming
import io.github.proify.lyricon.lyric.model.interfaces.IRichLyricLine
import io.github.proify.lyricon.lyric.view.line.LyricLineView
import io.github.proify.lyricon.lyric.view.util.LayoutTransitionX
import io.github.proify.lyricon.lyric.view.util.visible

@SuppressLint("ViewConstructor")
class RichLyricLineView(
    context: Context,
    var displayTranslation: Boolean = false,
    var enableRelativeProgress: Boolean = false,
    var enableRelativeProgressHighlight: Boolean = false
) :
    LinearLayout(context) {

    companion object {
        private val EMPTY_LYRIC_LINE = LyricLine()
    }

    val customLayoutTransition: LayoutTransition = LayoutTransitionX()

    init {
        layoutTransition = customLayoutTransition
    }

    var line: IRichLyricLine? = null
        set(value) {
            field = value
            setMainLine(value)
            setSecondaryLine(value)
        }

    val main: LyricLineView =
        LyricLineView(context)

    val secondary: LyricLineView =
        LyricLineView(context).apply {
            visible = false
        }

    fun notifyLineChanged() {
        setMainLine(line)
        setSecondaryLine(line)
    }

    init {
        orientation = VERTICAL
        val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        addView(main, lp)
        addView(secondary, lp)
    }

    fun calculateRelativeProgressWords(
        timing: ILyricTiming,
        text: String?,
        words: List<LyricWord>?,
    ): List<LyricWord>? {
        return if (words.isNullOrEmpty()
            && !text.isNullOrBlank()
            && (timing.begin >= 0 && timing.end > 0 && timing.begin < timing.end)
        ) {
            listOf(
                LyricWord(
                    text = text,
                    begin = timing.begin,
                    end = timing.end,
                    duration = timing.end - timing.begin
                )
            )
        } else {
            words
        }
    }

    private fun setMainLine(source: IRichLyricLine?) {
        var isRelativeProgressWords = false

        val line = if (source == null) {
            EMPTY_LYRIC_LINE
        } else {
            val words = if (enableRelativeProgress && !source.isTitleLine()) {
                calculateRelativeProgressWords(
                    source,
                    source.text,
                    source.words
                )
            } else {
                source.words
            }
            if (words != source.words) {
                isRelativeProgressWords = true
            }

            LyricLine(
                begin = source.begin,
                end = source.end,
                duration = source.duration,
                isAlignedRight = source.isAlignedRight,
                metadata = source.metadata,
                text = source.text,
                words = words,
            )
        }

        main.setLyric(line)

        if (isRelativeProgressWords) {
            main.syllable.onlyScrollMode = !enableRelativeProgressHighlight
        } else {
            main.syllable.onlyScrollMode = false
        }
    }

    fun setMainLyricPlayListener(listener: LyricPlayListener?) {
        main.syllable.playListener = listener
    }

    fun setSecondaryLyricPlayListener(listener: LyricPlayListener?) {
        secondary.syllable.playListener = listener
    }

    private fun setSecondaryLine(source: IRichLyricLine?) {
        if (source == null) {
            secondary.setLyric(null)
            secondary.visible = false
            return
        }

        val line = LyricLine().apply {
            begin = source.begin
            end = source.end
            isAlignedRight = source.isAlignedRight

            when {
                !source.secondary.isNullOrBlank() || !source.secondaryWords.isNullOrEmpty() -> {
                    text = source.secondary
                    words = calculateRelativeProgressWords(
                        source,
                        source.secondary,
                        source.secondaryWords
                    )
                }

                displayTranslation && (!source.translation.isNullOrBlank() || !source.translationWords.isNullOrEmpty()) -> {
                    text = source.translation
                    words = calculateRelativeProgressWords(
                        source,
                        source.translation,
                        source.translationWords
                    )
                    type = LyricLine.TYPE_TRANSLATION
                }
            }
        }

        secondary.setLyric(line)
        secondary.visible = line.type == LyricLine.TYPE_TRANSLATION
                && (!line.text.isNullOrBlank() || !line.words.isNullOrEmpty())
    }

    fun seekTo(position: Long) {
        main.seekTo(position)
        secondary.seekTo(position)
    }

    fun setPosition(position: Long) {
        main.setPosition(position)
        secondary.setPosition(position)
    }

    fun setStyle(config: RichLyricLineConfig) {
        setMainStyle(
            config.primary,
            config.marquee,
            config.syllable,
            config.gradientProgressStyle
        )

        setSecondaryStyle(
            config.secondary,
            config.marquee,
            config.syllable,
            config.gradientProgressStyle
        )
    }

    private fun setMainStyle(
        config: MainTextConfig,
        marqueeConfig: MarqueeConfig,
        syllableConfig: SyllableConfig,
        gradientProgressStyle: Boolean
    ) {
        var notifyLineChanged = false

        if (config.enableRelativeProgress != enableRelativeProgress) {
            enableRelativeProgress = config.enableRelativeProgress
            notifyLineChanged = true
        }

        if (enableRelativeProgressHighlight != config.enableRelativeProgressHighlight) {
            enableRelativeProgressHighlight = config.enableRelativeProgressHighlight
            notifyLineChanged = true
        }

        val lineConfig = LyricLineConfig(
            config,
            marqueeConfig,
            syllableConfig,
            gradientProgressStyle
        )
        main.setStyle(lineConfig)

        if (notifyLineChanged) {
            notifyLineChanged()
        }
    }

    private fun setSecondaryStyle(
        secondaryTextConfig: SecondaryTextConfig,
        marqueeConfig: MarqueeConfig,
        syllableConfig: SyllableConfig,
        gradientProgressStyle: Boolean
    ) {
        val config = LyricLineConfig(
            secondaryTextConfig,
            marqueeConfig,
            syllableConfig,
            gradientProgressStyle
        )
        secondary.setStyle(config)
    }

    fun tryStartMarquee() {
        if (main.isMarqueeMode()) {
            main.startMarquee()
        }
        if (secondary.isMarqueeMode()) {
            secondary.startMarquee()
        }
    }

}