package io.github.proify.lyricon.lyric.view.model

import io.github.proify.lyricon.lyric.model.interfaces.IRichLyricLine

class RichLyricLineModel(private val source: IRichLyricLine) : IRichLyricLine by source {
    var previous: RichLyricLineModel? = null
    var next: RichLyricLineModel? = null
}