/*
 * Copyright (c) 2026 Proify
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

package io.github.proify.lyricon.provider.remote

import android.os.SharedMemory
import android.util.Log
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.IRemotePlayer
import io.github.proify.lyricon.provider.deflate
import io.github.proify.lyricon.provider.json
import java.nio.ByteBuffer

/**
 * 远程播放器代理。
 *
 * 负责与远程播放器服务 [IRemotePlayer] 进行交互，包括：
 * - 设置播放歌曲与状态
 * - 更新播放进度
 * - 发送文本信息
 * - 管理共享内存以实现高频播放进度同步
 *
 * 代理实现了 [RemotePlayer] 接口，并通过 [RemoteServiceBinder] 管理服务绑定。
 */
internal class RemotePlayerProxy : RemotePlayer, RemoteServiceBinder<IRemotePlayer?> {

    private companion object {
        private const val TAG = "RemotePlayerProxy"
    }

    /** 持有远程服务引用，可能为 null */
    @Volatile
    private var remoteService: IRemotePlayer? = null

    /** 用于同步播放位置的共享内存 */
    private var positionSharedMemory: SharedMemory? = null

    /** 映射共享内存的字节缓冲区 */
    private var positionByteBuffer: ByteBuffer? = null

    /**
     * 清理当前绑定的远程服务及相关共享内存资源。
     *
     * 释放映射的缓冲区并关闭共享内存，同时清空服务引用。
     */
    @Synchronized
    private fun clearBinding() {
        try {
            positionByteBuffer?.let { SharedMemory.unmap(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unmap position buffer", e)
        } finally {
            positionByteBuffer = null
        }

        try {
            positionSharedMemory?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to close shared memory", e)
        } finally {
            positionSharedMemory = null
        }

        remoteService = null
    }

    /**
     * 绑定远程播放器服务。
     *
     * 同时尝试获取远程服务提供的共享内存以用于位置同步。
     *
     * @param service 远程播放器服务接口实例，可为 null
     */
    @Synchronized
    override fun bindRemoteService(service: IRemotePlayer?) {
        Log.d(TAG, "bindRemoteService")
        clearBinding()
        remoteService = service

        try {
            val sharedMemory = remoteService?.positionUpdateSharedMemory
            positionSharedMemory = sharedMemory
            positionByteBuffer = positionSharedMemory?.mapReadWrite()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to obtain position shared memory", e)
        }
    }

    /**
     * 设置远程播放器的当前播放歌曲。
     *
     * 歌曲对象会序列化为 JSON 并压缩后发送。
     *
     * @param song 歌曲对象，或 null 表示清空
     */
    override fun setSong(song: Song?) =
        executeRemoteCall {
            val bytes = if (song != null) {
                json.encodeToString(song)
                    .toByteArray()
                    .deflate()
            } else null
            it.setSong(bytes)
        }

    /** 设置远程播放器的播放状态 */
    override fun setPlaybackState(isPlaying: Boolean) =
        executeRemoteCall { it.setPlaybackState(isPlaying) }

    /**
     * 请求远程播放器跳转到指定播放位置。
     *
     * @param position 目标播放位置，最小为 0
     * @return 操作是否成功
     */
    override fun seekTo(position: Long): Boolean =
        executeRemoteCall { it.seekTo(position.coerceAtLeast(0)) }

    /**
     * 直接更新共享内存中的播放位置。
     *
     * @param position 播放位置
     * @return 是否写入成功
     */
    override fun setPosition(position: Long) = try {
        positionByteBuffer?.putLong(0, position.coerceAtLeast(0))
        true
    } catch (e: Exception) {
        Log.e(TAG, "Failed to write position to shared buffer", e)
        false
    }

    /** 设置位置更新间隔 */
    override fun setPositionUpdateInterval(interval: Int) =
        executeRemoteCall { it.setPositionUpdateInterval(interval) }

    /** 向远程播放器发送文本信息 */
    override fun sendText(text: String?) = executeRemoteCall { it.sendText(text) }

    /** 判断远程播放器是否仍然可用 */
    override val isActivated get() = remoteService?.asBinder()?.isBinderAlive == true

    /**
     * 执行远程调用，并统一处理异常。
     *
     * @param block 远程调用逻辑
     * @return 调用是否成功
     */
    private inline fun executeRemoteCall(block: (IRemotePlayer) -> Any?): Boolean {
        val service = remoteService ?: return false
        return try {
            when (val result = block(service)) {
                is Boolean -> result
                else -> true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Remote call failed", e)
            false
        }
    }
}