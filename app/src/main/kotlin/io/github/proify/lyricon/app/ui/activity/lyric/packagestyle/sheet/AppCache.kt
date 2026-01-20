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

package io.github.proify.lyricon.app.ui.activity.lyric.packagestyle.sheet

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import io.github.proify.android.extensions.toBitmap
import java.lang.ref.WeakReference
import java.util.WeakHashMap

object AppCache {
    private val iconCache = WeakHashMap<String, Drawable>()
    private val labelCache = WeakHashMap<String, String>()

    private val blurredCache = mutableMapOf<String, WeakReference<Bitmap>>()

    fun getCachedIcon(packageName: String): Drawable? = synchronized(iconCache) {
        iconCache[packageName]
    }

    fun cacheIcon(packageName: String, icon: Drawable?): Any? = synchronized(iconCache) {
        if (icon != null) iconCache[packageName] = icon else iconCache.remove(packageName)
    }

    fun getCachedLabel(packageName: String): String? = synchronized(labelCache) {
        labelCache[packageName]
    }

    fun cacheLabel(packageName: String, label: String): Unit = synchronized(labelCache) {
        labelCache[packageName] = label
    }

    fun getBitmap(packageName: String, radius: Float = 20f): Bitmap? {
        val cached = blurredCache[packageName]?.get()
        if (cached != null) return cached

        val drawable = getCachedIcon(packageName) ?: return null
        val bitmap = drawable.toBitmap()

        blurredCache[packageName] = WeakReference(bitmap)
        return bitmap
    }
}