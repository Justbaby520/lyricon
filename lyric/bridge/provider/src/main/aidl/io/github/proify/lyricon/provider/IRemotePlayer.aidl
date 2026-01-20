package io.github.proify.lyricon.provider;

import android.os.SharedMemory;
import io.github.proify.lyricon.lyric.model.Song;

interface IRemotePlayer {
    void setSong(in byte[] song);
    void setPlaybackState(boolean isPlaying);
    void seekTo(long position);
    void sendText(String text);
    void setPositionUpdateInterval(int interval);
    void setDisplayTranslation(boolean isDisplayTranslation);
    SharedMemory getPositionMemory();
}