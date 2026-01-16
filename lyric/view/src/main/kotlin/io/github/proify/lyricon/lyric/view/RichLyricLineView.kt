/*
 * Copyright (c) 2026 Proify
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
import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import io.github.proify.lyricon.lyric.model.LyricLine
import io.github.proify.lyricon.lyric.model.interfaces.IRichLyricLine
import io.github.proify.lyricon.lyric.view.line.LyricLineView
import io.github.proify.lyricon.lyric.view.util.LayoutTransitionX
import io.github.proify.lyricon.lyric.view.util.visible

class RichLyricLineView(context: Context, attrs: AttributeSet? = null) :
    LinearLayout(context, attrs) {

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
        LyricLineView(context)

    init {
        orientation = VERTICAL
        val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        addView(main, lp)
        addView(secondary, lp)
    }

    private fun setMainLine(lyricLine: IRichLyricLine?) {
        val line = if (lyricLine == null) {
            EMPTY_LYRIC_LINE
        } else {
            LyricLine(
                begin = lyricLine.begin,
                end = lyricLine.end,
                duration = lyricLine.duration,
                isAlignedRight = lyricLine.isAlignedRight,
                metadata = lyricLine.metadata,
                text = lyricLine.text,
                words = lyricLine.words,
            )
        }

        main.setLyric(line)
        //main.visible = line.words.isNotEmpty()
    }

    fun setMainLyricPlayListener(listener: LyricPlayListener?) {
        main.syllable.playListener = listener
    }

    fun setSecondaryLyricPlayListener(listener: LyricPlayListener?) {
        secondary.syllable.playListener = listener
    }

    private fun setSecondaryLine(source: IRichLyricLine?) {
        val line = LyricLine().apply {
            if (source == null) return@apply
            begin = source.begin
            end = source.end
            isAlignedRight = source.isAlignedRight
            text = source.secondaryText
            words = source.secondaryWords
        }
        secondary.setLyric(line)
        secondary.visible = false
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
        setStyle(
            main,
            config.primary,
            config.marquee,
            config.syllable,
            config.gradientProgressStyle
        )

        setStyle(
            secondary,
            config.secondary,
            config.marquee,
            config.syllable,
            config.gradientProgressStyle
        )
    }

    private fun setStyle(
        view: LyricLineView,
        textConfig: TextConfig,
        marqueeConfig: MarqueeConfig,
        syllableConfig: SyllableConfig,
        gradientProgressStyle: Boolean
    ) {
        val config = LyricLineConfig(
            textConfig,
            marqueeConfig,
            syllableConfig,
            gradientProgressStyle
        )
        view.setStyle(config)
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