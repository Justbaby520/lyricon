package io.github.proify.lyricon.lyric.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewParent
import android.view.animation.DecelerateInterpolator
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.sign

/**
 * 智能共存手势识别器
 * 支持与系统手势共存，根据手势类型和触摸区域智能决策
 */
class ViewGesture(
    view: View,
    initialConfig: GestureConfig = GestureConfig()
) : View.OnTouchListener {

    data class GestureConfig(
        val enableHorizontal: Boolean = true,
        val maxOffset: Float = 100f,
        val triggerDistance: Float = 90f,
        val dampingFactor: Float = 0.55f,
        val backDuration: Long = 220L,
        val longPressTimeout: Long = ViewConfiguration.getLongPressTimeout().toLong(),
        val hapticEnabled: Boolean = true,
        val hapticConstant: Int = HapticFeedbackConstants.VIRTUAL_KEY,

        // 智能共存相关配置
        val mode: CoexistMode = CoexistMode.SMART,
        val deferThresholdMs: Long = 16L,  // 延迟处理阈值（一帧）
        val systemPriorityTop: Float = 0.3f,  // 系统优先区域顶部比例
        val horizontalPriority: Boolean = true,  // 水平滑动是否优先处理
        val tapAsync: Boolean = true,  // 点击是否异步处理
        val enableEventCloning: Boolean = true  // 是否启用事件克隆
    )

    enum class CoexistMode {
        PARALLEL,      // 完全并行：克隆事件异步处理
        DEFER,         // 延迟处理：等待系统先处理
        SMART          // 智能判断：根据手势类型和区域决策
    }

    enum class Direction { LEFT, RIGHT }

    enum class GestureType {
        HORIZONTAL_SWIPE,
        VERTICAL_SWIPE,
        TAP,
        UNKNOWN
    }

    interface OnGestureListener {
        fun onClick() {}
        fun onLongPress() {}
        fun onSwipe(direction: Direction, triggered: Boolean) {}
    }

    var listener: OnGestureListener? = null

    @Volatile
    var config: GestureConfig = initialConfig
        private set

    @Volatile
    var enabled: Boolean = true
        private set

    private val viewRef = WeakReference(view)
    private val context = view.context
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val handler = Handler(Looper.getMainLooper())
    private val statusBarHeight: Int

    // 触摸状态
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var lockedDirection: Direction? = null
    private var offset = 0f
    private var longPressFired = false
    private var longPressRunnable: Runnable? = null
    private var animator: ValueAnimator? = null

    // 共存相关状态
    private var pendingEvents = mutableListOf<MotionEvent>()
    private var currentGestureType = GestureType.UNKNOWN
    private var processedDown = false
    private var eventSequenceId = 0

    init {
        statusBarHeight = getStatusBarHeight()
    }

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) {
            cancelCurrentGesture()
        }
    }

    fun updateConfig(newConfig: GestureConfig) {
        config = newConfig
    }

    fun apply() {
        val view = viewRef.get() ?: return
        view.setOnTouchListener(this)
    }

    fun cancel() {
        val view = viewRef.get()
        view?.setOnTouchListener(null)
        cancelCurrentGesture()
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        if (!enabled) return false

        return when (config.mode) {
            CoexistMode.PARALLEL -> handleParallel(v, event)
            CoexistMode.DEFER -> handleDefer(v, event)
            CoexistMode.SMART -> handleSmart(v, event)
        }
    }

    // 模式1：完全并行处理
    private fun handleParallel(v: View, event: MotionEvent): Boolean {
        // 克隆事件并异步处理
        if (config.enableEventCloning) {
            val clone = MotionEvent.obtain(event)
            handler.post {
                processGestureEvent(v, clone)
                clone.recycle()
            }
        } else {
            // 直接异步处理（注意：event 在 post 后可能被系统回收）
            handler.post { processGestureEvent(v, event) }
        }

        // 始终返回 false，让系统继续处理
        return false
    }

    // 模式2：延迟处理
    private fun handleDefer(v: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // 保存 DOWN 事件
                val clone = MotionEvent.obtain(event)
                pendingEvents.clear()
                pendingEvents.add(clone)

                // 延迟处理整个事件序列
                v.postDelayed({
                    processPendingEvents(v)
                }, config.deferThresholdMs)
            }

            MotionEvent.ACTION_MOVE,
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                // 保存后续事件
                if (pendingEvents.isNotEmpty()) {
                    val clone = MotionEvent.obtain(event)
                    pendingEvents.add(clone)
                }
            }
        }

        // 让系统先处理
        return false
    }

    // 模式3：智能判断（推荐）
    private fun handleSmart(v: View, event: MotionEvent): Boolean {
        val parent = v.parent as? ViewParent

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // 判断是否在系统优先区域
                if (isSystemPriorityZone(event)) {
                    // 系统优先区域：让系统完全处理
                    parent?.requestDisallowInterceptTouchEvent(false)
                    processedDown = false
                    return false
                }

                // 保存 DOWN 事件
                downX = event.rawX
                downY = event.rawY
                processedDown = true

                // 启动长按定时器
                startLongPressTimer(v)

                // 允许父视图拦截（待方向判断）
                parent?.requestDisallowInterceptTouchEvent(false)

                // 重置状态
                currentGestureType = GestureType.UNKNOWN
                dragging = false
                longPressFired = false

                // 返回 true 以便接收 MOVE/UP 事件
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!processedDown) return false

                val dx = event.rawX - downX
                val dy = event.rawY - downY

                // 判断手势类型（如果还未确定）
                if (currentGestureType == GestureType.UNKNOWN &&
                    (abs(dx) > touchSlop || abs(dy) > touchSlop)
                ) {
                    currentGestureType = when {
                        abs(dx) > abs(dy) -> GestureType.HORIZONTAL_SWIPE
                        abs(dy) > abs(dx) -> GestureType.VERTICAL_SWIPE
                        else -> GestureType.TAP
                    }
                }

                // 根据手势类型和配置决策
                return when (currentGestureType) {
                    GestureType.HORIZONTAL_SWIPE -> {
                        if (config.horizontalPriority) {
                            // 水平滑动优先处理
                            handleHorizontalMove(v, event, dx, parent)
                        } else {
                            // 水平滑动也交给系统
                            false
                        }
                    }

                    GestureType.VERTICAL_SWIPE -> {
                        // 垂直滑动让给系统
                        parent?.requestDisallowInterceptTouchEvent(false)
                        false
                    }

                    else -> {
                        // 未知手势，继续监听
                        true
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!processedDown) return false

                val result = when (currentGestureType) {
                    GestureType.HORIZONTAL_SWIPE -> handleHorizontalUp(v, event)
                    GestureType.TAP -> {
                        if (config.tapAsync) {
                            // 点击异步处理
                            handler.post { handleClick(v) }
                            false
                        } else {
                            handleClick(v)
                            true
                        }
                    }

                    else -> {
                        // 其他情况，不消费
                        false
                    }
                }

                // 重置状态
                processedDown = false
                currentGestureType = GestureType.UNKNOWN
                cancelLongPressTimer()

                return result
            }
        }

        return false
    }

    // 水平滑动处理
    private fun handleHorizontalMove(
        v: View,
        event: MotionEvent,
        dx: Float,
        parent: ViewParent?
    ): Boolean {
        if (!config.enableHorizontal) return false

        if (!dragging && abs(dx) > touchSlop) {
            dragging = true
            lockedDirection = if (dx > 0) Direction.RIGHT else Direction.LEFT
            parent?.requestDisallowInterceptTouchEvent(true)
            cancelLongPressTimer()

            if (config.hapticEnabled) {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
        }

        if (dragging && lockedDirection != null) {
            offset = damp(dx)
            v.translationX = offset

            // 阈值反馈
            if (config.hapticEnabled && abs(offset) > config.triggerDistance * 0.8f &&
                abs(offset) < config.triggerDistance * 1.2f
            ) {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }

            return true
        }

        return false
    }

    private fun handleHorizontalUp(v: View, event: MotionEvent): Boolean {
        if (dragging && lockedDirection != null) {
            val triggered = abs(offset) > config.triggerDistance
            listener?.onSwipe(lockedDirection!!, triggered)

            if (triggered && config.hapticEnabled) {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }

            animateBack(v)
            dragging = false
            return true
        }
        return false
    }

    private fun handleClick(v: View): Boolean {
        if (!longPressFired) {
            listener?.onClick()
            if (config.hapticEnabled) {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
            return true
        }
        return false
    }

    // 判断是否在系统优先区域
    private fun isSystemPriorityZone(event: MotionEvent): Boolean {
        val y = event.rawY
        return y < statusBarHeight * config.systemPriorityTop
    }

    // 处理挂起的事件序列
    private fun processPendingEvents(v: View) {
        if (pendingEvents.isEmpty()) return

        // 模拟事件序列处理
        val events = pendingEvents.toList()
        pendingEvents.clear()

        events.forEachIndexed { index, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    // 重置状态
                    downX = event.rawX
                    downY = event.rawY
                    dragging = false
                    longPressFired = false
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    if (config.enableHorizontal && abs(dx) > touchSlop && !dragging) {
                        dragging = true
                        lockedDirection = if (dx > 0) Direction.RIGHT else Direction.LEFT

                        if (config.hapticEnabled) {
                            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        }
                    }

                    if (dragging && lockedDirection != null) {
                        offset = damp(dx)
                        v.translationX = offset
                    }
                }

                MotionEvent.ACTION_UP -> {
                    if (dragging && lockedDirection != null) {
                        val triggered = abs(offset) > config.triggerDistance
                        listener?.onSwipe(lockedDirection!!, triggered)
                        animateBack(v)
                    } else if (events.size == 1) {
                        // 只有 DOWN 和 UP，可能是点击
                        listener?.onClick()
                    }
                }
            }
            event.recycle()
        }
    }

    // 处理手势事件（用于异步处理）
    private fun processGestureEvent(v: View, event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                dragging = false
                longPressFired = false

                // 启动长按定时器
                startLongPressTimer(v)
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX

                if (config.enableHorizontal && abs(dx) > touchSlop && !dragging) {
                    dragging = true
                    lockedDirection = if (dx > 0) Direction.RIGHT else Direction.LEFT
                    cancelLongPressTimer()

                    if (config.hapticEnabled) {
                        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                }

                if (dragging && lockedDirection != null) {
                    offset = damp(dx)
                    v.translationX = offset
                }
            }

            MotionEvent.ACTION_UP -> {
                if (dragging && lockedDirection != null) {
                    val triggered = abs(offset) > config.triggerDistance
                    listener?.onSwipe(lockedDirection!!, triggered)
                    animateBack(v)
                } else if (!longPressFired) {
                    listener?.onClick()
                }
                cancelLongPressTimer()
            }

            MotionEvent.ACTION_CANCEL -> {
                cancelLongPressTimer()
            }
        }
    }

    // 辅助方法
    private fun startLongPressTimer(v: View) {
        cancelLongPressTimer()
        longPressRunnable = Runnable {
            if (!dragging) {
                longPressFired = true
                listener?.onLongPress()
                if (config.hapticEnabled) {
                    v.performHapticFeedback(config.hapticConstant)
                }
            }
        }.also { handler.postDelayed(it, config.longPressTimeout) }
    }

    private fun cancelLongPressTimer() {
        longPressRunnable?.let { handler.removeCallbacks(it) }
        longPressRunnable = null
    }

    private fun damp(distance: Float): Float {
        val sign = sign(distance)
        val absDist = abs(distance)
        val max = config.maxOffset.coerceAtLeast(0.1f)
        val ratio = (absDist / max).coerceAtMost(1f)
        return max * (1f - 1f / (ratio * config.dampingFactor + 1f)) * sign
    }

    private fun animateBack(v: View) {
        val start = offset
        stopAnimation()

        ValueAnimator.ofFloat(1f, 0f).apply {
            duration = config.backDuration
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val fraction = it.animatedValue as Float
                v.translationX = start * fraction
            }
            doOnEnd {
                v.translationX = 0f
                animator = null
            }
            start()
            animator = this
        }

        offset = 0f
        lockedDirection = null
    }

    private fun stopAnimation() {
        animator?.cancel()
        animator = null
    }

    private fun cancelCurrentGesture() {
        stopAnimation()
        val view = viewRef.get()
        view?.translationX = 0f
        offset = 0f
        lockedDirection = null
        dragging = false
        longPressFired = false
        cancelLongPressTimer()
        pendingEvents.forEach { it.recycle() }
        pendingEvents.clear()
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = context.resources.getIdentifier(
            "status_bar_height", "dimen", "android"
        )
        return if (resourceId > 0) {
            context.resources.getDimensionPixelSize(resourceId)
        } else {
            0
        }
    }

    private fun ValueAnimator.doOnEnd(action: () -> Unit) {
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                action()
            }
        })
    }
}