/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.central.provider.player

import android.util.Log
import io.github.proify.lyricon.central.Constants
import io.github.proify.lyricon.central.provider.player.ActivePlayerDispatcher.syncNewProviderState
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.ProviderInfo
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write

/**
 * 活跃播放器调度器。
 *
 * 维护当前全局唯一的活跃播放器实例，并将播放器产生的事件分发给注册的 [ActivePlayerListener]。
 * 实现了抢占式焦点管理，确保只有当前活跃的播放器能向 UI 或其他组件发送更新。
 */
object ActivePlayerDispatcher : PlayerListener {

    private const val TAG = "ActivePlayerDispatcher"
    private val DEBUG = Constants.isDebug()

    /** 保护内部状态的读写锁 */
    private val lock = ReentrantReadWriteLock()

    /** 当前持有焦点的播放器提供者信息 */
    private var activeInfo: ProviderInfo? = null

    /** 当前活跃播放器是否处于播放状态 */
    private var activeIsPlaying: Boolean = false

    /** 线程安全的监听器集合 */
    private val listeners = CopyOnWriteArraySet<ActivePlayerListener>()

    /**
     * 添加活跃播放器状态监听器。
     *
     * @param listener 接收事件的回调接口
     */
    fun addActivePlayerListener(listener: ActivePlayerListener) {
        listeners += listener
    }

    /**
     * 移除活跃播放器状态监听器。
     *
     * @param listener 待移除的回调接口
     */
    @Suppress("unused")
    fun removeActivePlayerListener(listener: ActivePlayerListener) {
        listeners -= listener
    }

    /**
     * 将指定播放器标记为失效。
     *
     * 如果该播放器当前是活跃播放器，将清除活跃状态并通知监听器。
     *
     * @param provider 失效的播放器信息
     */
    fun notifyProviderInvalid(provider: ProviderInfo) {
        // 直接使用写锁，避免 "Check-Then-Act" 竞态条件
        val shouldNotify = lock.write {
            if (activeInfo == provider) {
                activeInfo = null
                activeIsPlaying = false
                true
            } else {
                false
            }
        }

        if (shouldNotify) {
            broadcast { it.onActiveProviderChanged(null) }
        }
    }

    // ---- PlayerListener 实现 ----

    override fun onSongChanged(recorder: PlayerRecorder, song: Song?) {
        if (DEBUG) Log.d(TAG, "onSongChanged: $song")
        // 歌曲切换事件特殊处理：如果是新激活的播放器，同步逻辑已包含歌曲信息，无需二次分发
        dispatchIfActive(recorder, allowDuplicateIfSwitching = false) {
            it.onSongChanged(song)
        }
    }

    override fun onPlaybackStateChanged(recorder: PlayerRecorder, isPlaying: Boolean) {
        if (DEBUG) Log.d(TAG, "onPlaybackStateChanged: $isPlaying")
        dispatchIfActive(recorder) {
            it.onPlaybackStateChanged(isPlaying)
        }
    }

    override fun onPositionChanged(recorder: PlayerRecorder, position: Long) {
        dispatchIfActive(recorder) {
            it.onPositionChanged(position)
        }
    }

    override fun onSeekTo(recorder: PlayerRecorder, position: Long) {
        dispatchIfActive(recorder) {
            it.onSeekTo(position)
        }
    }

    override fun onSendText(recorder: PlayerRecorder, text: String?) {
        // 文本发送事件特殊处理：切换时通常不立即重发旧文本，避免 UI 闪烁，除非在 sync 逻辑中明确处理
        dispatchIfActive(recorder, allowDuplicateIfSwitching = false) {
            it.onSendText(text)
        }
    }

    override fun onDisplayTranslationChanged(recorder: PlayerRecorder, isDisplayTranslation: Boolean) {
        dispatchIfActive(recorder, allowDuplicateIfSwitching = false) {
            it.onDisplayTranslationChanged(isDisplayTranslation)
        }
    }

    /**
     * 调度执行计划。
     * 用于将锁内的状态判断结果传递到锁外执行。
     */
    private class DispatchPlan(
        val isSwitched: Boolean = false,
        val shouldBroadcastOriginal: Boolean = false
    )

    /**
     * 核心调度逻辑。
     *
     * 判断事件来源是否拥有活跃权，并在必要时执行抢占切换。
     * 所有的外部回调均在锁外执行，保证线程安全。
     *
     * @param recorder 事件来源
     * @param allowDuplicateIfSwitching 当发生播放器切换时，是否仍分发原始事件。
     * 例如：歌曲切换事件在 [syncNewProviderState] 中已处理，通常设为 false。
     * @param notifier 实际的事件回调逻辑
     */
    private inline fun dispatchIfActive(
        recorder: PlayerRecorder,
        allowDuplicateIfSwitching: Boolean = true,
        crossinline notifier: (ActivePlayerListener) -> Unit
    ) {
        val recorderInfo = recorder.info
        val recorderPlaying = recorder.lastIsPlaying

        // 1. 在锁内计算状态变更和执行计划
        val plan = lock.write {
            val currentInfo = activeInfo

            if (currentInfo == recorderInfo) {
                // 场景：当前活跃播放器的常规更新
                activeIsPlaying = recorderPlaying
                DispatchPlan(isSwitched = false, shouldBroadcastOriginal = true)
            } else {
                // 场景：非活跃播放器尝试抢占
                // 抢占条件：当前无活跃播放器，或者当前活跃播放器已暂停且新播放器正在播放
                val canSwitch = currentInfo == null || (!activeIsPlaying && recorderPlaying)

                if (canSwitch) {
                    activeInfo = recorderInfo
                    activeIsPlaying = recorderPlaying
                    DispatchPlan(isSwitched = true, shouldBroadcastOriginal = allowDuplicateIfSwitching)
                } else {
                    // 忽略该事件
                    DispatchPlan()
                }
            }
        }

        // 2. 在锁外执行回调（避免死锁）
        if (plan.isSwitched) {
            // 先通知播放器变更
            broadcast { it.onActiveProviderChanged(recorderInfo) }
            // 再同步该播放器的全量状态
            syncNewProviderState(recorder)
        }

        if (plan.shouldBroadcastOriginal) {
            broadcast(notifier)
        }
    }

    /**
     * 同步新活跃播放器的状态快照给所有监听器。
     */
    private fun syncNewProviderState(recorder: PlayerRecorder) {
        broadcast { listener ->
            // 必须按逻辑顺序分发状态，模拟一次完整的初始化
            listener.onPlaybackStateChanged(activeIsPlaying)

            // 只有当存在文本时才分发，避免覆盖空状态
            recorder.lastText?.let { text ->
                listener.onSendText(text)
                // 如果有特定文本，通常不继续分发歌曲变更（视具体业务逻辑而定，此处保持原逻辑结构但做了微调）
                return@broadcast
            }

            listener.onSongChanged(recorder.lastSong)
            listener.onDisplayTranslationChanged(recorder.lastIsDisplayTranslation)
            listener.onPositionChanged(recorder.lastPosition)
        }
    }

    /**
     * 安全地向所有监听器广播事件。
     * 捕获并记录单个监听器的异常，防止影响其他监听器。
     */
    private inline fun broadcast(crossinline notifier: (ActivePlayerListener) -> Unit) {
        for (listener in listeners) {
            try {
                notifier(listener)
            } catch (e: Exception) {
                Log.e(TAG, "Dispatch failed for listener: ${listener.javaClass.name}", e)
            }
        }
    }
}