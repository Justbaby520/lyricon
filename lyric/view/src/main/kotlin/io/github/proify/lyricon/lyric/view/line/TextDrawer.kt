/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.view.line

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Shader
import android.text.TextPaint
import androidx.core.graphics.withSave
import io.github.proify.lyricon.lyric.view.line.model.LyricModel
import kotlin.math.abs
import kotlin.math.max

internal class TextDrawer {
    private var bgColors = intArrayOf(Color.GRAY)
    private var hlColors = intArrayOf(Color.WHITE)

    val isRainbowBg get() = bgColors.size > 1
    val isRainbowHl get() = hlColors.size > 1

    private val fontMetrics = Paint.FontMetrics()
    private var baselineOffset = 0f

    private var cachedRainbowShader: LinearGradient? = null
    private var cachedAlphaMaskShader: LinearGradient? = null
    private var lastTotalWidth = -1f
    private var lastHighlightWidth = -1f
    private var lastColorsHash = 0

    fun setColors(background: IntArray, highlight: IntArray) {
        if (background.isNotEmpty()) bgColors = background
        if (highlight.isNotEmpty()) hlColors = highlight
    }

    fun updateMetrics(paint: TextPaint) {
        paint.getFontMetrics(fontMetrics)
        baselineOffset = -(fontMetrics.descent + fontMetrics.ascent) / 2f
    }

    fun clearShaderCache() {
        cachedRainbowShader = null
        cachedAlphaMaskShader = null
        lastTotalWidth = -1f
    }

    fun draw(
        canvas: Canvas,
        model: LyricModel,
        viewWidth: Int,
        viewHeight: Int,
        scrollX: Float,
        isOverflow: Boolean,
        highlightWidth: Float,
        useGradient: Boolean,
        scrollOnly: Boolean,
        bgPaint: TextPaint,
        hlPaint: TextPaint,
        normPaint: TextPaint
    ) {
        val y = (viewHeight / 2f) + baselineOffset
        canvas.withSave {
            val xOffset = when {
                isOverflow -> scrollX
                model.isAlignedRight -> viewWidth - model.width
                else -> 0f
            }
            translate(xOffset, 0f)

            if (scrollOnly) {
                canvas.drawText(model.wordText, 0f, y, normPaint)
                return@withSave
            }

            if (isRainbowBg) {
                bgPaint.shader = getOrCreateRainbowShader(model.width, bgColors)
            } else {
                bgPaint.shader = null
            }

            if (!useGradient) {
                canvas.withSave {
                    canvas.clipRect(highlightWidth, 0f, Float.MAX_VALUE, viewHeight.toFloat())
                    canvas.drawText(model.wordText, 0f, y, bgPaint)
                }
            } else {
                canvas.drawText(model.wordText, 0f, y, bgPaint)
            }

            if (highlightWidth > 0f) {
                canvas.withSave {
                    canvas.clipRect(0f, 0f, highlightWidth, viewHeight.toFloat())

                    val atEnd = highlightWidth >= model.width
                    if (useGradient && !atEnd) {
                        val baseShader = if (isRainbowHl) {
                            getOrCreateRainbowShader(model.width, hlColors)
                        } else {
                            LinearGradient(
                                0f, 0f, model.width, 0f,
                                hlPaint.color, hlPaint.color,
                                Shader.TileMode.CLAMP
                            )
                        }
                        val maskShader = getOrCreateAlphaMaskShader(model.width, highlightWidth)
                        hlPaint.shader = ComposeShader(baseShader, maskShader, PorterDuff.Mode.DST_IN)
                    } else {
                        if (isRainbowHl) {
                            hlPaint.shader = getOrCreateRainbowShader(model.width, hlColors)
                        } else {
                            hlPaint.shader = null
                        }
                    }
                    canvas.drawText(model.wordText, 0f, y, hlPaint)
                }
            }
        }
    }

    private fun getOrCreateRainbowShader(totalWidth: Float, colors: IntArray): Shader {
        val colorsHash = colors.contentHashCode()
        if (cachedRainbowShader == null || lastTotalWidth != totalWidth || lastColorsHash != colorsHash) {
            cachedRainbowShader = LinearGradient(
                0f, 0f, totalWidth, 0f,
                colors, null, Shader.TileMode.CLAMP
            )
            lastTotalWidth = totalWidth
            lastColorsHash = colorsHash
        }
        return cachedRainbowShader!!
    }

    private fun getOrCreateAlphaMaskShader(totalWidth: Float, highlightWidth: Float): Shader {
        val edgePosition = max(highlightWidth / totalWidth, 0.9f)
        if (cachedAlphaMaskShader == null || abs(lastHighlightWidth - highlightWidth) > 0.1f) {
            cachedAlphaMaskShader = LinearGradient(
                0f, 0f, highlightWidth, 0f,
                intArrayOf(Color.BLACK, Color.BLACK, Color.TRANSPARENT),
                floatArrayOf(0f, edgePosition, 1f),
                Shader.TileMode.CLAMP
            )
            lastHighlightWidth = highlightWidth
        }
        return cachedAlphaMaskShader!!
    }
}
