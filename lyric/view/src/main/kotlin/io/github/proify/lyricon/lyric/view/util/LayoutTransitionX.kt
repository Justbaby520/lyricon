/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("SpellCheckingInspection", "ConstPropertyName")

package io.github.proify.lyricon.lyric.view.util

import android.animation.LayoutTransition
import android.animation.TimeInterpolator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator

class LayoutTransitionX(
    config: Config = Config()
) : LayoutTransition() {
    init {
        setDuration(CHANGE_APPEARING, config.changeAppearingDuration)
        setDuration(CHANGE_DISAPPEARING, config.changeDisappearingDuration)
        setDuration(CHANGING, config.changingDuration)
        setDuration(APPEARING, config.appearingDuration)
        setDuration(DISAPPEARING, config.disappearingDuration)

        setInterpolator(CHANGE_APPEARING, config.changeAppearingInterpolator)
        setInterpolator(CHANGE_DISAPPEARING, config.changeDisappearingInterpolator)
        setInterpolator(CHANGING, config.changingInterpolator)
        setInterpolator(APPEARING, config.appearingInterpolator)
        setInterpolator(DISAPPEARING, config.disappearingInterpolator)
    }

    data class Config(
        val appearingDuration: Long = 220,
        val disappearingDuration: Long = 180,
        val changeAppearingDuration: Long = 300,
        val changeDisappearingDuration: Long = 300,
        val changingDuration: Long = 260,

        val appearingInterpolator: TimeInterpolator = INTERPOLATOR_ACCEL_DECEL,
        val disappearingInterpolator: TimeInterpolator = INTERPOLATOR_ACCEL_DECEL,
        val changeAppearingInterpolator: TimeInterpolator = INTERPOLATOR_DECEL,
        val changeDisappearingInterpolator: TimeInterpolator = INTERPOLATOR_DECEL,
        val changingInterpolator: TimeInterpolator = INTERPOLATOR_DECEL
    )

    companion object {
        private val INTERPOLATOR_ACCEL_DECEL: TimeInterpolator =
            FastOutSlowInInterpolator()
        private val INTERPOLATOR_DECEL: TimeInterpolator =
            LinearOutSlowInInterpolator()
    }
}