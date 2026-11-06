package com.example.examprep.data.db;

import android.content.Context;
import android.util.Log;

import com.example.examprep.util.UserPaths;

import java.io.File;

/**
 * Before this fix all accounts shared one database ("examprep_db") and one set of folders.
 * The first account that signs in after updating "adopts" that old data, so nothing is lost.
 * Every other account starts clean.
 */
final class LegacyMigration {

    private static final String TAG = "LegacyMigration";
    static final String LEGACY_DB = "examprep_db";
    private static final String[] DB_SUFFIXES = {"", "-wal", "-shm", "-journal"};

    private LegacyMigration() {}

    /** @return true if the old database was handed over to this user. */
    static boolean claim(Context ctx, String uid) {
        if (UserPaths.SIGNED_OUT.equals(uid)) return false;

        File marker = new File(ctx.getFilesDir(), ".legacy_claimed");
        if (marker.exists()) return false;

        boolean dbClaimed = false;
        File legacyDb = ctx.getDatabasePath(LEGACY_DB);
        File targetDb = ctx.getDatabasePath(AppDatabase.dbNameFor(uid));
        if (legacyDb.exists() && !targetDb.exists()) {
            for (String suffix : DB_SUFFIXES) {
                File from = new File(legacyDb.getPath() + suffix);
                File to = new File(targetDb.getPath() + suffix);
                if (from.exists() && !from.renameTo(to)) {
                    Log.w(TAG, "Could not move " + from.getName());
                }
            }
            dbClaimed = true;
        }

        File userDir = UserPaths.filesDir(ctx);
        for (String folder : new String[]{"projects", "generated"}) {
            File from = new File(ctx.getFilesDir(), folder);
            File to = new File(userDir, folder);
            if (from.exists() && !to.exists() && !from.renameTo(to)) {
                Log.w(TAG, "Could not move folder " + folder);
            }
        }

        try {
            marker.createNewFile();
        } catch (Exception e) {
            Log.w(TAG, "Could not write marker", e);
        }
        return dbClaimed;
    }

    /** Uploaded files moved folders, so the absolute paths stored in the DB must follow. */
    static void fixFilePaths(Context ctx, AppDatabase db) {
        String oldPrefix = new File(ctx.getFilesDir(), "projects").getAbsolutePath();
        String newPrefix = new File(UserPaths.filesDir(ctx), "projects").getAbsolutePath();
        db.getOpenHelper().getWritableDatabase().execSQL(
                "UPDATE project_files SET fileUri = REPLACE(fileUri, ?, ?)",
                new Object[]{oldPrefix, newPrefix});
    }
}
