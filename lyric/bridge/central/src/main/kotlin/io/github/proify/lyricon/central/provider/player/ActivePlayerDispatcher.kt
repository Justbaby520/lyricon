package io.github.proify.lyricon.central.provider.player

import android.util.Log
import io.github.proify.lyricon.central.Constants
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.ProviderInfo
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * 活跃播放器调度器。
 *
 * 负责以抢占式策略维护当前唯一的“活跃播放器”，并将播放器产生的事件
 * （如歌曲切换、播放状态、进度更新等）分发给所有已注册的
 * [ActivePlayerListener]。
 *
 * 核心规则：
 * - 当存在正在播放的活跃播放器时，其它播放器的事件不会生效。
 * - 当不存在活跃播放器，或当前活跃播放器已停止播放时，
 *   新产生播放行为的播放器将获得活跃权。
 * - 活跃播放器发生切换时，会将 [PlayerRecorder] 中缓存的最新状态
 *   同步给所有监听器。
 */
object ActivePlayerDispatcher : PlayerListener {

    private const val TAG = "ActivePlayerDispatcher"
    private val DEBUG = Constants.isDebug()

    /**
     * 内部事件类型标识，仅用于调度逻辑区分事件来源。
     */
    private object EventType {
        const val SONG_CHANGED = 1
        const val PLAYBACK_STATE_CHANGED = 2
        const val POSITION_CHANGED = 3
        const val SEEK_TO = 4
        const val POST_TEXT = 5
    }

    /** 用于保护活跃播放器相关状态的读写锁 */
    private val lock = ReentrantReadWriteLock()

    /** 当前持有活跃权的播放器信息 */
    private var activeInfo: ProviderInfo? = null

    /** 当前活跃播放器是否处于播放状态 */
    private var activeIsPlaying: Boolean = false

    /** 已注册的活跃播放器监听器集合 */
    private val listeners = CopyOnWriteArraySet<ActivePlayerListener>()

    /**
     * 注册活跃播放器监听器。
     *
     * @param listener 监听活跃播放器状态变化的回调对象
     */
    fun addActivePlayerListener(listener: ActivePlayerListener) {
        listeners += listener
    }

    /**
     * 移除已注册的活跃播放器监听器。
     */
    @Suppress("unused")
    fun removeActivePlayerListener(listener: ActivePlayerListener) {
        listeners -= listener
    }

    /**
     * 通知某个播放器提供者已失效。
     *
     * 若该提供者当前正持有活跃权，则会清空活跃状态并通知监听器。
     *
     * @param provider 失效的播放器信息
     */
    fun notifyProviderInvalid(provider: ProviderInfo) {
        val isTarget = lock.read { activeInfo == provider }
        if (isTarget) {
            lock.write {
                if (activeInfo == provider) {
                    activeInfo = null
                    activeIsPlaying = false
                    broadcast { it.onActiveProviderChanged(null) }
                }
            }
        }
    }

    // ---- PlayerListener 实现 ----

    override fun onSongChanged(recorder: PlayerRecorder, song: Song?) {
        if (DEBUG) Log.d(TAG, "onSongChanged: $song")
        handleEvent(EventType.SONG_CHANGED, recorder) { it.onSongChanged(song) }
    }

    override fun onPlaybackStateChanged(recorder: PlayerRecorder, isPlaying: Boolean) {
        if (DEBUG) Log.d(TAG, "onPlaybackStateChanged: $isPlaying")
        handleEvent(EventType.PLAYBACK_STATE_CHANGED, recorder) {
            it.onPlaybackStateChanged(isPlaying)
        }
    }

    override fun onPositionChanged(recorder: PlayerRecorder, position: Long) {
        handleEvent(EventType.POSITION_CHANGED, recorder) {
            it.onPositionChanged(position)
        }
    }

    override fun onSeekTo(recorder: PlayerRecorder, position: Long) {
        handleEvent(EventType.SEEK_TO, recorder) {
            it.onSeekTo(position)
        }
    }

    override fun onPostText(recorder: PlayerRecorder, text: String?) {
        handleEvent(EventType.POST_TEXT, recorder) {
            it.onPostText(text)
        }
    }

    /**
     * 统一的事件调度入口。
     *
     * 根据事件来源与当前活跃播放器状态，判断是否需要：
     * - 仅更新活跃播放器状态；
     * - 切换活跃播放器；
     * - 向监听器广播事件。
     *
     * @param eventType 当前事件类型
     * @param recorder 事件来源的播放器记录器
     * @param notifier 实际执行事件回调的逻辑
     */
    private inline fun handleEvent(
        eventType: Int,
        recorder: PlayerRecorder,
        crossinline notifier: (ActivePlayerListener) -> Unit
    ) {
        var shouldBroadcastOriginalEvent = false

        lock.write {
            val recorderInfo = recorder.info
            val currentInfo = activeInfo
            val recorderPlaying = recorder.lastIsPlaying

            if (currentInfo == recorderInfo) {
                // 场景一：事件来自当前活跃播放器
                activeIsPlaying = recorderPlaying
                shouldBroadcastOriginalEvent = true
            } else {
                // 场景二：非活跃播放器尝试获得活跃权
                val canSwitch = currentInfo == null || (!activeIsPlaying && recorderPlaying)

                if (canSwitch) {
                    activeInfo = recorderInfo
                    activeIsPlaying = recorderPlaying

                    // 通知活跃播放器变更，并同步其历史状态
                    broadcast { it.onActiveProviderChanged(recorderInfo) }
                    syncNewProviderState(recorder)

                    // 歌曲切换事件已在同步流程中处理，避免重复分发
                    if (eventType != EventType.SONG_CHANGED) {
                        shouldBroadcastOriginalEvent = true
                    }
                }
            }
        }

        if (shouldBroadcastOriginalEvent) {
            broadcast(notifier)
        }
    }

    /**
     * 在活跃播放器切换完成后，
     * 将其记录器中缓存的最新状态同步给所有监听器。
     */
    private fun syncNewProviderState(recorder: PlayerRecorder) {
        // 优先同步文本信息（如错误提示、解析结果等）
        recorder.lastText?.let { text ->
            broadcast { it.onPostText(text) }
            return
        }

        broadcast {
            it.onPlaybackStateChanged(activeIsPlaying)
            it.onSongChanged(recorder.lastSong)
            it.onPositionChanged(recorder.lastPosition)
        }
    }

    /**
     * 向所有已注册监听器分发事件，并隔离单个监听器的异常。
     */
    private inline fun broadcast(crossinline notifier: (ActivePlayerListener) -> Unit) {
        for (listener in listeners) {
            try {
                notifier(listener)
            } catch (e: Exception) {
                Log.e(TAG, "Dispatch failed for ${listener.javaClass.simpleName}", e)
            }
        }
    }
}