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

package io.github.proify.lyricon.lyric_test

import android.graphics.Typeface
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.github.proify.lyricon.lyric.model.LyricLine
import io.github.proify.lyricon.lyric.view.DefaultMarqueeConfig
import io.github.proify.lyricon.lyric.view.DefaultSyllableConfig
import io.github.proify.lyricon.lyric.view.LyricLineConfig
import io.github.proify.lyricon.lyric.view.MainTextConfig
import io.github.proify.lyricon.lyric_test.databinding.MarqueeBinding

class MarqueeTestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val b = MarqueeBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.line.setStyle(
            LyricLineConfig(
                text = MainTextConfig(),
                marquee = DefaultMarqueeConfig(),
                syllable = DefaultSyllableConfig(),
                gradientProgressStyle = true
            ).apply {
                text.apply {
                    textSize = 28f.sp
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                }
                marquee.apply {
                    ghostSpacing = 0.dp.toFloat()
                    scrollSpeed = 50f
                    loopDelay = 500
                    //repeatCount = 1
                    stopAtEnd = true
                }
            })

        b.line.setLyric(LyricLine(text = "哈基米叮咚鸡胖宝宝踩踩背搞核酸"))
        b.line.startMarquee()
    }
}