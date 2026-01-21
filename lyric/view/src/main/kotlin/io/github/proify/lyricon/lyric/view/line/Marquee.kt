/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
@file:Suppress("MemberVisibilityCanBePrivate")

package io.github.proify.lyricon.lyric.view.line

import android.content.res.Resources
import android.graphics.Canvas
import androidx.core.graphics.withTranslation
import io.github.proify.lyricon.lyric.view.util.Interpolates
import io.github.proify.lyricon.lyric.view.util.dp
import java.lang.ref.WeakReference

class Marquee(private val viewRef: WeakReference<LyricLineView>) {
    companion object {
        private const val DEFAULT_SCROLL_SPEED_DP = 40f
        private val interpolator = Interpolates.linear
    }

    // --- 配置参数 ---
    var ghostSpacing: Float = 40f.dp

    var scrollSpeed: Float = calculateSpeed(DEFAULT_SCROLL_SPEED_DP)
        set(value) {
            field = calculateSpeed(value)
        }

    var initialDelayMs: Int = 400
    var loopDelayMs: Int = 800
    var repeatCount: Int = -1 // -1 为无限循环
    var stopAtEnd: Boolean = false

    // --- 内部运行状态 ---
    var currentRepeat: Int = 0
        private set

    var isRunning: Boolean = false
    var isPendingDelay: Boolean = false
    private var delayRemainingNanos = 0L

    /**
     * 当前滚动的进度位移 (0f . unit)
     * unit = lyricWidth + ghostSpacing
     */
    var currentUnitOffset: Float = 0f

    private fun calculateSpeed(dpPerSec: Float): Float {
        // 转换为 px/ms
        return (dpPerSec * Resources.getSystem().displayMetrics.density) / 1000f
    }

    fun start() {
        if (repeatCount == 0) return
        reset()
        scheduleDelay(initialDelayMs.toLong())
    }

    fun pause() {
        isRunning = false
        isPendingDelay = false
    }

    fun reset() {
        isRunning = false
        isPendingDelay = false
        currentRepeat = 0
        currentUnitOffset = 0f
        delayRemainingNanos = 0L
        updateViewOffset(0f, false)
    }

    private fun scheduleDelay(delayMs: Long) {
        if (delayMs <= 0L) {
            isRunning = true
            isPendingDelay = false
        } else {
            delayRemainingNanos = delayMs * 1_000_000L
            isPendingDelay = true
            isRunning = false
        }
    }

    /**
     * 每一帧调用的核心逻辑
     */
    fun step(deltaNanos: Long) {
        val view = viewRef.get() ?: return
        val lyricWidth = view.lyricWidth
        val viewWidth = view.width.toFloat()

        // 如果文本没超过视图宽度，不需要跑马灯
        if (lyricWidth <= viewWidth) {
            updateViewOffset(0f, true)
            return
        }

        // 处理延迟逻辑（初始延迟或循环间延迟）
        if (isPendingDelay) {
            delayRemainingNanos -= deltaNanos
            if (delayRemainingNanos <= 0) {
                isPendingDelay = false
                isRunning = true
            }
            return
        }

        if (!isRunning) return

        val unit = lyricWidth + ghostSpacing
        val deltaPx = scrollSpeed * (deltaNanos / 1_000_000f)
        currentUnitOffset += deltaPx

        // 检查是否完成了一个 Unit 周期
        if (currentUnitOffset >= unit) {
            currentUnitOffset -= unit
            currentRepeat++

            // 检查重复次数
            @Suppress("EmptyRange")
            if (repeatCount in 1..currentRepeat) {
                if (stopAtEnd) {
                    // 停在最后位置：即文本右侧与视图右侧对齐
                    val finalOffset = viewWidth - lyricWidth
                    updateViewOffset(finalOffset, true)
                    pause()
                    return
                } else {
                    reset()
                    return
                }
            } else {
                // 进入下一轮前的延迟
                scheduleDelay(loopDelayMs.toLong())
            }
        }

        // 应用插值器并更新 View
        val progress = (currentUnitOffset / unit).coerceIn(0f, 1f)
        val easedOffset = -interpolator.getInterpolation(progress) * unit
        updateViewOffset(easedOffset, false)
    }

    private fun updateViewOffset(offset: Float, finished: Boolean) {
        viewRef.get()?.let {
            it.scrollXOffset = offset
            it.isScrollFinished = finished
        }
    }

    /**
     * 干净的绘制逻辑：不修改数据，只根据 offset 做平移
     */
    fun draw(canvas: Canvas) {
        val view = viewRef.get() ?: return
        val model = view.lyricModel
        val paint = view.textPaint
        val text = model.text
        val lyricWidth = model.width
        val viewWidth = view.width.toFloat()
        val offset = view.scrollXOffset

        val baseline = ((view.height - (paint.descent() - paint.ascent())) / 2f) - paint.ascent()

        // 1. 绘制主体文本
        canvas.withTranslation(x = offset) {
            drawText(text, 0f, baseline, paint)
        }

        // 2. 只有在需要循环且没结束时，绘制“鬼影”副本
        // 判定条件：主体文本的右边界已经进入屏幕
        if (isRunning || (isPendingDelay && currentRepeat > 0)) {
            if (offset + lyricWidth < viewWidth) {
                val ghostOffset = offset + lyricWidth + ghostSpacing
                canvas.withTranslation(x = ghostOffset) {
                    drawText(text, 0f, baseline, paint)
                }
            }
        }
    }
}