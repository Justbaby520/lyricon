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

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.forEach
import androidx.core.view.isNotEmpty
import androidx.core.view.isVisible
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.lyric.model.extensions.TimingNavigator
import io.github.proify.lyricon.lyric.model.interfaces.IRichLyricLine
import io.github.proify.lyricon.lyric.model.lyricMetadataOf
import io.github.proify.lyricon.lyric.view.line.LyricLineView
import io.github.proify.lyricon.lyric.view.model.RichLyricLineModel
import io.github.proify.lyricon.lyric.view.util.LayoutTransitionX
import io.github.proify.lyricon.lyric.view.util.visibilityIfChanged
import io.github.proify.lyricon.lyric.view.util.visible

open class LyricPlayerView(context: Context, attrs: AttributeSet? = null) :
    LinearLayout(context, attrs) {

    companion object {
        private const val KEY_SONG_TITLE_LINE = "TitleLine"
        private const val MIN_GAP_DURATION: Long = 6 * 1000
    }

    var song: Song? = null
        set(value) {
            reset()
            if (value != null) {
                val newSong = fillGapAtStart(value)

                var previous: RichLyricLineModel? = null
                lineModels = newSong.lyrics?.map {
                    val model = RichLyricLineModel(it)

                    model.previous = previous
                    previous?.next = model
                    previous = model

                    model
                }

                timingNavigator = TimingNavigator(lineModels?.toTypedArray() ?: emptyArray())
                field = value
            } else {
                field = null
                lineModels = null
                timingNavigator = emptyTimingNavigator()
            }
        }

    private var lineModels: List<RichLyricLineModel>? = null
    private val activeLines = mutableListOf<IRichLyricLine>()
    private var config: RichLyricLineConfig = RichLyricLineConfig()
    private val myLayoutTransition = LayoutTransitionX()
    private val tempViewsToRemove = mutableListOf<RichLyricLineView>()
    private val tempViewsToAdd = mutableListOf<RichLyricLineView>()
    private val tempFindActiveLines: MutableList<RichLyricLineModel> = mutableListOf()
    private val reusableLayoutParams =
        LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

    private val mainLyricPlayListener = object : LyricPlayListener {
        override fun onPlayStarted(view: LyricLineView) {
            updateViewsVisibility()
        }

        override fun onPlayEnded(view: LyricLineView) {
            updateViewsVisibility()
        }
    }

    private val secondaryLyricPlayListener = object : LyricPlayListener {
        override fun onPlayStarted(view: LyricLineView) {
            view.visible = true
            updateViewsVisibility()
        }

        override fun onPlayEnded(view: LyricLineView) {
            updateViewsVisibility()
        }
    }

    private var timingNavigator: TimingNavigator<RichLyricLineModel> = emptyTimingNavigator()
    private var interludeState: InterludeState? = null

    init {
        orientation = VERTICAL
        layoutTransition = myLayoutTransition
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        reset()
    }

    fun reset() {
        removeAllViews()
        activeLines.clear()
    }

    override fun removeAllViews() {
        setLayoutTransition(null)
        super.removeAllViews()
    }

    private fun createDoubleLineView(line: IRichLyricLine): RichLyricLineView {
        val view = RichLyricLineView(context)
        view.line = line
        view.setStyle(config)
        view.setMainLyricPlayListener(mainLyricPlayListener)
        view.setSecondaryLyricPlayListener(secondaryLyricPlayListener)
        return view
    }

    internal fun updateViewsVisibility() {
        val childCount = childCount
        if (childCount == 0) return

        val first = getChildAt(0) as? RichLyricLineView ?: return

        for (i in 0 until childCount) {
            val view = getChildAt(i) as? RichLyricLineView ?: continue

            view.visibility = VISIBLE
            view.main.setTextSize(config.primary.textSize)
            view.secondary.setTextSize(config.secondary.textSize)

            when (i) {
                0 -> {
                    if (view.secondary.isVisible
                        && view.main.syllable.isPlayFinished()
                        && childCount > 1
                    ) {
                        view.main.visibilityIfChanged = GONE
                    }
                }

                1 -> {
                    if (first.main.isVisible && first.secondary.isVisible) {
                        view.visibilityIfChanged = GONE
                    } else {
                        if (first.isVisible && first.main.isVisible) {
                            view.main.setTextSize(config.secondary.textSize)
                            view.secondary.setTextSize(config.primary.textSize)
                        }
                    }
                }

                else -> {
                    view.visibilityIfChanged = GONE
                }
            }
        }
    }

    override fun removeView(view: View?) {
        super.removeView(view)
    }

    fun seekTo(position: Long) {
        updatePosition(position, true)
    }

    fun setPosition(position: Long) {
        updatePosition(position)
    }

    private fun updatePosition(position: Long, seekTo: Boolean = false) {
        tempFindActiveLines.clear()
        timingNavigator.forEachAtOrPrevious(position) {
            tempFindActiveLines.add(it)
        }
        val matches = tempFindActiveLines
        updateActiveViews(matches)

        forEach {
            if (it is RichLyricLineView) {
                if (seekTo) {
                    it.seekTo(position)
                } else {
                    it.setPosition(position)
                }
            }
        }

        handleInterlude(position, matches)
    }

    private fun handleInterlude(
        position: Long,
        matches: List<RichLyricLineModel>
    ) {
        val resolvedState = resolveInterludeState(position, matches)

        if (interludeState == resolvedState) return

        if (interludeState != null && resolvedState == null) {
            interludeState = null
            exitInterludeMode()
            return
        }

        if (resolvedState != null) {
            interludeState = resolvedState
            enteringInterludeMode(resolvedState.end - resolvedState.start)
        }
    }

    private fun resolveInterludeState(
        position: Long,
        matches: List<RichLyricLineModel>
    ): InterludeState? {

        // 1. 若已有间奏，优先校验是否仍然命中
        interludeState?.let { state ->
            if (position > state.start && position < state.end) {
                return state
            }
        }

        // 2. 尝试基于当前歌词重新构建间奏
        if (matches.isEmpty()) return null

        val current = matches.last()
        val next = current.next ?: return null

        val gapDuration = next.begin - current.end
        if (gapDuration <= MIN_GAP_DURATION) return null

        if (position <= current.end || position >= next.begin) return null

        return InterludeState(
            start = current.end,
            end = next.begin
        )
    }

    protected open fun enteringInterludeMode(duration: Long) {}
    protected open fun exitInterludeMode() {}

    private fun updateActiveViews(matches: List<IRichLyricLine>) {
        tempViewsToRemove.clear()
        tempViewsToAdd.clear()

        // 找出需要移除的视图
        val currentSize = childCount
        for (i in 0 until currentSize) {
            val view = getChildAt(i) as? RichLyricLineView ?: continue
            val line = view.line
            if (line != null && line !in matches) {
                tempViewsToRemove.add(view)
            }
        }

        // 找出需要添加的视图
        val matchesSize = matches.size
        for (i in 0 until matchesSize) {
            val line = matches[i]
            if (line !in activeLines) {
                tempViewsToAdd.add(createDoubleLineView(line))
            }
        }

        // 如果没有变化,直接返回
        if (tempViewsToRemove.isEmpty() && tempViewsToAdd.isEmpty()) return

        // 优化:单个视图替换的情况
        val isSingleViewSwap = activeLines.size == 1
                && tempViewsToRemove.size == 1
                && tempViewsToAdd.size == 1

        if (isSingleViewSwap) {
            run {
                val recycleView = getChildAt(0) as? RichLyricLineView ?: return@run
                val newLine = tempViewsToAdd[0].line

                newLine?.let { activeLines[0] = it }
                recycleView.line = newLine

                recycleView.tryStartMarquee()
            }
        } else {
            // 批量处理移除
            val removeSize = tempViewsToRemove.size
            for (i in 0 until removeSize) {
                val view = tempViewsToRemove[i]
                removeView(view)
                activeLines.remove(view.line)
            }

            // 批量处理添加
            val addSize = tempViewsToAdd.size
            for (i in 0 until addSize) {
                val view = tempViewsToAdd[i]
                view.line?.let { activeLines.add(it) }
                autoAddView(view)

                view.tryStartMarquee()
            }
        }

        updateViewsVisibility()
    }

    fun autoAddView(view: RichLyricLineView) {
        if (layoutTransition == null && isNotEmpty()) {
            setLayoutTransition(myLayoutTransition)
        }
        addView(view, reusableLayoutParams)
    }

    fun setStyle(config: RichLyricLineConfig): LyricPlayerView = apply {
        this.config = config
        forEach {
            if (it is RichLyricLineView) it.setStyle(config)
        }
    }

    fun getStyle(): RichLyricLineConfig = config

    fun fillGapAtStart(origin: Song): Song {
        val song = origin.deepCopy()
        val songTitle = getSongTitle(song) ?: return song
        val lyrics: MutableList<RichLyricLine> = song.lyrics?.toMutableList() ?: mutableListOf()

        if (lyrics.isEmpty()) {
            val duration = if (song.duration > 0) song.duration else Long.MAX_VALUE
            lyrics.add(
                createLyricTitleLine(
                    begin = 0,
                    end = duration,
                    duration = duration,
                    text = songTitle
                )
            )
        } else {
            val first = lyrics.first()
            if (first.begin > 0) {
                lyrics.add(
                    0,
                    createLyricTitleLine(
                        begin = 0,
                        end = first.begin,
                        duration = first.begin,
                        songTitle
                    )
                )
            }
        }

        song.lyrics = lyrics
        return song
    }

    @Suppress("SameParameterValue")
    private fun createLyricTitleLine(
        begin: Long,
        end: Long,
        duration: Long,
        text: String
    ) = RichLyricLine(
        begin = begin,
        end = end,
        duration = duration,
        text = text
    ).apply {
        metadata = lyricMetadataOf(KEY_SONG_TITLE_LINE to "true")
    }

    private fun getSongTitle(song: Song): String? {
        val hasName = song.name?.isNotBlank() ?: false
        val hasArtist = song.artist?.isNotBlank() ?: false

        return when {
            hasName && hasArtist -> "${song.name} - ${song.artist}"
            hasName -> song.name
            else -> null
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
    }

    private fun emptyTimingNavigator() = TimingNavigator<RichLyricLineModel>(emptyArray())

    private data class InterludeState(
        val start: Long,
        val end: Long
    )
}