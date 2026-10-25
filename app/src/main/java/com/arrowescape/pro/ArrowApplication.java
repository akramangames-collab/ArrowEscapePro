package com.arrowescape.pro;

import android.app.Application;
import com.google.android.gms.games.PlayGamesSdk;

public final class ArrowApplication extends Application {
    public static boolean playGamesReady;
    @Override public void onCreate() {
        super.onCreate();
        if (!BuildConfig.PLAY_GAMES_CONFIGURED) return;
        try {
            PlayGamesSdk.initialize(this);
            playGamesReady=true;
        } catch (IllegalStateException | IllegalArgumentException error) {
            // Configuration failures must never prevent the puzzle from opening.
            playGamesReady=false;
        }
    }
}
