package com.arrowescape.pro;

import android.content.Context;
import android.content.SharedPreferences;

final class PreferenceWalletStorage implements Wallet.Storage {
    private final SharedPreferences prefs;
    PreferenceWalletStorage(Context context) {
        // Preserve V12–V15 coins, daily-claim date and streak.
        prefs = context.getSharedPreferences("arrow_escape_pro", Context.MODE_PRIVATE);
    }
    public Wallet.State load() {
        Wallet.State s = new Wallet.State();
        s.coins = prefs.getInt("coins", Wallet.STARTER);
        s.streak = prefs.getInt("streak", 0);
        s.dailyDay = prefs.getLong("reward_day", -1);
        s.levelToken = prefs.getString("wallet_level_token", "");
        s.adToken = prefs.getString("wallet_ad_token", "");
        s.initialized = prefs.getBoolean("wallet_v16_ready", false);
        return s;
    }
    public boolean save(Wallet.State s) {
        return prefs.edit().putInt("coins", s.coins).putInt("streak", s.streak)
            .putLong("reward_day", s.dailyDay).putString("wallet_level_token", s.levelToken)
            .putString("wallet_ad_token", s.adToken).putBoolean("wallet_v16_ready", s.initialized).commit();
    }
}
