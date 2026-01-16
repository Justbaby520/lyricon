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

import androidx.annotation.IntRange
import io.github.proify.lyricon.lyric.model.Song

/**
 * 远程播放器接口。
 *
 * 定义与远程播放器服务交互的基本操作，包括：
 * - 歌曲控制
 * - 播放状态控制
 * - 播放位置同步
 * - 文本信息发送
 */
interface RemotePlayer {

    /**
     * 检查远程播放器连接是否仍然有效。
     */
    val isActivated: Boolean

    /**
     * 设置远程播放器当前播放的歌曲信息。
     *
     * @param song 歌曲对象，null 表示清空当前播放
     * @return 命令是否成功发送
     */
    fun setSong(song: Song?): Boolean

    /**
     * 设置远程播放器的播放状态。
     *
     * @param isPlaying true 表示播放中，false 表示暂停
     * @return 命令是否成功发送
     */
    fun setPlaybackState(isPlaying: Boolean): Boolean

    /**
     * 立即跳转到指定播放位置。
     *
     * 通常在用户拖动进度条或主动调整播放位置时调用。
     *
     * @param position 播放位置，单位毫秒，最小值为 0
     * @return 操作是否成功
     */
    fun seekTo(@IntRange(from = 0) position: Long): Boolean

    /**
     * 将播放位置写入到远程播放器共享区域，用于轮询同步。
     *
     * @param position 播放位置，最小值为 0
     * @see setPositionUpdateInterval
     */
    fun setPosition(@IntRange(from = 0) position: Long): Boolean

    /**
     * 设置播放位置轮询间隔，用于共享内存同步机制。
     *
     * @param interval 间隔毫秒数，最小值为 0
     * @return 操作是否成功
     */
    fun setPositionUpdateInterval(@IntRange(from = 0) interval: Int): Boolean

    /**
     * 向远程播放器发送文本消息。
     *
     * 调用此方法会清除之前设置的歌曲信息，播放器进入纯文本模式。
     *
     * @param text 要发送的文本内容，可为 null
     * @return 命令是否成功发送
     */
    fun sendText(text: String?): Boolean
}