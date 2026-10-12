package com.arrowescape.pro;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;

/** Quiet, offline original music. Never plays outside the foreground app. */
final class AmbientMusic {
    private final Context context;
    private final AudioManager audio;
    private MediaPlayer player;
    private AudioFocusRequest request;
    private boolean active, enabled = true;
    private final AudioManager.OnAudioFocusChangeListener listener = change -> {
        if (change == AudioManager.AUDIOFOCUS_GAIN) play();
        else pausePlayer();
    };
    AmbientMusic(Context context) {
        this.context = context;
        audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }
    void setEnabled(boolean value) { enabled = value; if (active && enabled) resume(); else suspend(); }
    void resume() {
        active = true;
        if (!enabled || audio == null) return;
        try {
            int result;
            if (Build.VERSION.SDK_INT >= 26) {
                if (request == null) request = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setOnAudioFocusChangeListener(listener).setWillPauseWhenDucked(true).build();
                result = audio.requestAudioFocus(request);
            } else result = audio.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) play();
        } catch (RuntimeException ignored) { pausePlayer(); }
    }
    private void play() {
        if (!active || !enabled) return;
        try {
            if (player == null) {
                player = MediaPlayer.create(context, R.raw.quiet_trail);
                if (player == null) return;
                player.setLooping(true);
                player.setVolume(.22f, .22f);
                player.setOnErrorListener((p, what, extra) -> { releasePlayer(); return true; });
            }
            if (!player.isPlaying()) player.start();
        } catch (RuntimeException ignored) { releasePlayer(); }
    }
    private void pausePlayer() { try { if (player != null && player.isPlaying()) player.pause(); } catch (RuntimeException ignored) { releasePlayer(); } }
    void suspend() {
        active = false;
        pausePlayer();
        if (audio != null) {
            if (Build.VERSION.SDK_INT >= 26 && request != null) audio.abandonAudioFocusRequest(request);
            else audio.abandonAudioFocus(listener);
        }
    }
    private void releasePlayer() { if (player != null) { player.release(); player = null; } }
    void release() { suspend(); releasePlayer(); }
}
