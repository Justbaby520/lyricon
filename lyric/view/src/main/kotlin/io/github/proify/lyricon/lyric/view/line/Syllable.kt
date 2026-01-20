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

package io.github.proify.lyricon.lyric.view.line

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.text.TextPaint
import androidx.core.graphics.withSave
import io.github.proify.lyricon.lyric.view.Constances
import io.github.proify.lyricon.lyric.view.LyricPlayListener
import io.github.proify.lyricon.lyric.view.line.model.LyricModel
import io.github.proify.lyricon.lyric.view.line.model.WordModel
import io.github.proify.lyricon.lyric.view.util.Interpolates
import kotlin.math.max

/**
 * 歌词行渲染控制器
 */
class Syllable(private val ownerView: LyricLineView) {

    // --- 绘图资源 ---
    val inactivePaint: TextPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG)
    val activePaint: TextPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG)

    var enableGradient: Boolean = true
        set(value) {
            field = value
            renderer.enableGradient = value
        }

    var onlyScrollMode: Boolean = false
        set(value) {
            field = value
            renderer.onlyScrollMode = value
        }

    var playListener: LyricPlayListener? = null

    // --- 核心组件 ---
    private val karaokeAnimator = KaraokeAnimator()
    private val entranceEffect = EntranceEffect()
    private val autoScroller = AutoScroller()
    private val renderer = LineRenderer()

    // --- 缓存对象 (Zero Allocation) ---
    private val cachedLayoutInfo = LineRenderer.LayoutInfo()
    private val cachedPaintContext = LineRenderer.PaintContext(
        ownerView.textPaint, inactivePaint, activePaint
    )

    // 状态追踪
    private var lastPlayPosition: Long = Long.MIN_VALUE

    @Suppress("unused")
    fun isPlayStarted(): Boolean = karaokeAnimator.hasStarted

    @Suppress("unused")
    fun isPlaying(): Boolean = karaokeAnimator.isPlaying

    fun isPlayFinished(): Boolean = karaokeAnimator.hasFinished

    /**
     * 重置所有状态
     */
    fun reset() {
        karaokeAnimator.reset()
        entranceEffect.reset(ownerView.lyricModel)
        autoScroller.reset(ownerView)
        lastPlayPosition = Long.MIN_VALUE
    }

    /**
     * 【新增】跳转到指定位置 (Seek)
     * 区别于 updateProgress：
     * 1. 立即停止所有动画
     * 2. 强制将 UI 状态“瞬移”到目标时间点
     * 3. 忽略平滑插值
     */
    fun seek(position: Long) {
        val model = ownerView.lyricModel
        val currentWord = model.wordTimingNavigator.first(position)

        // 1. 计算理论上的目标状态
        val targetWidth = calculateTargetWidth(position, model, currentWord)

        // 2. 强制重置/瞬移各个组件
        // 动画器：直接跳到目标值，不产生过渡
        karaokeAnimator.jumpTo(targetWidth)

        // 进场特效：根据 Seek 位置，瞬间决定每个字是“已落下”还是“待落下”
        entranceEffect.fastForward(targetWidth, model.words)

        // 滚动器：直接定位到目标滚动位置
        autoScroller.update(targetWidth, ownerView, smooth = false)

        // 3. 更新状态记录
        lastPlayPosition = position

        // 4. 立即分发一次进度，确保 UI 刷新
        dispatchProgressEvents()
    }

    /**
     * 更新播放进度 (Normal Playback)
     * 行为：
     * 1. 计算差值，进行平滑动画插值
     * 2. 触发逐字下落动画
     */
    fun updateProgress(position: Long) {
        // 安全检测：如果时间倒流（如用户拖动进度条但未调用 seek，或循环播放），自动触发 seek 逻辑
        if (lastPlayPosition != Long.MIN_VALUE && position < lastPlayPosition) {
            seek(position)
            return
        }

        val model = ownerView.lyricModel
        val currentWord = model.wordTimingNavigator.first(position)
        val targetWidth = calculateTargetWidth(position, model, currentWord)

        // 首次进入校准：如果是这一行的刚开始，强制对齐上一个词的结束位置，防止高亮条从 0 飞过来
        if (currentWord != null && karaokeAnimator.currentWidth == 0f) {
            currentWord.previous?.let {
                karaokeAnimator.jumpTo(it.endPosition)
            }
        }

        // 驱动平滑动画
        if (targetWidth != karaokeAnimator.targetWidth) {
            val duration = currentWord?.duration ?: 0
            karaokeAnimator.animateTo(targetWidth, duration)
        }

        lastPlayPosition = position
    }

    /**
     * 帧更新驱动 (由 onDraw 调用)
     */
    fun updateFrame(frameTimeNanos: Long): Boolean {
        // 更新高亮动画
        val isHighlightChanged = karaokeAnimator.update(frameTimeNanos)

        if (isHighlightChanged) {
            val currentWidth = karaokeAnimator.currentWidth
            // 正常播放时，触发下落动画和滚动
            entranceEffect.checkTriggers(currentWidth, ownerView.lyricModel.words, frameTimeNanos)
            autoScroller.update(currentWidth, ownerView, smooth = true)
            dispatchProgressEvents()
        }

        // 更新下落动画
        val isEntranceChanged = entranceEffect.update(frameTimeNanos, ownerView.lyricModel.words)

        return isHighlightChanged || isEntranceChanged
    }

    fun draw(canvas: Canvas) {
        cachedLayoutInfo.update(
            viewWidth = ownerView.measuredWidth,
            viewHeight = ownerView.measuredHeight,
            scrollX = ownerView.scrollXOffset,
            isOverflow = ownerView.isOverflow()
        )

        renderer.draw(
            canvas = canvas,
            model = ownerView.lyricModel,
            layoutInfo = cachedLayoutInfo,
            paints = cachedPaintContext,
            highlightWidth = karaokeAnimator.currentWidth
        )
    }

    // --- Private Helper ---

    private fun calculateTargetWidth(
        position: Long,
        model: LyricModel,
        currentWord: WordModel?
    ): Float {
        return when {
            currentWord != null -> currentWord.endPosition
            position >= model.end -> ownerView.lyricWidth
            position <= model.begin -> 0f
            else -> karaokeAnimator.currentWidth
        }
    }

    private fun dispatchProgressEvents() {
        val current = karaokeAnimator.currentWidth
        val total = ownerView.lyricWidth

        if (!karaokeAnimator.hasStarted && current > 0f) {
            karaokeAnimator.hasStarted = true
            playListener?.onPlayStarted(ownerView)
        }
        if (!karaokeAnimator.hasFinished && current >= total) {
            karaokeAnimator.hasFinished = true
            playListener?.onPlayEnded(ownerView)
        }
        playListener?.onPlayProgress(ownerView, total, current)
    }

    // --- 内部组件 ---

    /**
     * 卡拉OK动画
     */
    private class KaraokeAnimator {
        private val interpolator = Interpolates.linear
        var currentWidth = 0f; private set
        var targetWidth = 0f; private set
        var hasStarted = false
        var hasFinished = false
        val isPlaying get() = hasStarted && !hasFinished

        private var startWidth = 0f
        private var startTimeNanos = 0L
        private var durationNanos = 0L
        private var isAnimating = false

        fun reset() {
            currentWidth = 0f; targetWidth = 0f; startWidth = 0f
            isAnimating = false; hasStarted = false; hasFinished = false
        }

        fun jumpTo(width: Float) {
            currentWidth = width; targetWidth = width; startWidth = width
            isAnimating = false
            // Seek 时不应重置 finished 状态，需根据宽度重新判断，这里交由 dispatchProgressEvents 处理
        }

        fun animateTo(target: Float, durationMs: Long) {
            startWidth = currentWidth
            targetWidth = target
            startTimeNanos = System.nanoTime()
            durationNanos = max(1L, durationMs) * 1_000_000L
            isAnimating = true
        }

        fun update(now: Long): Boolean {
            if (!isAnimating) return false
            val elapsed = (now - startTimeNanos).coerceAtLeast(0L)
            if (elapsed >= durationNanos) {
                currentWidth = targetWidth
                isAnimating = false
                return true
            }
            val progress = elapsed.toFloat() / durationNanos
            val eased = interpolator.getInterpolation(progress)
            currentWidth = startWidth + (targetWidth - startWidth) * eased
            return true
        }
    }

    /**
     * 词首字符下落动画
     */
    private class EntranceEffect(private val interpolator: (Float) -> Float = Interpolates.linear::getInterpolation) {
        private val durationNanos = Constances.WORD_DROP_ANIMATION_DURATION * 1_000_000L
        private val activeAnims = mutableListOf<AnimState>()
        private var nextTriggerIndex = 0

        fun reset(model: LyricModel) {
            activeAnims.clear()
            nextTriggerIndex = 0
            model.words.forEach { it.resetOffset() }
        }

        /**
         * Seek 时快速快进
         */
        fun fastForward(highlightWidth: Float, words: List<WordModel>) {
            activeAnims.clear()
            var boundaryIndex = 0
            for (i in words.indices) {
                val word = words[i]
                if (highlightWidth >= word.endPosition) {
                    word.charOffsetY = 0f
                    boundaryIndex = i + 1
                } else {
                    word.resetOffset()
                }
            }
            nextTriggerIndex = boundaryIndex
        }

        /**
         * 检测是否触发新的下落动画
         */
        fun checkTriggers(highlightWidth: Float, words: List<WordModel>, now: Long) {
            while (nextTriggerIndex < words.size) {
                val word = words[nextTriggerIndex]
                if (highlightWidth <= word.startPosition) break
                activeAnims.add(AnimState(nextTriggerIndex, now, word.charOffsetY))
                nextTriggerIndex++
            }
        }

        /**
         * 更新动画，每帧调用
         */
        fun update(now: Long, words: List<WordModel>): Boolean {
            if (activeAnims.isEmpty()) return false
            val iterator = activeAnims.iterator()
            while (iterator.hasNext()) {
                val anim = iterator.next()
                val progress = ((now - anim.start).toFloat() / durationNanos).coerceIn(0f, 1f)
                val eased = interpolator(progress)
                val word = words.getOrNull(anim.idx) ?: continue
                word.charOffsetY = anim.startOffset * (1f - eased)
                if (progress >= 1f) iterator.remove()
            }
            return true
        }

        private data class AnimState(
            val idx: Int,
            val start: Long,
            val startOffset: Float
        )
    }

    /**
     * 歌词自动滚动
     */
    private class AutoScroller {
        fun reset(view: LyricLineView) {
            view.scrollXOffset = 0f; view.isScrollFinished = false
        }

        // 增加 smooth 参数区分 Seek 和 Update
        fun update(currentX: Float, view: LyricLineView, smooth: Boolean) {
            if (!view.isOverflow()) {
                view.scrollXOffset = 0f
                return
            }

            val halfWidth = view.measuredWidth / 2f
            if (currentX > halfWidth) {
                val minScroll = -view.lyricWidth + view.measuredWidth
                val targetScroll = max(halfWidth - currentX, minScroll)

                // 这里如果需要实现平滑滚动效果，可以在 smooth=true 时引入 Scroller 或插值
                // 但对于歌词随时间轴滚动，直接映射通常效果最好（最跟手）
                view.scrollXOffset = targetScroll
                view.isScrollFinished = view.scrollXOffset <= minScroll
            } else {
                view.scrollXOffset = 0f
            }
        }
    }

    /**
     * 文字渲染
     */
    private class LineRenderer {

        class LayoutInfo {
            var viewWidth = 0
            var viewHeight = 0
            var scrollX = 0f
            var isOverflow = false

            fun update(viewWidth: Int, viewHeight: Int, scrollX: Float, isOverflow: Boolean) {
                this.viewWidth = viewWidth
                this.viewHeight = viewHeight
                this.scrollX = scrollX
                this.isOverflow = isOverflow
            }
        }

        data class PaintContext(
            val normal: TextPaint,
            val inactive: TextPaint,
            val active: TextPaint
        )

        // --- Shader 缓存 ---
        private val gradColors = intArrayOf(0, 0, Color.TRANSPARENT)
        private val gradPositions = floatArrayOf(0f, 0.86f, 1f)
        private var cachedShader: LinearGradient? = null
        var enableGradient = true

        var onlyScrollMode: Boolean = false

        fun draw(
            canvas: Canvas,
            model: LyricModel,
            layoutInfo: LayoutInfo,
            paints: PaintContext,
            highlightWidth: Float
        ) {
            val baseline = calculateBaseline(layoutInfo.viewHeight, paints.inactive)

            canvas.withSave {
                val tx = when {
                    layoutInfo.isOverflow -> layoutInfo.scrollX
                    model.isAlignedRight -> -model.width + layoutInfo.viewWidth
                    else -> 0f
                }
                translate(tx, 0f)

                if (!onlyScrollMode) {
                    // 先绘制非高亮文字
                    drawText(canvas, model, baseline, paints.inactive)

                    // 绘制高亮部分
                    if (highlightWidth > 0f) {
                        clipRect(0f, 0f, highlightWidth, layoutInfo.viewHeight.toFloat())
                        if (enableGradient) {
                            applyGradient(
                                paints.active,
                                highlightWidth,
                                model.width
                            )
                        } else {
                            paints.active.shader = null
                        }
                        drawText(canvas, model, baseline, paints.active)
                    }
                } else {
                    drawText(canvas, model, baseline, paints.normal)
                }
            }
        }

        // --- 内部函数 ---

        private fun calculateBaseline(h: Int, paint: Paint): Float {
            val fm = paint.fontMetrics
            return (h - (fm.descent - fm.ascent)) / 2f - fm.ascent
        }

        private var lastHighlightWidth = -1f
        private fun applyGradient(paint: Paint, highlightWidth: Float, tw: Float) {
            if (tw <= 0f) {
                paint.shader = null
                return
            }

            val ratio = highlightWidth / tw
            val pos1 = ratio.coerceAtLeast(0.90f)
            val color = paint.color

            val needUpdate = cachedShader == null
                    || lastHighlightWidth != highlightWidth
                    || gradColors[0] != color
                    || gradPositions[1] != pos1

            if (needUpdate) {
                gradColors[0] = color
                gradColors[1] = color
                gradPositions[1] = pos1
                lastHighlightWidth = highlightWidth

                cachedShader = LinearGradient(
                    0f,
                    0f,
                    highlightWidth,
                    0f,
                    gradColors,
                    gradPositions,
                    Shader.TileMode.CLAMP
                )
            }

            paint.shader = cachedShader
        }

        private fun drawText(canvas: Canvas, model: LyricModel, baseline: Float, paint: Paint) {
            if (onlyScrollMode || model.isDisabledOffsetAnimation) {
                canvas.drawText(model.wordText, 0f, baseline, paint)
                return
            }

            var curX = 0f
            for (word in model.words) {
                canvas.drawText(
                    word.text,
                    curX,
                    if (word.charOffsetMode) baseline else baseline + word.charOffsetY,
                    paint
                )
                curX += word.textWidth
            }
        }
    }
}