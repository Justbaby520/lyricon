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

@file:Suppress("unused")

package io.github.proify.lyricon.lyric.model.extensions

import io.github.proify.lyricon.lyric.model.interfaces.ILyricTiming

/**
 * 时间轴导航器：用于在毫秒级播放进度中高效定位歌词。
 *
 * ### 核心架构设计：
 * 1. **分级定位策略**：
 * - **Hot Path (O(1))**：针对连续播放场景，优先校验缓存索引及其后继索引。
 * - **Cold Path (O(log N))**：针对进度条拖动或回退场景，使用二分查找定位。
 * 2. **轻重路径分离**：
 * - [first]：极速路径，仅返回当前锚点索引项，无重叠回溯开销。
 * - [forEachAt]：完整路径，处理重叠歌词（Overlapping Lyrics），保证多行并发显示的准确性。
 *
 * ### 使用前提：
 * - [source] 必须已按 [ILyricTiming.begin] 升序排列。
 * - 本类【非线程安全】，建议仅在播放控制线程中使用。
 */
class TimingNavigator<T : ILyricTiming>(
    val source: Array<T>
) {
    /** 歌词总数 */
    val size: Int = source.size

    /** 最近一次成功匹配的索引。初始为 -1，表示尚未匹配或已超出边界。 */
    var lastMatchedIndex: Int = -1
        private set

    /** 最近一次查询的时间戳（ms）。用于判断播放方向以决定是否启用顺序优化。 */
    var lastQueryPosition: Long = -1L
        private set

    // ----------------------------------------------------------------
    // Public APIs
    // ----------------------------------------------------------------

    /**
     * 获取当前时间点对应的第一条歌词。
     * 适用于只需显示单行歌词的场景。
     *
     * @param position 当前播放时间 (ms)
     * @return 匹配的歌词项，若无匹配则返回 null
     */
    fun first(position: Long): T? {
        val index = findTargetIndex(position)
        updateCache(position, index)
        return if (index != -1) source[index] else null
    }

    /**
     * 遍历当前时间点所有生效的歌词项（处理重叠歌词）。
     * 适用于支持多行重叠、卡拉OK逐字渲染等复杂场景。
     *
     * @param position 当前播放时间 (ms)
     * @param action 对每一项匹配歌词执行的操作
     * @return 匹配到的歌词总数
     */
    inline fun forEachAt(position: Long, action: (T) -> Unit): Int {
        if (size == 0) return 0

        val anchorIndex = findTargetIndex(position)
        updateCache(position, anchorIndex)

        if (anchorIndex == -1) return 0

        // 针对重叠歌词执行回溯扫描
        return resolveOverlapping(position, anchorIndex, action)
    }

    /**
     * 尝试匹配当前歌词，若当前为空隙，则回退并匹配最近的一条历史歌词。
     * 常用于 UI 在间歇期保留上一句歌词的显示状态。
     */
    inline fun forEachAtOrPrevious(position: Long, action: (T) -> Unit): Int {
        val count = forEachAt(position, action)
        if (count > 0) return count

        val previous = findPreviousEntry(position) ?: return 0
        action(previous)
        return 1
    }

    /**
     * 查找当前位置之前的最后一条有效歌词。
     */
    fun findPreviousEntry(position: Long): T? {
        if (size == 0 || position < source[0].begin) return null
        if (position > source[size - 1].end) return source[size - 1]

        var low = 0
        var high = size - 1
        var resultIdx = -1

        while (low <= high) {
            val mid = (low + high) ushr 1
            if (source[mid].begin < position) {
                resultIdx = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return if (resultIdx >= 0) source[resultIdx] else null
    }

    /**
     * 清除缓存索引与时间记录。
     * 在切换歌曲或停止播放时应调用此方法。
     */
    fun resetCache() {
        lastMatchedIndex = -1
        lastQueryPosition = -1L
    }

    // ----------------------------------------------------------------
    // Internal Engine
    // ----------------------------------------------------------------

    /**
     * 查找目标索引的核心引擎。
     * 优先顺序：边界检查 -> 顺序缓存命中 -> 线性探测下一项 -> 二分查找。
     */
    fun findTargetIndex(position: Long): Int {
        if (size == 0) return -1

        // 快速失败：超出整体时间轴
        if (position < source[0].begin || position > source[size - 1].end) return -1

        val lastIdx = lastMatchedIndex

        // 顺序播放优化：当时间线性增加时，利用缓存索引
        if (lastIdx >= 0 && position >= lastQueryPosition) {
            // 1. 检查当前项是否依然有效
            if (isHit(lastIdx, position)) return lastIdx

            // 2. 检查紧邻的下一项（连续播放最常见场景）
            val nextIdx = lastIdx + 1
            if (nextIdx < size && isHit(nextIdx, position)) return nextIdx
        }

        // 随机跳转或回退：执行二分查找
        return binarySearch(position)
    }

    private fun binarySearch(position: Long): Int {
        var low = 0
        var high = size - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val entry = source[mid]
            when {
                position < entry.begin -> high = mid - 1
                position > entry.end -> low = mid + 1
                else -> return mid
            }
        }
        return -1
    }

    /**
     * 处理重叠逻辑。
     * 由于 source 按 begin 排序，多个歌词可能在同一 position 重叠。
     * 需要向前回溯到最早可能重叠的项，然后顺序向后扫描。
     */
    @PublishedApi
    internal inline fun resolveOverlapping(
        position: Long,
        anchorIndex: Int,
        action: (T) -> Unit
    ): Int {
        var start = anchorIndex

        // 回溯：寻找所有可能包含当前时间的歌词起始点
        while (start > 1) {
            val prev = source[start - 1]
            // 如果前一项的结束时间在 position 之后，说明存在重叠风险，继续回溯
            if (prev.end >= position) start-- else break
        }

        var count = 0
        for (i in start until size) {
            val entry = source[i]
            // 剪枝：如果当前项开始时间已超，后续项因排序关系必不匹配
            if (position < entry.begin) break

            if (position <= entry.end) {
                action(entry)
                count++
            }
        }
        return count
    }

    private fun isHit(index: Int, position: Long): Boolean {
        val item = source[index]
        return position >= item.begin && position <= item.end
    }

    fun updateCache(position: Long, index: Int) {
        lastQueryPosition = position
        lastMatchedIndex = index
    }
}