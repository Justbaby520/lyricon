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

package io.github.proify.lyricon.central.provider.player

import android.annotation.SuppressLint
import android.os.SharedMemory
import android.system.Os
import android.system.OsConstants
import android.util.Log
import io.github.proify.lyricon.central.Constants
import io.github.proify.lyricon.central.inflate
import io.github.proify.lyricon.central.json
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.IRemotePlayer
import io.github.proify.lyricon.provider.ProviderConstants
import io.github.proify.lyricon.provider.ProviderInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 远程播放器代理实现。
 *
 * 该类作为 AIDL 层的具体实现，负责接收远程播放器进程上报的状态，
 * 并通过 [PlayerListener] 将事件分发到本地调度体系。
 *
 * 主要职责包括：
 * - 解析并缓存歌曲、播放状态等低频事件；
 * - 通过 [SharedMemory] 高效接收高频播放进度；
 * - 维护后台协程以解耦读取与主线程分发。
 */
internal class RemotePlayer(
    private val info: ProviderInfo,
    private val playerListener: PlayerListener = ActivePlayerDispatcher
) : IRemotePlayer.Stub() {

    companion object {
        private val DEBUG = Constants.isDebug()
        private const val TAG = "RemotePlayer"
    }

    /** 当前播放器对应的状态记录器 */
    private val recorder = PlayerRecorder(info)

    /** 用于跨进程共享播放进度的 SharedMemory */
    private var positionSharedMemory: SharedMemory? = null

    /** 映射后的只读缓冲区，用于读取播放进度 */
    @Volatile
    private var positionReadBuffer: ByteBuffer? = null

    /**
     * 后台协程作用域。
     *
     * 用于 SharedMemory 读取、解码等耗时或高频任务，
     * 使用 [SupervisorJob] 避免单个任务失败导致整体取消。
     */
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default
    )

    /** 播放进度生产者任务（后台读取 SharedMemory） */
    private var positionProducerJob: Job? = null

    /** 播放进度消费者任务（主线程分发回调） */
    private var positionConsumerJob: Job? = null

    /**
     * 播放进度通道。
     *
     * 使用 [Channel.CONFLATED] 仅保留最新进度值，
     * 以避免高频更新导致的积压。
     */
    private val positionChannel = Channel<Long>(Channel.CONFLATED)

    /** 播放进度读取间隔（毫秒） */
    @Volatile
    private var positionUpdateInterval: Long = ProviderConstants.DEFAULT_POSITION_UPDATE_INTERVAL

    /** 标记当前实例是否已释放 */
    private val released = AtomicBoolean(false)

    init {
        initSharedMemory()
    }

    /**
     * 释放资源并终止所有后台任务。
     *
     * 该方法在播放器失效或进程回收时调用。
     */
    fun destroy() {
        if (released.get()) return
        released.set(true)

        stopPositionUpdate()

        positionReadBuffer?.let { SharedMemory.unmap(it) }
        positionReadBuffer = null

        positionSharedMemory?.close()
        positionSharedMemory = null

        positionChannel.close()
        scope.cancel()
    }

    /**
     * 初始化用于播放进度同步的 SharedMemory。
     */
    private fun initSharedMemory() {
        try {
            val hashCode =
                "${info.providerPackageName}/${info.playerPackageName}".hashCode().toHexString()
            positionSharedMemory = SharedMemory.create(
                "lyricon_music_position_${hashCode}_${Os.getpid()}",
                Long.SIZE_BYTES
            ).apply {
                setProtect(OsConstants.PROT_READ or OsConstants.PROT_WRITE)
                positionReadBuffer = mapReadOnly()
            }
            Log.i(TAG, "SharedMemory initialized")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to init SharedMemory", t)
        }
    }

    /**
     * 从 SharedMemory 中读取当前播放进度。
     *
     * @return 播放位置（毫秒），读取失败时返回 0
     */
    private fun readPosition(): Long {
        val buffer = positionReadBuffer ?: return 0
        return try {
            synchronized(buffer) {
                buffer.getLong(0).coerceAtLeast(0)
            }
        } catch (_: Throwable) {
            0
        }
    }

    // ---------- 播放进度更新（Producer / Consumer） ----------

    /**
     * 启动播放进度更新流程。
     *
     * 包含：
     * - 后台 Producer：周期性读取 SharedMemory；
     * - 主线程 Consumer：分发最新进度到监听器。
     */
    private fun startPositionUpdate() {
        if (positionProducerJob != null) return

        if (DEBUG) Log.d(TAG, "Start position updater ,interval $positionUpdateInterval ms")

        positionProducerJob = scope.launch {
            while (isActive) {
                val position = readPosition()
                recorder.lastPosition = position
                positionChannel.trySend(position)
                delay(positionUpdateInterval)
            }
        }

        positionConsumerJob = scope.launch(Dispatchers.Main) {
            positionChannel
                .receiveAsFlow()
                .collectLatest { position ->
                    try {
                        playerListener.onPositionChanged(recorder, position)
                    } catch (t: Throwable) {
                        Log.e(TAG, "Error notifying position listener", t)
                    }
                }
        }
    }

    /**
     * 停止播放进度更新流程。
     */
    @SuppressLint("MemberExtensionConflict")
    private fun stopPositionUpdate() {
        positionProducerJob?.cancel()
        positionProducerJob = null

        positionConsumerJob?.cancel()
        positionConsumerJob = null

        Log.d(TAG, "Stop position updater")
    }

    override fun setPositionUpdateInterval(interval: Int) {
        if (released.get()) return

        positionUpdateInterval = interval.coerceAtLeast(16).toLong()
        if (DEBUG) Log.d(TAG, "Update interval = $positionUpdateInterval ms")

        if (positionProducerJob != null) {
            stopPositionUpdate()
            startPositionUpdate()
        }
    }

    // ---------- 工具方法：主线程安全回调 ----------

    /**
     * 在主线程安全调用 [PlayerListener] 的辅助方法。
     *
     * 主要用于低频事件（歌曲切换、状态变化等）。
     */
    private inline fun runOnMain(crossinline block: PlayerListener.() -> Unit) {
        scope.launch(Dispatchers.Main) {
            try {
                playerListener.block()
            } catch (t: Throwable) {
                Log.e(TAG, "Error notifying listener", t)
            }
        }
    }

    // ---------- AIDL 接口实现 ----------

    @OptIn(ExperimentalSerializationApi::class)
    override fun setSong(bytes: ByteArray?) {
        if (released.get()) return

        val song = bytes?.let {
            try {
                val start = System.currentTimeMillis()
                val decompressedBytes = bytes.inflate()
                val parsed = json.decodeFromStream(
                    Song.serializer(),
                    decompressedBytes.inputStream()
                )
                if (DEBUG) Log.d(TAG, "Song parsed in ${System.currentTimeMillis() - start} ms")
                parsed
            } catch (t: Throwable) {
                Log.e(TAG, "Song parse failed", t)
                null
            }
        }

        val normalized = song?.normalize()
        recorder.lastSong = normalized
        recorder.lastText = null

        Log.i(TAG, "Song changed")
        runOnMain { onSongChanged(recorder, normalized) }
    }

    override fun setPlaybackState(isPlaying: Boolean) {
        if (released.get()) return

        recorder.lastIsPlaying = isPlaying
        runOnMain { onPlaybackStateChanged(recorder, isPlaying) }

        if (DEBUG) Log.i(TAG, "Playback state = $isPlaying")

        if (isPlaying) {
            startPositionUpdate()
        } else {
            stopPositionUpdate()
        }
    }

    override fun seekTo(position: Long) {
        if (released.get()) return

        val safe = position.coerceAtLeast(0)
        recorder.lastPosition = safe

        runOnMain { onSeekTo(recorder, safe) }
    }

    override fun sendText(text: String?) {
        if (released.get()) return

        recorder.lastSong = null
        recorder.lastText = text

        runOnMain { onSendText(recorder, text) }
    }

    override fun setDisplayTranslation(isDisplayTranslation: Boolean) {
        if (released.get()) return

        recorder.lastIsDisplayTranslation = isDisplayTranslation
        runOnMain { onDisplayTranslationChanged(recorder, isDisplayTranslation) }
    }

    /**
     * 向远程进程暴露用于播放进度写入的 SharedMemory。
     */
    override fun getPositionMemory(): SharedMemory? = positionSharedMemory
}