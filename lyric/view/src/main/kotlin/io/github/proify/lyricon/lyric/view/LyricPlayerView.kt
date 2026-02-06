/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lyricon.lyric.view

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import androidx.annotation.CallSuper
import androidx.core.view.contains
import androidx.core.view.forEach
import androidx.core.view.isNotEmpty
import androidx.core.view.isVisible
import io.github.proify.lyricon.lyric.model.LyricLine
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.lyric.model.extensions.TimingNavigator
import io.github.proify.lyricon.lyric.model.interfaces.IRichLyricLine
import io.github.proify.lyricon.lyric.model.lyricMetadataOf
import io.github.proify.lyricon.lyric.view.line.LyricLineView
import io.github.proify.lyricon.lyric.view.model.RichLyricLineModel
import io.github.proify.lyricon.lyric.view.util.LayoutTransitionX
import io.github.proify.lyricon.lyric.view.util.getChildAtOrNull
import io.github.proify.lyricon.lyric.view.util.visibilityIfChanged
import io.github.proify.lyricon.lyric.view.util.visibleIfChanged

open class LyricPlayerView(
    context: Context,
    attributes: AttributeSet? = null,
) : LinearLayout(context, attributes), UpdatableColor {

    companion object {
        internal const val KEY_SONG_TITLE_LINE: String = "TitleLine"
        private const val MIN_GAP_DURATION: Long = 8 * 1000
        private const val TAG = "LyricPlayerView"
    }

    private var textMode = false
    private var config = RichLyricLineConfig()

    var isDisplayTranslation = false
        private set
    var isDisplayRoma = false
        private set

    private var enableRelativeProgress = false
    private var enableRelativeProgressHighlight = false
    private var enteringInterludeMode = false

    var song: Song? = null
        set(value) {
            textMode = false
            if (value != null) {
                val curFirstLine = activeLines.firstOrNull()
                val isExitingPlaceholder =
                    curFirstLine.isTitleLine() && getSongTitle(value) == curFirstLine?.text

                if (!isExitingPlaceholder) {
                    reset()
                }

                val newSong = fillGapAtStart(value)
                var previous: RichLyricLineModel? = null
                lineModels = newSong.lyrics?.map {
                    RichLyricLineModel(it).apply {
                        this.previous = previous
                        previous?.next = this
                        previous = this
                    }
                }
                lyricNavigator = TimingNavigator(lineModels?.toTypedArray() ?: emptyArray())
            } else {
                reset()
                lineModels = null
                lyricNavigator = emptyTimingNavigator()
            }

            field = value
        }

    var text: String? = null
        set(value) {
            field = value
            if (!textMode) {
                reset(); textMode = true
            }
            if (value.isNullOrBlank()) {
                removeAllViews()
                return
            }

            if (!contains(recycleTextLineView)) {
                addView(recycleTextLineView, reusableLayoutParams)
                updateTextLineViewStyle(config)
            }
            recycleTextLineView.setLyric(LyricLine(text = value, end = Long.MAX_VALUE / 10))
            recycleTextLineView.post { recycleTextLineView.startMarquee() }
        }

    private var lineModels: List<RichLyricLineModel>? = null
    private val activeLines = mutableListOf<IRichLyricLine>()
    private val recycleTextLineView by lazy { LyricLineView(context) }
    private val reusableLayoutParams =
        LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

    private val tempViewsToRemove = mutableListOf<RichLyricLineView>()
    private val tempViewsToAdd = mutableListOf<IRichLyricLine>()
    private val tempFindActiveLines = mutableListOf<RichLyricLineModel>()

    private var lyricNavigator: TimingNavigator<RichLyricLineModel> = emptyTimingNavigator()
    private var interludeState: InterludeState? = null

    private var layoutTransitionX: LayoutTransitionX? = null

    /**
     * 由 [autoAddView] 方法在合适使设置
     */
    private fun updateLayoutTransitionX(config: String? = LayoutTransitionX.TRANSITION_CONFIG_SMOOTH) {
        layoutTransitionX = LayoutTransitionX(config)
        layoutTransition = null
    }

    private val mainLyricPlayListener = object : LyricPlayListener {
        override fun onPlayStarted(view: LyricLineView) = updateViewsVisibility()
        override fun onPlayEnded(view: LyricLineView) = updateViewsVisibility()
        override fun onPlayProgress(view: LyricLineView, total: Float, progress: Float) {}
    }

    private val secondaryLyricPlayListener = object : LyricPlayListener {
        override fun onPlayStarted(view: LyricLineView) {
            view.visibleIfChanged = true; updateViewsVisibility()
        }

        override fun onPlayEnded(view: LyricLineView) = updateViewsVisibility()
        override fun onPlayProgress(view: LyricLineView, total: Float, progress: Float) {}
    }

    init {
        orientation = VERTICAL
        updateLayoutTransitionX()
        gravity = Gravity.CENTER_VERTICAL
    }

    // --- 公开 API ---

    private var _transitionConfig: String? = null
    fun setTransitionConfig(config: String?) {
        if (_transitionConfig == config) return
        _transitionConfig = config
        updateLayoutTransitionX(config)

        forEach { if (it is RichLyricLineView) it.setTransitionConfig(config) }

        Log.d("LyricPlayerView", "setTransitionConfig: $config")
    }

    fun setStyle(config: RichLyricLineConfig) = apply {
        this.config = config
        updateTextLineViewStyle(config)
        forEach { if (it is RichLyricLineView) it.setStyle(config) }
        updateViewsVisibility()
    }

    fun getStyle() = config

    fun updateDisplayTranslation(
        displayTranslation: Boolean = isDisplayTranslation,
        displayRoma: Boolean = isDisplayRoma
    ) {
        isDisplayTranslation = displayTranslation
        isDisplayRoma = displayRoma
        forEach {
            if (it is RichLyricLineView) {
                it.displayTranslation = displayTranslation
                it.displayRoma = displayRoma
                it.notifyLineChanged()
            }
        }
    }

    fun seekTo(position: Long) = updatePosition(position, true)

    fun setPosition(position: Long) = updatePosition(position)

    fun reset() {
        removeAllViews()
        activeLines.clear()
        if (enteringInterludeMode) exitInterludeMode()
    }

    override fun removeAllViews() {
        layoutTransition = null // 移除时禁用动画防止闪烁
        super.removeAllViews()
    }

    override fun updateColor(primary: Int, background: Int, highlight: Int) {
        val needsUpdate = primary != config.primary.textColor ||
                highlight != config.syllable.highlightColor
        if (!needsUpdate) return

        config.apply {
            this.primary.textColor = primary
            secondary.textColor = primary
            syllable.highlightColor = highlight
            syllable.backgroundColor = background
        }
        forEach {
            if (it is UpdatableColor) it.updateColor(
                primary,
                background,
                highlight
            )
        }
    }

    // --- 核心更新逻辑 ---

    private fun updatePosition(position: Long, seekTo: Boolean = false) {
        if (textMode) return

        tempFindActiveLines.clear()
        lyricNavigator.forEachAtOrPrevious(position) { tempFindActiveLines.add(it) }

        val matches = tempFindActiveLines
        updateActiveViews(matches)

        forEach { view ->
            if (view is RichLyricLineView) {
                if (seekTo) view.seekTo(position) else view.setPosition(position)
            }
        }
        handleInterlude(position, matches)
    }

    private fun updateActiveViews(matches: List<IRichLyricLine>) {
        tempViewsToRemove.clear()
        tempViewsToAdd.clear()

        // 1. 识别需移除项
        for (i in 0 until childCount) {
            val view = getChildAtOrNull(i) as? RichLyricLineView ?: continue
            if (view.line !in matches) tempViewsToRemove.add(view)
        }

        // 2. 识别需添加项
        matches.forEach { if (it !in activeLines) tempViewsToAdd.add(it) }

        if (tempViewsToRemove.isEmpty() && tempViewsToAdd.isEmpty()) return

        // 3. 单行变化直接复用 View
        if (activeLines.size == 1 && tempViewsToRemove.size == 1 && tempViewsToAdd.size == 1) {
            val recycleView = getChildAtOrNull(0) as? RichLyricLineView
            val newLine = tempViewsToAdd[0]
            if (recycleView != null) {
                activeLines[0] = newLine
                recycleView.line = newLine
                recycleView.tryStartMarquee()
            }
        } else {
            tempViewsToRemove.forEach { removeView(it); activeLines.remove(it.line) }
            tempViewsToAdd.forEach { line ->
                activeLines.add(line)
                createDoubleLineView(line).also { autoAddView(it); it.tryStartMarquee() }
            }
        }
        updateViewsVisibility()
    }

    fun updateViewsVisibility() {
        val totalChildCount = childCount
        if (totalChildCount == 0) return

        val v0 = getChildAtOrNull(0) as? RichLyricLineView ?: return
        val v1 = getChildAtOrNull(1) as? RichLyricLineView // 可能为 null

        // --- Phase 1: 状态决策 ---

        // 判定条件：V0 的主歌词是否已播放完毕（意图进入下一句）
        val v0MainFinished = v0.main.isPlayFinished()
        // 判定条件：V0 是否有副歌词内容
        val v0HasSecContent = v0.secondary.lyric.let {
            it.text.isNotBlank() || it.words.isNotEmpty()
        }

        // 核心逻辑：是否进入"换行过渡模式"
        // 条件：V0主唱完 + V1存在 + (V0没副歌词 或者 我们决定牺牲副歌词来显示下一句)
        // 这里我们倾向于：只要V0唱完且V1存在，就进入过渡模式（符合你描述的"V0变小, V1变大"）
        val isTransitionMode = v0MainFinished && v1 != null

        // --- Phase 2: 样式与初步可见性配置 ---

        val pSize = config.primary.textSize
        val sSize = config.secondary.textSize

        // [配置 V0]
        if (isTransitionMode) {
            // 模式：换行过渡 (V0 Main 变小，V0 Sec 强制隐藏)
            v0.main.visibilityIfChanged = VISIBLE
            v0.main.setTextSize(sSize) // 变小
            v0.secondary.visibilityIfChanged = GONE // 腾出位置
        } else {
            // 模式：聚焦当前 (V0 Main 正常)
            v0.main.visibilityIfChanged = VISIBLE
            v0.main.setTextSize(pSize)

            v0.secondary.visibilityIfChanged =
                if (v0.alwaysShowSecondary
                    || (v0HasSecContent && v0.secondary.isPlayStarted())
                ) VISIBLE else GONE

            v0.secondary.setTextSize(sSize)
        }

        // [配置 V1]
        if (v1 != null) {
            if (isTransitionMode) {
                // 模式：换行过渡 (V1 Main 变大，作为新的焦点)
                v1.main.visibilityIfChanged = VISIBLE
                v1.main.setTextSize(pSize) // 变大
                v1.secondary.visibilityIfChanged = GONE // 刚开始唱，副歌词暂不显示或优先级最低
            } else {
                // 模式：预读 (V1 Main 小字)
                v1.main.visibilityIfChanged = VISIBLE
                v1.main.setTextSize(sSize)
                v1.secondary.visibilityIfChanged = GONE
            }
        }

        // --- Phase 3: 最终裁决 (The Final Check) ---
        // 强制执行"最多显示2个"的硬性规定，按优先级保留

        var slotsRemaining = 2

        forEach { view ->
            if (view is RichLyricLineView) {
                var mainVis = view.main.visibility
                var secVis = view.secondary.visibility

                // 检查 Main
                if (view.main.isVisible) {
                    if (slotsRemaining > 0) {
                        slotsRemaining--
                    } else {
                        mainVis = GONE
                    }
                }
                // 检查 Secondary
                // 注意：因为我们在 Phase 2 已经根据模式策略性地隐藏了 v0.secondary (在过渡时)，
                // 所以这里的循环主要是为了兜底。
                if (view.secondary.isVisible) {
                    if (slotsRemaining > 0) {
                        slotsRemaining--
                    } else {
                        secVis = GONE
                    }
                }

                // 如果容器内全被隐藏了，容器本身也隐藏
                val allGone = mainVis != VISIBLE && secVis != VISIBLE
                if (!allGone) {
                    view.main.visibilityIfChanged = mainVis
                    view.secondary.visibilityIfChanged = secVis
                }
                view.visibilityIfChanged = if (allGone) GONE else VISIBLE
            }
        }

        // --- Phase 4: 布局动画与缩放 (基于最终可见性) ---

        // 重新计算实际可见行数，用于缩放
        val finalVisibleLines = (2 - slotsRemaining) // 总槽位(2) - 剩余槽位 = 实际占用

        val targetScale = if (finalVisibleLines > 1) {
            config.scaleInMultiLine.coerceIn(0.1f, 2f)
        } else 1.0f

        val isMultiViewMode =
            totalChildCount > 1 && v1?.visibility == VISIBLE && targetScale != 1.0f

        for (i in 0 until totalChildCount) {
            val view = getChildAtOrNull(i) as? RichLyricLineView ?: continue

            // 应用缩放
            view.setRenderScale(targetScale)

            // 应用吸附位移
            if (isMultiViewMode && view.isVisible && view.height > 0) {
                val offset = (view.height * (1f - targetScale)) / 2f
                // i=0 向下吸附(+)，i=1 向上吸附(-)
                view.translationY = if (i == 0) offset else -offset
            } else {
                view.translationY = 0f
            }
        }

        postInvalidateOnAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateViewsVisibility()
    }

    private fun createDoubleLineView(line: IRichLyricLine) = RichLyricLineView(
        context,
        displayTranslation = isDisplayTranslation,
        displayRoma = isDisplayRoma,
        enableRelativeProgress = enableRelativeProgress,
        enableRelativeProgressHighlight = enableRelativeProgressHighlight,
    ).apply {
        this.line = line
        setStyle(config)
        setMainLyricPlayListener(mainLyricPlayListener)
        setSecondaryLyricPlayListener(secondaryLyricPlayListener)
        setTransitionConfig(_transitionConfig)
    }

    private fun autoAddView(view: RichLyricLineView) {
        if (layoutTransition == null && isNotEmpty()) layoutTransition = layoutTransitionX
        addView(view, reusableLayoutParams)
    }

    private fun updateTextLineViewStyle(config: RichLyricLineConfig) {
        recycleTextLineView.setStyle(
            LyricLineConfig(
                config.primary,
                config.marquee,
                config.syllable,
                config.gradientProgressStyle,
                config.fadingEdgeLength
            )
        )
    }

    // --- 间奏处理逻辑 ---

    private fun handleInterlude(position: Long, matches: List<RichLyricLineModel>) {
        val resolved = resolveInterludeState(position, matches)
        if (interludeState == resolved) return

        if (interludeState != null && resolved == null) {
            interludeState = null
            exitInterludeMode()
        } else if (resolved != null) {
            interludeState = resolved
            enteringInterludeMode(resolved.end - resolved.start)
        }
    }

    private fun resolveInterludeState(
        pos: Long,
        matches: List<RichLyricLineModel>
    ): InterludeState? {
        interludeState?.let { if (pos in (it.start + 1) until it.end) return it }

        if (matches.isEmpty()) return null
        val current = matches.last()
        val next = current.next ?: return null

        if (next.begin - current.end <= MIN_GAP_DURATION) return null
        if (pos <= current.end || pos >= next.begin) return null

        return InterludeState(current.end, next.begin)
    }

    @CallSuper
    protected open fun enteringInterludeMode(duration: Long) {
        enteringInterludeMode = true
    }

    @CallSuper
    protected open fun exitInterludeMode() {
        enteringInterludeMode = false
    }

    // --- 数据填充辅助 ---

    fun fillGapAtStart(origin: Song): Song {
        val song = origin.deepCopy()
        val title = getSongTitle(song) ?: return song
        val lyrics = song.lyrics?.toMutableList() ?: mutableListOf()

        if (lyrics.isEmpty()) {
            val d = if (song.duration > 0) song.duration else Long.MAX_VALUE
            lyrics.add(createLyricTitleLine(d, d, title))
        } else {
            val first = lyrics.first()
            if (first.begin > 0) lyrics.add(
                0,
                createLyricTitleLine(first.begin, first.begin, title)
            )
        }
        song.lyrics = lyrics
        return song
    }

    private fun createLyricTitleLine(end: Long, duration: Long, text: String) =
        RichLyricLine(end = end, duration = duration, text = text).apply {
            metadata = lyricMetadataOf(KEY_SONG_TITLE_LINE to "true")
        }

    private fun getSongTitle(song: Song) = when {
        !song.name.isNullOrBlank() && !song.artist.isNullOrBlank() -> "${song.name} - ${song.artist}"
        !song.name.isNullOrBlank() -> song.name
        else -> null
    }

    private fun emptyTimingNavigator() = TimingNavigator<RichLyricLineModel>(emptyArray())

    private data class InterludeState(val start: Long, val end: Long)

    private val myViewTreeObserver =
        ViewTreeObserver.OnGlobalLayoutListener {
            updateViewsVisibility()
        }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewTreeObserver.removeOnGlobalLayoutListener(myViewTreeObserver)
        reset()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnGlobalLayoutListener(myViewTreeObserver)
    }
}

fun IRichLyricLine?.isTitleLine(): Boolean =
    this?.metadata?.getBoolean(LyricPlayerView.KEY_SONG_TITLE_LINE, false) == true