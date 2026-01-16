@file:Suppress("ReplacePrintlnWithLogging", "TestMethodWithoutAssertion")

package io.makino.lyricon.model.lyric

import io.github.proify.lyricon.lyric.model.extensions.TimingNavigator
import org.junit.Test
import kotlin.random.Random
import kotlin.system.measureTimeMillis


class LyricTimingNavigatorPerformanceTest {

    private fun generateLyrics(
        count: Int,
        duration: Long = 1000L,
        overlapEvery: Int = 0
    ): List<TestLyricLine> {
        val list = ArrayList<TestLyricLine>(count)
        var time = 0L

        repeat(count) { index ->
            val begin = if (overlapEvery > 0 && index % overlapEvery == 0) {
                time - duration / 2
            } else {
                time
            }
            val end = begin + duration
            list += TestLyricLine(begin, end)
            time += duration
        }
        return list
    }

    private fun measureMultipleTimes(
        repeatCount: Int = 5,
        block: () -> Unit
    ): Pair<List<Long>, Long> {
        val times = mutableListOf<Long>()
        repeat(repeatCount) {
            val time = measureTimeMillis { block() }
            times += time
        }
        val average = times.average().toLong()
        return times to average
    }

    @Test
    fun sequentialPlaybackPerformance() {
        val lyrics = generateLyrics(count = 1000000)
        val navigator = TimingNavigator(lyrics.toTypedArray())
        val iterations = 1000000

        val (times, avg) = measureMultipleTimes {
            var position = 0L
            repeat(iterations) {
                navigator.forEachAt(position) { }
                position += 10
            }
        }

        println("Sequential playback times: $times")
        println("Sequential playback average: $avg ms")
    }

    @Test
    fun randomSeekPerformance() {
        val lyrics = generateLyrics(count = 100_000)
        val navigator = TimingNavigator(lyrics.toTypedArray())
        val iterations = 1_000_000
        val maxTime = lyrics.last().end

        val (times, avg) = measureMultipleTimes {
            repeat(iterations) {
                val position = Random.nextLong(0, maxTime)
                navigator.forEachAt(position) { }
            }
        }

        println("Random seek times: $times")
        println("Random seek average: $avg ms")
    }

    @Test
    fun overlappingLyricsPerformance() {
        val lyrics = generateLyrics(
            count = 100_000,
            overlapEvery = 3
        )
        val navigator = TimingNavigator(lyrics.toTypedArray())
        val iterations = 500_000

        val (times, avg) = measureMultipleTimes {
            var position = 0L
            repeat(iterations) {
                navigator.forEachAt(position) { }
                position += 15
            }
        }

        println("Overlapping lyrics times: $times")
        println("Overlapping lyrics average: $avg ms")
    }
}
