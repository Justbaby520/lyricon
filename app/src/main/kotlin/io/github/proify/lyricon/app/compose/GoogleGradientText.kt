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

package io.github.proify.lyricon.app.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp

val GOOGLE_COLORS: List<Color> = listOf(
    Color(0xFFEA4335), // 红色
    Color(0xFFFBBC05), // 黄色
    Color(0xFF34A853), // 绿色
    Color(0xFF4285F4)  // 蓝色
)

@Composable
fun GoogleRainbowText(
    text: String,
    style: TextStyle = LocalTextStyle.current,
) {
    val gradientBrush = Brush.linearGradient(colors = GOOGLE_COLORS)
    Text(
        text = text,
        style = style.copy(brush = gradientBrush)
    )
}

@Composable
@Preview
fun RainbowTextDemo() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        GoogleRainbowText(text = "Google2333333333", TextStyle(fontSize = 36.sp))
        GoogleRainbowText(text = "Rainbow", TextStyle(fontSize = 36.sp))
    }
}