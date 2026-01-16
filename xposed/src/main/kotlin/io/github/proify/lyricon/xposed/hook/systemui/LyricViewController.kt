package io.github.proify.lyricon.xposed.hook.systemui

import android.annotation.SuppressLint
import android.os.Looper
import com.highcapable.yukihookapi.hook.log.YLog
import io.github.proify.lyricon.central.provider.player.ActivePlayerListener
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.ProviderInfo
import io.github.proify.lyricon.xposed.lyricview.LyricView
import io.github.proify.lyricon.xposed.util.LyricPrefs
import io.github.proify.lyricon.xposed.util.StatusBarColorMonitor
import io.github.proify.lyricon.xposed.util.StatusColor

object LyricViewController : ActivePlayerListener,
    StatusBarColorMonitor.OnColorChangeListener {

    var isPlaying: Boolean = false
        private set

    var activePackage: String = ""
        private set

    @SuppressLint("StaticFieldLeak")
    var statusBarViewManager: StatusBarViewManager? = null

    init {
        StatusBarColorMonitor.register(this)
    }

    override fun onActiveProviderChanged(providerInfo: ProviderInfo?) {
        YLog.debug("activeProviderChanged: $providerInfo")

        if (providerInfo == null) {
            activePackage = ""
            LyricPrefs.activePackageName = null

            callView {
                it.logoView.providerLogo = null
                statusBarViewManager?.updateLyricStyle(LyricPrefs.getLyricStyle())
                it.updateSong(null)
                it.setPlaying(false)
            }
            return
        }

        val packageName = providerInfo.playerPackageName
        activePackage = packageName
        LyricPrefs.activePackageName = packageName

        callView {
            it.logoView.providerLogo = providerInfo.logo

            val style = LyricPrefs.getLyricStyle()
            statusBarViewManager?.updateLyricStyle(style)
            it.updateVisibility()
        }
    }

    override fun onSongChanged(song: Song?) {
        YLog.debug("songChanged: $song")
        callView {
            it.updateSong(song)
        }
    }

    override fun onPlaybackStateChanged(isPlaying: Boolean) {
        YLog.debug("playbackStateChanged: $isPlaying")
        this.isPlaying = isPlaying
        callView {
            it.setPlaying(isPlaying)
        }
    }

    override fun onPositionChanged(position: Long) {
        callView {
            it.updatePosition(position)
        }
    }

    override fun onSeekTo(position: Long) {
        callView {
            it.seekTo(position)
        }
    }

    override fun onPostText(text: String?) {
        callView {
            it.updateText(text)
        }
    }

    private inline fun callView(crossinline action: (LyricView) -> Unit) {
        val view = statusBarViewManager?.lyricView ?: return
        if (!view.isAttachedToWindow) return

        val isMainThread = Looper.myLooper() == Looper.getMainLooper()

        if (isMainThread) {
            try {
                action(view)
            } catch (t: Throwable) {
                YLog.error("callView action failed", t)
            }
        } else {
            view.post {
                try {
                    if (view.isAttachedToWindow) action(view)
                } catch (t: Throwable) {
                    YLog.error("callView post action failed", t)
                }
            }
        }
    }

    override fun onColorChange(color: StatusColor) {
        callView { it.onColorChange(color) }
    }
}