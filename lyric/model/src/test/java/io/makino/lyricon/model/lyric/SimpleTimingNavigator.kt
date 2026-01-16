@file:Suppress("unused")

package io.makino.lyricon.model.lyric

import io.github.proify.lyricon.lyric.model.interfaces.ILyricTiming

/**
 * 普通版本的时间轴导航器（无缓存优化）
 */
class SimpleTimingNavigator<T : ILyricTiming>(
    source: Array<T>
) {

    /** 已按 begin 升序排列的歌词条目 */
    val entries: Array<T> = source
    val size: Int = entries.size

    /**
     * 在指定 position 上遍历匹配到的歌词项
     *
     * @return 匹配到的歌词数量
     */
    inline fun forEachAt(position: Long, action: (T) -> Unit): Int {
        if (size == 0) return 0
        val matchIdx = binarySearchIndexForPosition(position)
        return if (matchIdx >= 0) forEachOverlappingAt(position, matchIdx, action) else 0
    }

    /**
     * 在指定时间查找歌词
     *
     * - 若当前位置无匹配项，则回退到 position 之前的最后一条歌词
     */
    inline fun forEachAtOrPrevious(position: Long, action: (T) -> Unit): Int {
        val count = forEachAt(position, action)
        if (count > 0) return count

        val previous = findPreviousEntry(position) ?: return 0
        action(previous)
        return 1
    }

    /**
     * 查找 position 之前的最后一条歌词
     */
    fun findPreviousEntry(position: Long): T? {
        if (size == 0) return null
        if (position < entries[0].begin) return null
        if (position > entries[size - 1].end) return entries[size - 1]

        var low = 0
        var high = size - 1
        var result = -1

        while (low <= high) {
            val mid = (low + high) ushr 1
            val entry = entries[mid]

            if (entry.begin < position) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        return if (result >= 0) entries[result] else null
    }

    /**
     * 二分查找指定 position 所在的歌词索引
     *
     * @return 匹配索引，未命中返回 -1
     */
    fun binarySearchIndexForPosition(position: Long): Int {
        var low = 0
        var high = size - 1

        while (low <= high) {
            val mid = (low + high) ushr 1
            val entry = entries[mid]

            when {
                position < entry.begin -> high = mid - 1
                position > entry.end -> low = mid + 1
                else -> return mid
            }
        }
        return -1
    }

    /**
     * 处理重叠歌词：从 matchIndex 向前回溯到第一个重叠项，然后顺序遍历
     */
    inline fun forEachOverlappingAt(
        position: Long,
        matchIndex: Int,
        action: (T) -> Unit
    ): Int {
        var start = matchIndex

        while (start > 0) {
            val prev = entries[start - 1]
            if (position in prev.begin..prev.end) start-- else break
        }

        var count = 0
        for (i in start until size) {
            val entry = entries[i]
            if (position < entry.begin) break
            if (position <= entry.end) {
                action(entry)
                count++
            }
        }
        return count
    }
}