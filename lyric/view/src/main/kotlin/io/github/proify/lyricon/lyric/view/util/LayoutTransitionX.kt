/*
 * Copyright 2026 Proify
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

@file:Suppress("SpellCheckingInspection", "ConstPropertyName")

package io.github.proify.lyricon.lyric.view.util

import android.animation.LayoutTransition
import android.animation.TimeInterpolator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator

class LayoutTransitionX : LayoutTransition() {

    init {
        setDuration(CHANGE_APPEARING, mChangingAppearingDuration)
        setDuration(CHANGE_DISAPPEARING, mChangingDisappearingDuration)
        setDuration(CHANGING, mChangingDuration)
        setDuration(APPEARING, mAppearingDuration)
        setDuration(DISAPPEARING, mDisappearingDuration)

        setInterpolator(CHANGE_APPEARING, sChangingAppearingInterpolator)
        setInterpolator(CHANGE_DISAPPEARING, sChangingDisappearingInterpolator)
        setInterpolator(CHANGING, sChangingInterpolator)
        setInterpolator(APPEARING, sAppearingInterpolator)
        setInterpolator(DISAPPEARING, sDisappearingInterpolator)
    }

    companion object {
        private val ACCEL_DECEL_INTERPOLATOR: TimeInterpolator = FastOutSlowInInterpolator()
        private val DECEL_INTERPOLATOR: TimeInterpolator = LinearOutSlowInInterpolator()

        val sAppearingInterpolator: TimeInterpolator = ACCEL_DECEL_INTERPOLATOR
        val sDisappearingInterpolator: TimeInterpolator = ACCEL_DECEL_INTERPOLATOR
        val sChangingAppearingInterpolator: TimeInterpolator = DECEL_INTERPOLATOR
        val sChangingDisappearingInterpolator: TimeInterpolator = DECEL_INTERPOLATOR
        val sChangingInterpolator: TimeInterpolator = DECEL_INTERPOLATOR

        const val DEFAULT_DURATION: Long = 300
        const val mChangingAppearingDuration: Long = DEFAULT_DURATION
        const val mChangingDisappearingDuration: Long = DEFAULT_DURATION
        const val mChangingDuration: Long = DEFAULT_DURATION
        const val mAppearingDuration: Long = DEFAULT_DURATION
        const val mDisappearingDuration: Long = DEFAULT_DURATION
    }
}