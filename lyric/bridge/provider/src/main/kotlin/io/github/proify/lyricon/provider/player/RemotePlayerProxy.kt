/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.provider.player

import android.os.SharedMemory
import android.util.Log
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.IRemotePlayer
import io.github.proify.lyricon.provider.ProviderConstants
import io.github.proify.lyricon.provider.RemotePlayer
import io.github.proify.lyricon.provider.deflate
import io.github.proify.lyricon.provider.json
import io.github.proify.lyricon.provider.service.RemoteServiceBinder
import java.nio.ByteBuffer

/**
 * 远程播放器代理类。
 *
 * 该类作为 [RemotePlayer] 的实现，封装了与远程 [IRemotePlayer] AIDL 接口的通信逻辑。
 * 通过 [SharedMemory] 实现跨进程的高频播放进度同步，以降低 Binder 调用开销。
 *
 * @author Lyricon Team
 *
 */
internal class RemotePlayerProxy : RemotePlayer, RemoteServiceBinder<IRemotePlayer?> {

    @Volatile
    var isConnected: Boolean = false
        internal set

    /**
     * 持有远程服务的引用。
     * 使用 [Volatile] 确保多线程环境下的可见性。
     */
    @Volatile
    private var remoteService: IRemotePlayer? = null

    /** 用于同步播放位置的共享内存实例 */
    private var positionSharedMemory: SharedMemory? = null

    /** 映射到进程虚拟地址空间的字节缓冲区，用于直接操作进度数据 */
    private var positionByteBuffer: ByteBuffer? = null

    /**
     * 处理远程服务绑定。
     *
     * @param service 远程服务实例，若为 null 则执行解绑逻辑。
     */
    @Synchronized
    override fun bindRemoteService(service: IRemotePlayer?) {
        if (ProviderConstants.isDebug()) Log.d(TAG, "Binding remote service: ${service != null}")
        clearBinding()

        remoteService = service ?: return

        runCatching {
            service.positionMemory?.also { memory ->
                positionSharedMemory = memory
                // 映射为读写模式，用于更新进度
                positionByteBuffer = memory.mapReadWrite()
            }
        }.onFailure { e ->
            Log.e(TAG, "Failed to map shared memory for position sync", e)
        }
    }

    override fun setSong(song: Song?): Boolean = executeRemoteCall { service ->
        val payload = song?.let {
            json.encodeToString<Song>(it).toByteArray().deflate()
        }
        service.setSong(payload)
    }

    override fun setPlaybackState(isPlaying: Boolean): Boolean =
        executeRemoteCall { it.setPlaybackState(isPlaying) }

    override fun seekTo(position: Long): Boolean =
        executeRemoteCall { it.seekTo(position.coerceAtLeast(0)) }

    override fun setPosition(position: Long): Boolean {
        return try {
            positionByteBuffer?.putLong(0, position.coerceAtLeast(0))
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun setPositionUpdateInterval(interval: Int): Boolean =
        executeRemoteCall { it.setPositionUpdateInterval(interval) }

    override fun sendText(text: String?): Boolean =
        executeRemoteCall { it.sendText(text) }

    override fun setDisplayTranslation(isDisplayTranslation: Boolean): Boolean =
        executeRemoteCall { it.setDisplayTranslation(isDisplayTranslation) }

    override val isActivated: Boolean
        get() = remoteService?.asBinder()?.isBinderAlive == true

    /**
     * 清理资源并关闭共享内存映射。
     */
    @Synchronized
    private fun clearBinding() {
        positionByteBuffer?.let {
            runCatching { SharedMemory.unmap(it) }
            positionByteBuffer = null
        }

        positionSharedMemory?.let {
            runCatching { it.close() }
            positionSharedMemory = null
        }

        remoteService = null
    }

    /**
     * 封装 Binder 调用通用逻辑。
     *
     * @param block 具体的 AIDL 调用逻辑
     * @return 调用是否成功执行（对于有返回值的调用，返回其结果；无返回值则返回 true）
     */
    private inline fun executeRemoteCall(block: (IRemotePlayer) -> Any?): Boolean {
        if (!isConnected) {
            return false
        }
        val service = remoteService ?: return false
        return try {
            val result = block(service)
            (result as? Boolean) ?: true
        } catch (e: Exception) {
            Log.e(TAG, "Remote call failed: ${e.message}", e)
            false
        }
    }

    private companion object {
        private const val TAG = "RemotePlayerProxy"
    }
}