package com.example.examprep.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Manages secure storage of sensitive data like API keys using EncryptedSharedPreferences.
 * Never logs or exposes the API key.
 */
public class SecurePrefs {

    private static final String TAG = "SecurePrefs";
    private static final String PREFS_FILE = "secure_prefs";
    private static final String KEY_API_KEY = "anthropic_api_key";
    private static final String KEY_DAILY_CAP = "daily_spending_cap";
    private static final String KEY_DAILY_SPENT = "daily_spent";
    private static final String KEY_LAST_SPEND_DATE = "last_spend_date";

    private final SharedPreferences prefs;

    public SecurePrefs(Context context) {
        SharedPreferences temp = null;
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            temp = EncryptedSharedPreferences.create(
                    context,
                    PREFS_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Failed to create encrypted prefs", e);
            // Fallback to regular prefs (should not happen on API 26+)
            temp = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
        }
        prefs = temp;
    }

    public void setApiKey(String apiKey) {
        prefs.edit().putString(KEY_API_KEY, apiKey).apply();
    }

    public String getApiKey() {
        return prefs.getString(KEY_API_KEY, null);
    }

    public boolean hasApiKey() {
        String key = getApiKey();
        return key != null && !key.trim().isEmpty();
    }

    public void setDailyCap(double capUsd) {
        prefs.edit().putString(KEY_DAILY_CAP, String.valueOf(capUsd)).apply();
    }

    public double getDailyCap() {
        String cap = prefs.getString(KEY_DAILY_CAP, "1.00");
        try {
            return Double.parseDouble(cap);
        } catch (NumberFormatException e) {
            return 1.00;
        }
    }

    public void addSpending(double amountUsd) {
        String today = java.time.LocalDate.now().toString();
        String lastDate = prefs.getString(KEY_LAST_SPEND_DATE, "");
        
        double currentSpent = 0;
        if (today.equals(lastDate)) {
            String spentStr = prefs.getString(KEY_DAILY_SPENT, "0");
            try {
                currentSpent = Double.parseDouble(spentStr);
            } catch (NumberFormatException ignored) {}
        }
        
        currentSpent += amountUsd;
        prefs.edit()
                .putString(KEY_DAILY_SPENT, String.valueOf(currentSpent))
                .putString(KEY_LAST_SPEND_DATE, today)
                .apply();
    }

    public double getTodaySpending() {
        String today = java.time.LocalDate.now().toString();
        String lastDate = prefs.getString(KEY_LAST_SPEND_DATE, "");
        
        if (!today.equals(lastDate)) {
            return 0;
        }
        
        String spentStr = prefs.getString(KEY_DAILY_SPENT, "0");
        try {
            return Double.parseDouble(spentStr);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public boolean isDailyCapReached() {
        return getTodaySpending() >= getDailyCap();
    }

    public void clearApiKey() {
        prefs.edit().remove(KEY_API_KEY).apply();
    }
}
