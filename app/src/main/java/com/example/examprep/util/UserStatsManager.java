package com.example.examprep.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import com.example.examprep.ExamPrepApp;

import java.io.File;

public class UserStatsManager {

    private static final String PREFS_NAME = "user_activity_stats";
    private static final String KEY_TOTAL_TIME_MS = "total_time_devoted_ms";
    private static final String KEY_SESSION_COUNT = "session_count";

    private static UserStatsManager instance;
    private final SharedPreferences prefs;
    private long sessionStartElapsed = 0;

    private UserStatsManager(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized UserStatsManager getInstance(Context context) {
        if (instance == null) {
            instance = new UserStatsManager(context);
        }
        return instance;
    }

    public void onSessionStart() {
        sessionStartElapsed = SystemClock.elapsedRealtime();
        int sessions = prefs.getInt(KEY_SESSION_COUNT, 0) + 1;
        prefs.edit().putInt(KEY_SESSION_COUNT, sessions).apply();
    }

    public void onSessionPause() {
        if (sessionStartElapsed > 0) {
            long duration = SystemClock.elapsedRealtime() - sessionStartElapsed;
            if (duration > 0) {
                long total = prefs.getLong(KEY_TOTAL_TIME_MS, 0) + duration;
                prefs.edit().putLong(KEY_TOTAL_TIME_MS, total).apply();
            }
            sessionStartElapsed = SystemClock.elapsedRealtime();
        }
    }

    public long getTotalTimeDevotedMs() {
        long recorded = prefs.getLong(KEY_TOTAL_TIME_MS, 0);
        if (sessionStartElapsed > 0) {
            long currentSession = SystemClock.elapsedRealtime() - sessionStartElapsed;
            if (currentSession > 0) {
                return recorded + currentSession;
            }
        }
        return recorded;
    }

    public String getFormattedTimeDevoted() {
        long totalMs = getTotalTimeDevotedMs();
        long minutes = totalMs / (1000 * 60);
        long hours = minutes / 60;
        long remMinutes = minutes % 60;

        if (hours > 0) {
            return hours + "h " + remMinutes + "m";
        } else if (minutes > 0) {
            return minutes + " mins";
        } else {
            return "< 1 min";
        }
    }

    public int getSessionCount() {
        return prefs.getInt(KEY_SESSION_COUNT, 1);
    }

    public int getWorksGeneratedCount(Context context) {
        int dbCount = 0;
        try {
            dbCount = ExamPrepApp.getInstance().getDatabase().generationCacheDao().getTotalWorksGenerated();
        } catch (Exception ignored) {}

        int fileCount = 0;
        try {
            File genDir = new File(context.getFilesDir(), "generated");
            if (genDir.exists()) {
                File[] projects = genDir.listFiles();
                if (projects != null) {
                    for (File p : projects) {
                        if (p.isDirectory()) {
                            File[] files = p.listFiles();
                            if (files != null) {
                                fileCount += files.length;
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        return Math.max(dbCount, fileCount);
    }
}
