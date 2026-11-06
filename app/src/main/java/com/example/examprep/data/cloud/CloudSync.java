package com.example.examprep.data.cloud;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.examprep.ExamPrepApp;
import com.example.examprep.data.db.ProjectDao;
import com.example.examprep.data.model.Project;
import com.example.examprep.util.UserPaths;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import com.example.examprep.auth.AuthManager;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Firestore layout (everything lives under the signed-in user's own document):
 *
 *   users/{uid}                    -> profile (name, email, photo, createdAt, lastLoginAt)
 *   users/{uid}/projects/{id}      -> project metadata (name, description, language, ...)
 *
 * Uploaded files and generated PDFs/slides stay on the device (Firestore has a 1 MB
 * document limit), but they are stored in per-user folders, see UserPaths.
 */
public final class CloudSync {

    private static final String TAG = "CloudSync";

    private CloudSync() {}

    private static DocumentReference userDoc(String uid) {
        return FirebaseFirestore.getInstance().collection("users").document(uid);
    }

    private static CollectionReference projectsCol(String uid) {
        return userDoc(uid).collection("projects");
    }

    /** Call once after login: saves the profile and restores projects on a fresh install. */
    public static void onSignedIn(@NonNull Context context) {
        FirebaseUser user = AuthManager.getCurrentUser();
        if (user == null) return;
        saveProfile(user);
        restoreProjectsIfLocalEmpty(user.getUid());
    }

    private static void saveProfile(@NonNull FirebaseUser user) {
        DocumentReference doc = userDoc(user.getUid());

        Map<String, Object> data = new HashMap<>();
        data.put("uid", user.getUid());
        data.put("displayName", user.getDisplayName());
        data.put("email", user.getEmail());
        data.put("photoUrl", user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : null);
        data.put("lastLoginAt", FieldValue.serverTimestamp());

        doc.get().addOnCompleteListener(task -> {
            boolean isNew = !task.isSuccessful() || task.getResult() == null || !task.getResult().exists();
            if (isNew) data.put("createdAt", FieldValue.serverTimestamp());
            doc.set(data, SetOptions.merge())
                    .addOnFailureListener(e -> Log.w(TAG, "Profile save failed", e));
        });
    }

    public static void pushProject(@NonNull Project p) {
        if (!AuthManager.isLoggedIn()) return;
        Map<String, Object> data = new HashMap<>();
        data.put("name", p.getName());
        data.put("description", p.getDescription());
        data.put("language", p.getLanguage());
        data.put("detailLevel", p.getDetailLevel());
        data.put("createdAt", p.getCreatedAt());
        data.put("updatedAt", p.getUpdatedAt());

        projectsCol(UserPaths.currentUid()).document(String.valueOf(p.getId()))
                .set(data, SetOptions.merge())
                .addOnFailureListener(e -> Log.w(TAG, "Project push failed", e));
    }

    public static void deleteProject(long projectId) {
        if (!AuthManager.isLoggedIn()) return;
        projectsCol(UserPaths.currentUid()).document(String.valueOf(projectId))
                .delete()
                .addOnFailureListener(e -> Log.w(TAG, "Project delete failed", e));
    }

    /** New phone / reinstall: bring the project list back from Firestore (files are not restored). */
    private static void restoreProjectsIfLocalEmpty(@NonNull String uid) {
        Executors.newSingleThreadExecutor().execute(() -> {
            ProjectDao dao = ExamPrepApp.getInstance().getDatabase().projectDao();
            if (dao.getProjectCount() > 0) return;

            projectsCol(uid).get().addOnSuccessListener(snapshot -> Executors.newSingleThreadExecutor().execute(() -> {
                // The user may have switched accounts while the request was running.
                if (!uid.equals(UserPaths.currentUid())) return;
                ProjectDao d = ExamPrepApp.getInstance().getDatabase().projectDao();
                if (d.getProjectCount() > 0) return;

                for (DocumentSnapshot doc : snapshot.getDocuments()) {
                    try {
                        String name = doc.getString("name");
                        if (name == null) continue;
                        Date created = doc.getDate("createdAt");
                        Date updated = doc.getDate("updatedAt");
                        String lang = doc.getString("language");
                        String detail = doc.getString("detailLevel");
                        d.insert(new Project(
                                Long.parseLong(doc.getId()),
                                name,
                                doc.getString("description"),
                                created != null ? created : new Date(),
                                updated != null ? updated : new Date(),
                                lang != null ? lang : "en",
                                detail != null ? detail : "standard"));
                    } catch (Exception e) {
                        Log.w(TAG, "Skipping bad project doc " + doc.getId(), e);
                    }
                }
            })).addOnFailureListener(e -> Log.w(TAG, "Project restore failed", e));
        });
    }
}
