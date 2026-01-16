package io.makino.lyricon.model.lyric

import io.github.proify.lyricon.lyric.model.interfaces.ILyricTiming

data class TestLyricLine(
    override var begin: Long,
    override var end: Long,
    override var duration: Long = end - begin
) : ILyricTiming