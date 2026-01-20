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

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import io.github.proify.lyricon.app.util.AnimationEmoji

private const val LOADING_ANIMATION_NAME = "luck"

object LoadingIndicatorSize {
    val XS: Dp = 24.dp
    val S: Dp = 48.dp
    val M: Dp = 72.dp
    val L: Dp = 96.dp
    val XL: Dp = 120.dp
    val XXL: Dp = 144.dp
}

@Composable
fun LuckLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = LoadingIndicatorSize.XL
) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.Asset(
            AnimationEmoji.getAssetsFile(LOADING_ANIMATION_NAME)
        )
    )

    LottieAnimation(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        modifier = modifier.size(size)
    )
}