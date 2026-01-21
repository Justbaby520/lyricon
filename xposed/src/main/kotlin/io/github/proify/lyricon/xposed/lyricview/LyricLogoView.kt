/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.lyricview

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.LinearLayout.LayoutParams
import android.widget.TextView
import io.github.proify.android.extensions.dp
import io.github.proify.android.extensions.md5
import io.github.proify.android.extensions.toBitmap
import io.github.proify.android.extensions.visibilityIfChanged
import io.github.proify.lyricon.common.util.SVGHelper
import io.github.proify.lyricon.lyric.style.LogoStyle
import io.github.proify.lyricon.lyric.style.LyricStyle
import io.github.proify.lyricon.lyric.style.RectF
import io.github.proify.lyricon.lyric.view.util.Interpolates
import io.github.proify.lyricon.provider.ProviderLogo
import io.github.proify.lyricon.xposed.hook.systemui.LyricViewController
import io.github.proify.lyricon.xposed.util.NotificationCoverHelper
import io.github.proify.lyricon.xposed.util.StatusBarColorMonitor
import io.github.proify.lyricon.xposed.util.StatusColor
import java.io.File
import java.util.WeakHashMap
import kotlin.math.roundToInt

class LyricLogoView(context: Context) : ImageView(context),
    StatusBarColorMonitor.OnColorChangeListener,
    NotificationCoverHelper.OnCoverUpdateListener {

    var linkedTextView: TextView? = null

    private var strategy: ILogoStrategy? = null

    var providerLogo: ProviderLogo? = null
        set(value) {
            if (field !== value) {
                field = value
                // 当 provider 变化时，释放策略内缓存并重新评估
                (strategy as? ProviderStrategy)?.onProviderChanged()
                reassessStrategy()
            }
        }

    private var currentStatusColor: StatusColor = StatusColor(Color.BLACK, false)

    private var lyricStyle: LyricStyle? = null

    // --- 进度条相关 ---
    private var progress: Float = 0f // 0.0f ~ 1.0f
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        alpha = 255 * 1f.roundToInt()
    }
    private val progressRect = android.graphics.RectF()
    private var progressAnimator: ValueAnimator? = null

    companion object {
        private const val DEFAULT_ROTATION_DURATION_MS = 12_000L
        private const val TEXT_SIZE_MULTIPLIER = 1.2f
        private const val DEFAULT_TEXT_SIZE_DP = 14
        private const val SQUIRCLE_CORNER_RADIUS_DP = 4
        const val VIEW_TAG: String = "lyricon:logo_view"
    }

    init {
        this.tag = VIEW_TAG
        this.clipToOutline = true
    }

    fun clearProgress() {
        progressAnimator?.cancel()
        progressAnimator = null
        this.progress = 0f
        invalidate()
    }

    fun syncProgress(current: Long, duration: Long) {
        progressAnimator?.cancel()
        if (duration <= 0) return

        if (strategy !is CoverStrategy
            || (strategy as CoverStrategy).style != LogoStyle.STYLE_COVER_CIRCLE
        ) {
            return
        }

        val startProgress = current.toFloat() / duration
        this.progress = startProgress
        invalidate()

        if (current < duration) {
            progressAnimator = ValueAnimator.ofFloat(startProgress, 1f).apply {
                this.duration = duration - current
                interpolator = Interpolates.linear
                addUpdateListener { animator ->
                    progress = animator.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (strategy is CoverStrategy && progress > 0f && progress < 1f) {
            drawProgress(canvas)
        }
    }

    private fun drawProgress(canvas: Canvas) {
        val strokeWidth = 2.dp.toFloat()
        val padding = strokeWidth / 2

        progressPaint.strokeWidth = strokeWidth
        progressPaint.color = currentStatusColor.color

        progressRect.set(padding, padding, width - padding, height - padding)

        val currentStyle = lyricStyle?.packageStyle?.logo?.style

        if (currentStyle == LogoStyle.STYLE_COVER_CIRCLE) {
            canvas.drawArc(progressRect, -90f, 360f * progress, false, progressPaint)
        } else if (currentStyle == LogoStyle.STYLE_COVER_SQUIRCLE) {
            // 简化绘制为圆弧以保持统一外观
            canvas.drawArc(progressRect, -90f, 360f * progress, false, progressPaint)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        NotificationCoverHelper.registerListener(this)
        strategy?.onAttach()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        NotificationCoverHelper.unregisterListener(this)
        strategy?.onDetach()
    }

    override fun onColorChange(color: StatusColor) {
        currentStatusColor = color
        if (strategy?.isEffective == true) {
            strategy?.onColorUpdate()
        }
        invalidate()
    }

    override fun onCoverUpdated(packageName: String, coverFile: File) {
        if (packageName == LyricViewController.activePackage && strategy is CoverStrategy) {
            strategy?.updateContent()
        }
    }

    /**
     * 视图可见性变化：单独处理可见/隐藏事件（不同于 attach/detach）
     */
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        val visible = visibility == VISIBLE && isShown
        strategy?.onVisibilityChanged(visible)
    }

    /**
     * 当窗口可见性变化（例如系统锁屏、窗口隐藏）时也要通知策略
     */
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        val visible = visibility == VISIBLE && isShown
        strategy?.onVisibilityChanged(visible)
    }

    // region Public API

    fun applyStyle(style: LyricStyle) {
        this.lyricStyle = style
        val logoConfig = style.packageStyle.logo

        updateLayoutParams(style, logoConfig)
        reassessStrategy()
    }

    // region Internal Logic

    private fun reassessStrategy() {
        val logoConfig = lyricStyle?.packageStyle?.logo ?: return

        val newStrategy = when (logoConfig.style) {
            LogoStyle.STYLE_COVER_SQUIRCLE,
            LogoStyle.STYLE_COVER_CIRCLE -> CoverStrategy()

            LogoStyle.STYLE_PROVIDER_LOGO ->
                if (providerLogo == null) AppLogoStrategy() else ProviderStrategy()

            else -> AppLogoStrategy()
        }

        if (strategy?.javaClass != newStrategy.javaClass) {
            strategy?.onDetach()
            strategy = newStrategy
            newStrategy.render()
        } else {
            strategy?.render()
        }

        updateVisibilityState()
    }

    internal fun updateVisibilityState() {
        val logoConfig = lyricStyle?.packageStyle?.logo
        val isEnabled = logoConfig?.enable == true
        val isEffective = strategy?.isEffective == true

        this.visibilityIfChanged = if (isEnabled && isEffective) VISIBLE else GONE
    }

    private fun updateLayoutParams(style: LyricStyle, logoStyle: LogoStyle) {
        val defaultSize = calculateDefaultSize(style)
        val width = if (logoStyle.width <= 0) defaultSize else logoStyle.width.dp
        val height = if (logoStyle.height <= 0) defaultSize else logoStyle.height.dp

        val params = (layoutParams as? LayoutParams) ?: LayoutParams(width, height)
        params.width = width
        params.height = height
        applyMargins(params, logoStyle.margins)

        layoutParams = params
    }

    private fun applyMargins(params: LayoutParams, margins: RectF) {
        params.leftMargin = margins.left.dp
        params.topMargin = margins.top.dp
        params.rightMargin = margins.right.dp
        params.bottomMargin = margins.bottom.dp
    }

    private fun calculateDefaultSize(style: LyricStyle): Int {
        val configuredSize = style.packageStyle.text.textSize
        return when {
            configuredSize > 0 -> configuredSize.dp
            linkedTextView != null -> {
                (linkedTextView!!.textSize * TEXT_SIZE_MULTIPLIER).roundToInt()
            }

            else -> DEFAULT_TEXT_SIZE_DP.dp
        }
    }

    // region Strategies

    private interface ILogoStrategy {
        val isEffective: Boolean
        fun render()
        fun updateContent()
        fun onColorUpdate()
        fun onAttach()
        fun onDetach()

        /**
         * 可见性变化回调（true 表示可见），默认空实现。
         * 将可见性与 attach/detach 区分开有助于减少不必要的工作量（例如：在 detach 时已释放资源）。
         */
        fun onVisibilityChanged(visible: Boolean) {}
    }

    private inner class ProviderStrategy : ILogoStrategy {
        override var isEffective: Boolean = false
            private set

        // 缓存上一次生成的 bitmap（避免重复解析 SVG）
        private var cachedBitmap: Bitmap? = null
        private var lastProviderSignature: String? = null

        override fun render() {
            // provider 图标通常使用 tint
            outlineProvider = null

            val bitmap = loadProviderBitmap()
            setImageBitmap(bitmap)
            isEffective = bitmap != null

            onColorUpdate()
            updateVisibilityState()
        }

        override fun updateContent() {
            if (!isEffective) render() else onColorUpdate()
        }

        override fun onColorUpdate() {
            imageTintList = when {
                providerLogo?.colorful == true -> null
                else -> calculateTint()
            }
        }

        override fun onAttach() {
            // attach 时无需特殊处理；如果之前处于隐藏状态，render 会在可见时触发
        }

        override fun onDetach() {
            // 释放缓存引用，避免持有大对象
            cachedBitmap = null
            lastProviderSignature = null
            // 确保 image 源移除，帮助 GC
            setImageBitmap(null)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (!visible) {
                // 隐藏时停止任何进度动画（若存在）并降低占用
                progressAnimator?.cancel()
                progressAnimator = null
            } else {
                // 可见时尝试渲染（避免在不可见时做昂贵操作）
                if (!isEffective) render()
            }
        }

        fun onProviderChanged() {
            // provider 变更时强制刷新并清缓存
            cachedBitmap = null
            lastProviderSignature = null
        }

        lateinit var v: View

        private fun loadProviderBitmap(): Bitmap? {
            val logo = providerLogo ?: return null
            val signature = "${logo.hashCode()}_${width}_${height}_${logo.type}"
            if (signature == lastProviderSignature && cachedBitmap != null) {
                return cachedBitmap
            }

            val bmp = when (logo.type) {
                ProviderLogo.TYPE_BITMAP -> logo.toBitmap()
                ProviderLogo.TYPE_SVG -> {
                    val svgString = logo.toSvg()
                    if (svgString.isNullOrBlank()) null
                    else SVGHelper.create(svgString)?.createBitmap(width, height)
                }

                else -> null
            }

            // 更新缓存（仅保存对 bitmap 的引用，不主动 recycle，交给系统 GC）
            cachedBitmap = bmp
            lastProviderSignature = signature
            return bmp
        }

        private fun calculateTint(): ColorStateList {
            val logoStyle = lyricStyle?.packageStyle?.logo
                ?: return ColorStateList.valueOf(currentStatusColor.color)

            if (!logoStyle.enableCustomColor) {
                return ColorStateList.valueOf(currentStatusColor.color)
            }

            val logoColorConfig = logoStyle.color(currentStatusColor.lightMode)
            val finalColor = when {
                logoColorConfig.followTextColor -> resolveFollowTextColor()
                logoColorConfig.color != 0 -> logoColorConfig.color
                else -> currentStatusColor.color
            }

            return ColorStateList.valueOf(finalColor)
        }

        private fun resolveFollowTextColor(): Int {
            val textStyle = lyricStyle?.packageStyle?.text
            if (textStyle?.enableCustomTextColor != true) {
                return currentStatusColor.color
            }

            val textColorConfig = textStyle.color(currentStatusColor.lightMode)
            return if (textColorConfig != null && textColorConfig.normal != 0) {
                textColorConfig.normal
            } else {
                currentStatusColor.color
            }
        }
    }

    private inner class CoverStrategy : ILogoStrategy {
        private var rotationAnimator: ObjectAnimator? = null
        private var lastFileSignature: String? = null
        override var isEffective: Boolean = false
            private set

        var style: Int = 0

        override fun render() {
            imageTintList = null
            loadAndApplyCover()
        }

        override fun updateContent() {
            val coverFile = NotificationCoverHelper.getCoverFile(LyricViewController.activePackage)
            val currentSignature = coverFile.md5()
            if (currentSignature == lastFileSignature) return
            loadAndApplyCover()
        }

        override fun onColorUpdate() {
            // 封面模式通常不跟随颜色变化
        }

        override fun onAttach() {
            // 附着时检查动画（但仅在可见时启动）
            checkAnimationState()
        }

        override fun onDetach() {
            stopAnimation()
            // 释放 image 引用，避免长时间持有
            setImageBitmap(null)
            lastFileSignature = null
            isEffective = false

            outlineProvider = null
            clipToOutline = false
        }

        override fun onVisibilityChanged(visible: Boolean) {
            // 可见性变化控制动画
            if (visible) {
                checkAnimationState()
            } else {
                stopAnimation()
            }

            // 隐藏时取消进度动画
            if (!visible) {
                progressAnimator?.cancel()
                progressAnimator = null
                progress = 0f
            }
        }

        private fun loadAndApplyCover() {
            // 尽量避免在不可见/未附着时进行昂贵操作，但 render 时仍允许加载（render 通常在策略变更后调用）
            val coverFile = NotificationCoverHelper.getCoverFile(LyricViewController.activePackage)
            val bitmap: Bitmap? = coverFile.toBitmap(width, height)

            setImageBitmap(bitmap)

            lastFileSignature = coverFile.lastModified().toString()
            isEffective = bitmap != null

            val currentStyle = lyricStyle?.packageStyle?.logo?.style ?: LogoStyle.STYLE_COVER_CIRCLE
            this.style = currentStyle

            // 确保宽高已准备好再设置 outline，以免出现 0 宽高导致裁剪失效
            post {
                applyOutlineProvider(currentStyle)
            }

            checkAnimationState()
            updateVisibilityState()
        }

        private fun applyOutlineProvider(style: Int) {
            val provider = when (style) {
                LogoStyle.STYLE_COVER_CIRCLE -> object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setOval(0, 0, view.width, view.height)
                    }
                }

                LogoStyle.STYLE_COVER_SQUIRCLE -> object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(
                            0,
                            0,
                            view.width,
                            view.height,
                            SQUIRCLE_CORNER_RADIUS_DP.dp.toFloat()
                        )
                    }
                }

                else -> null
            }

            outlineProvider = provider
            clipToOutline = provider != null
        }

        private fun checkAnimationState() {
            // 动画仅在：已附着 && 视图实际显示 && 内容有效 时启动
            if (isAttachedToWindow
                && isShown
                && isEffective
                && style == LogoStyle.STYLE_COVER_CIRCLE
            ) {
                startAnimation()
            } else {
                stopAnimation()
            }
        }

        private fun startAnimation() {
            rotationAnimator?.let { if (it.isRunning) return }

            // 保证动画只在可见时运行
            rotationAnimator =
                ObjectAnimator.ofFloat(this@LyricLogoView, "rotation", 0f, 360f).apply {
                    duration = DEFAULT_ROTATION_DURATION_MS
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.RESTART
                    interpolator = LinearInterpolator()
                    start()
                }
        }

        private fun stopAnimation() {
            rotationAnimator?.cancel()
            rotationAnimator = null
            this@LyricLogoView.rotation = 0f
        }
    }

    private inner class AppLogoStrategy : ILogoStrategy {
        private val cacheIcons = WeakHashMap<String, Drawable>()

        override var isEffective: Boolean = false

        override fun render() {
            imageTintList = null
            val activePackage = LyricViewController.activePackage
            val icon = getIcon(activePackage)
            setImageDrawable(icon)
            isEffective = icon != null
        }

        private fun getIcon(packageName: String): Drawable? {
            if (packageName.isBlank()) {
                return null
            }
            cacheIcons[packageName]?.let { return it }
            val packageManager = context.packageManager
            val icon = packageManager.getApplicationIcon(packageName)
            cacheIcons[packageName] = icon
            return icon
        }

        override fun updateContent() {}

        override fun onColorUpdate() {
            imageTintList = null
        }

        override fun onAttach() {}

        override fun onDetach() {}
    }
}