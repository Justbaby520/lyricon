/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
@file:Suppress("unused")

package io.github.proify.lyricon.lyric.view.util

import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.AnticipateInterpolator
import android.view.animation.BounceInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator

object Interpolates {
    val linear: LinearInterpolator by lazy {
        LinearInterpolator()
    }

    val accelerate: AccelerateInterpolator by lazy {
        AccelerateInterpolator()
    }

    val decelerate: DecelerateInterpolator by lazy {
        DecelerateInterpolator()
    }

    val accelerateDecelerate: AccelerateDecelerateInterpolator by lazy {
        AccelerateDecelerateInterpolator()
    }

    val fastOutSlowIn: FastOutSlowInInterpolator by lazy {
        FastOutSlowInInterpolator()
    }

    val bounce: BounceInterpolator by lazy {
        BounceInterpolator()
    }

    val anticipate: AnticipateInterpolator by lazy {
        AnticipateInterpolator()
    }

    val overshoot: OvershootInterpolator by lazy {
        OvershootInterpolator()
    }
}