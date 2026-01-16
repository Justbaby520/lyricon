/*
 * Copyright (c) 2026 Proify
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

@file:Suppress("MemberVisibilityCanBePrivate")

package io.github.proify.lyricon.xposed.lyricview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.children
import androidx.core.view.isNotEmpty
import io.github.proify.android.extensions.dp
import io.github.proify.android.extensions.visibilityIfChanged
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.lyric.style.LyricStyle
import io.github.proify.lyricon.lyric.view.util.LayoutTransitionX
import io.github.proify.lyricon.xposed.util.StatusBarColorMonitor
import io.github.proify.lyricon.xposed.util.StatusColor

@SuppressLint("ViewConstructor")
class LyricView(
    context: Context,
    initialStyle: LyricStyle,
    linkedTextView: TextView?
) : LinearLayout(context), StatusBarColorMonitor.OnColorChangeListener {

    companion object {
        const val VIEW_TAG: String = "lyricon:lyric_view"
    }

    val logoView: LyricLogoView = LyricLogoView(context).apply {
        this.linkedTextView = linkedTextView
    }

    val textView: LyricPlayerView = LyricPlayerView(context).apply {
        this.linkedTextView = linkedTextView
        eventListener = object : LyricPlayerView.EventListener {
            override fun enteringInterludeMode(duration: Long) {
                logoView.syncProgress(0, duration)
            }

            override fun exitInterludeMode() {
                logoView.clearProgress()
            }
        }
    }

    var currentStyle: LyricStyle = initialStyle
        private set

    private var currentStatusColor: StatusColor = StatusColor(Color.BLACK, false)

    private var isPlaying: Boolean = false

    private val textViewOnHierarchyChangeListener = object : OnHierarchyChangeListener {
        override fun onChildViewAdded(parent: View?, child: View?) {
            updateVisibility()
        }

        override fun onChildViewRemoved(parent: View?, child: View?) {
            updateVisibility()
        }
    }

    private val myLayoutTransition = LayoutTransitionX()

    init {
        tag = VIEW_TAG
        gravity = Gravity.CENTER_VERTICAL
        layoutTransition = myLayoutTransition

        addView(logoView)
        addView(textView)

        applyStyle(initialStyle)
        visibility = GONE
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        textView.setOnHierarchyChangeListener(textViewOnHierarchyChangeListener)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        textView.setOnHierarchyChangeListener(null)
    }

    fun updateStyle(style: LyricStyle) {
        currentStyle = style
        logoView.applyStyle(style)
        textView.applyStyle(style)
        updateLayoutParams(style)

        invalidate()
        requestLayout()
    }

    private fun applyStyle(style: LyricStyle) {
        currentStyle = style
        logoView.applyStyle(style)
        textView.applyStyle(style)
        updateLayoutParams(style)
    }

    private fun updateLayoutParams(style: LyricStyle) {
        val basicStyle = style.basicStyle
        val margins = basicStyle.margins
        val paddings = basicStyle.paddings

        val params = (layoutParams as? MarginLayoutParams)
            ?: MarginLayoutParams(basicStyle.width.dp, LayoutParams.MATCH_PARENT)

        params.apply {
            width = basicStyle.width.dp
            leftMargin = margins.left.dp
            topMargin = margins.top.dp
            rightMargin = margins.right.dp
            bottomMargin = margins.bottom.dp
        }

        layoutParams = params

        setPadding(
            paddings.left.dp,
            paddings.top.dp,
            paddings.right.dp,
            paddings.bottom.dp
        )
    }

    override fun onColorChange(color: StatusColor) {
        currentStatusColor = color
        children.forEach { child ->
            (child as? StatusBarColorMonitor.OnColorChangeListener)?.onColorChange(color)
        }
    }

    fun setPlaying(isPlaying: Boolean) {
        this.isPlaying = isPlaying
        updateVisibility()
    }

    fun updateVisibility() {
        visibilityIfChanged = if (isPlaying && textView.isNotEmpty()) {
            VISIBLE
        } else {
            GONE
        }
    }

    fun seekTo(position: Long) {
        textView.seekTo(position)
    }

    fun updatePosition(position: Long) {
        textView.setPosition(position)
    }

    fun updateSong(song: Song?) {
        textView.song = song
    }

    @Suppress("unused")
    fun updateText(text: String?) {
    }
}