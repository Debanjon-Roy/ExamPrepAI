package com.example.examprep.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.File;

/**
 * Single place that answers "where does the CURRENT signed-in user keep their stuff?".
 * Every per-user store (Room DB, files, SharedPreferences) is keyed by the Firebase UID,
 * so two Google accounts on the same phone never see each other's data.
 */
public final class UserPaths {

    /** Used only when nobody is signed in (e.g. a worker finishing right after logout). */
    public static final String SIGNED_OUT = "signed_out";

    private UserPaths() {}

    public static String currentUid() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user != null ? user.getUid() : SIGNED_OUT;
    }

    /** filesDir/users/<uid>/  (contains projects/ and generated/) */
    public static File filesDir(Context context) {
        return filesDir(context, currentUid());
    }

    public static File filesDir(Context context, String uid) {
        File dir = new File(context.getApplicationContext().getFilesDir(), "users/" + uid);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static String prefsName() {
        return "prefs_" + currentUid();
    }

    /** Per-user SharedPreferences (language, etc.). */
    public static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(prefsName(), Context.MODE_PRIVATE);
    }
}
