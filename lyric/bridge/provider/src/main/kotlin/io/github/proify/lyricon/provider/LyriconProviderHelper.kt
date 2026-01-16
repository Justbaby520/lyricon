@file:Suppress("unused")

package io.github.proify.lyricon.provider

import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.remote.RemotePlayer
import io.github.proify.lyricon.provider.remote.RemoteService
import io.github.proify.lyricon.provider.remote.addOnConnectionListener

/**
 * [LyriconProvider] 辅助类，用于管理播放器与远程服务连接。
 *
 * 提供对 [CachedRemotePlayer] 的访问，支持断线重连后自动恢复播放状态。
 *
 * @property provider 所属的 [LyriconProvider] 实例
 */
class LyriconProviderHelper(val provider: LyriconProvider) {

    private companion object {
        private const val TAG = "LyriconProviderHelper"
    }

    /** 远程服务实例 */
    val service: RemoteService = provider.service

    /** 缓存播放器实例，支持断线重连恢复 */
    val player: CachedRemotePlayer = CachedRemotePlayer(service.player)

    /** 当前连接状态 */
    private var isConnected = false

    init {
        service.addOnConnectionListener {
            fun handleConnected() {
                isConnected = true
                player.resume()
            }
            onConnected { handleConnected() }
            onReconnected { handleConnected() }
            onDisconnected { isConnected = false }
            onConnectTimeout { isConnected = false }
        }
    }

    /** 发起注册请求 */
    fun register(): Boolean = provider.register()

    /** 注销提供者 */
    fun unregister() {
        provider.unregister()
    }

    /** 销毁提供者及相关资源 */
    fun destroy() {
        provider.destroy()
    }

    /**
     * 缓存状态的播放器实现，用于断线重连恢复。
     *
     * 内部维护最近一次设置的歌曲、文本、播放状态、位置和更新间隔，
     * 并在恢复连接后将状态同步到远程播放器。
     */
    @Suppress("MemberVisibilityCanBePrivate")
    inner class CachedRemotePlayer(private val remotePlayer: RemotePlayer) : RemotePlayer {

        /** 最近设置的歌曲（发送文本后会被清空） */
        var lastSong: Song? = null
            private set

        /** 最近的播放状态 */
        var lastPlaybackState: Boolean = false
            private set

        /** 最近的位置 */
        var lastPosition: Long = 0
            private set

        /** 最近设置的位置更新间隔 */
        var lastPositionUpdateInterval: Int? = null
            private set

        /** 最近发送的文本（设置歌曲后会被清空） */
        var lastText: String? = null
            private set

        /** 根据缓存状态恢复播放 */
        internal fun resume() {
            remotePlayer.setPlaybackState(lastPlaybackState)
            lastPositionUpdateInterval?.let(remotePlayer::setPositionUpdateInterval)

            when {
                lastText != null -> remotePlayer.sendText(lastText)
                else -> {
                    remotePlayer.setSong(lastSong)
                    remotePlayer.seekTo(lastPosition)
                }
            }
        }

        override val isActivated: Boolean get() = remotePlayer.isActivated

        override fun setSong(song: Song?): Boolean {
            lastSong = song
            lastText = null
            return isConnected && remotePlayer.setSong(song)
        }

        override fun setPlaybackState(isPlaying: Boolean): Boolean {
            lastPlaybackState = isPlaying
            return isConnected && remotePlayer.setPlaybackState(isPlaying)
        }

        override fun seekTo(position: Long): Boolean {
            lastPosition = position
            return isConnected && remotePlayer.seekTo(position)
        }

        override fun setPosition(position: Long): Boolean {
            lastPosition = position
            return isConnected && remotePlayer.setPosition(position)
        }

        override fun setPositionUpdateInterval(interval: Int): Boolean {
            lastPositionUpdateInterval = interval
            return isConnected && remotePlayer.setPositionUpdateInterval(interval)
        }

        override fun sendText(text: String?): Boolean {
            lastText = text
            lastSong = null
            return isConnected && remotePlayer.sendText(text)
        }
    }
}