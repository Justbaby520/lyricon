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

import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.ProviderInfo

/**
 * 播放器状态记录器。
 *
 * 用于缓存某个播放器最近一次上报的关键状态信息，
 * 以便在活跃播放器切换或监听器注册时进行状态同步。
 *
 * @param info 播放器对应的提供者信息
 */
data class PlayerRecorder(val info: ProviderInfo) {

    /**
     * 最近一次播放的歌曲。
     *
     * 可能为空，表示当前未加载或未识别到有效歌曲。
     */
    var lastSong: Song? = null

    /**
     * 最近一次记录的播放状态。
     *
     * true 表示正在播放，false 表示暂停或停止。
     */
    var lastIsPlaying: Boolean = false

    /**
     * 最近一次记录的播放进度。
     *
     * 单位为毫秒，初始值为 -1 表示尚未产生有效进度。
     */
    var lastPosition: Long = -1

    /**
     * 最近一次上报的文本信息。
     *
     * 通常用于传递错误信息、提示文本或其它附加说明。
     */
    var lastText: String? = null

    /**
     * 是否显示翻译。
     */
    var lastIsDisplayTranslation: Boolean = false

}