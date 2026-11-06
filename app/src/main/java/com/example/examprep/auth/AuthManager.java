package com.example.examprep.auth;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.exceptions.ClearCredentialException;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/** Small helper so login state checks and logout live in one place. */
public final class AuthManager {

    private AuthManager() {}

    public static boolean isLoggedIn() {
        return getCurrentUser() != null;
    }

    public static FirebaseUser getCurrentUser() {
        return FirebaseAuth.getInstance().getCurrentUser();
    }

    /** Signs out of Firebase AND clears the Google credential state, then runs onDone on the main thread. */
    public static void signOut(@NonNull Activity activity, @NonNull Runnable onDone) {
        androidx.work.WorkManager.getInstance(activity).cancelAllWork();
        FirebaseAuth.getInstance().signOut();

        CredentialManager.create(activity).clearCredentialStateAsync(
                new ClearCredentialStateRequest(),
                null,
                ContextCompat.getMainExecutor(activity),
                new CredentialManagerCallback<Void, ClearCredentialException>() {
                    @Override public void onResult(Void result) { onDone.run(); }
                    @Override public void onError(@NonNull ClearCredentialException e) { onDone.run(); }
                });
    }
}
