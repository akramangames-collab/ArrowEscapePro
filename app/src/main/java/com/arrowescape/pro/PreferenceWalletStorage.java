package com.arrowescape.pro;

import android.content.Context;
import android.content.SharedPreferences;

final class PreferenceWalletStorage implements Wallet.Storage {
    private final SharedPreferences prefs;
    PreferenceWalletStorage(Context context) {
        prefs = context.getSharedPreferences("arrow_escape_pro", Context.MODE_PRIVATE);
    }
    public Wallet.State load() {
        Wallet.State s = new Wallet.State();
        s.coins = prefs.getInt("coins", Wallet.STARTER);
        s.streak = prefs.getInt("streak", 0);
        s.dailyDay = prefs.getLong("reward_day", -1);
        s.challengeDay = prefs.getLong("daily_challenge_reward_day", -1);
        s.levelToken = prefs.getString("wallet_level_token", "");
        s.adToken = prefs.getString("wallet_ad_token", "");
        s.treasureClaims = prefs.getString("wallet_treasure_claims", "");
        s.initialized = prefs.getBoolean("wallet_v16_ready", false);
        return s;
    }
    public boolean save(Wallet.State s) {
        return prefs.edit().putInt("coins", s.coins).putInt("streak", s.streak)
            .putLong("reward_day", s.dailyDay).putLong("daily_challenge_reward_day", s.challengeDay)
            .putString("wallet_level_token", s.levelToken).putString("wallet_ad_token", s.adToken)
            .putString("wallet_treasure_claims", s.treasureClaims == null ? "" : s.treasureClaims)
            .putBoolean("wallet_v16_ready", s.initialized).commit();
    }
}
