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
import com.example.examprep.data.db.AppDatabase;

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
 * Binary files stay in per-user folders, see UserPaths. Newly rendered study-guide
 * source content is backed up separately by StudyGuideBackup under generatedContent.
 */
public final class CloudSync {

    private static final String TAG = "CloudSync";
    private static final java.util.concurrent.ExecutorService IO = Executors.newSingleThreadExecutor();

    private CloudSync() {}

    public static com.google.android.gms.tasks.Task<Void> renameProject(Context context, String uid,
                                                                        long projectId, String name) {
        Context app = context.getApplicationContext();
        return com.google.android.gms.tasks.Tasks.call(IO, () -> {
            if (!uid.equals(UserPaths.currentUid()) || UserPaths.SIGNED_OUT.equals(uid))
                throw new IllegalStateException("Account changed");
            if (name.trim().isEmpty() || name.length() > 120)
                throw new IllegalArgumentException("Enter a project name of up to 120 characters.");
            ProjectDao dao = AppDatabase.getInstance(app, uid).projectDao();
            if (dao.rename(projectId, name.trim(), new Date()) == 0)
                throw new IllegalStateException("Project no longer exists");
            pushProject(uid, dao.getProjectByIdSync(projectId));
            return null;
        });
    }

    public static void renameGeneratedFile(String uid, long projectId, String oldName, String newName) {
        if (!uid.equals(UserPaths.currentUid()) || UserPaths.SIGNED_OUT.equals(uid)) return;
        projectsCol(uid).document(String.valueOf(projectId)).collection("generatedContent")
                .whereEqualTo("fileName", oldName).get().addOnSuccessListener(snapshot -> {
                    if (!uid.equals(UserPaths.currentUid())) return;
                    for (DocumentSnapshot doc : snapshot) doc.getReference().update(
                            "fileName", newName, "customFileName", true)
                            .addOnFailureListener(e -> Log.w(TAG, "Backup filename update failed", e));
                }).addOnFailureListener(e -> Log.w(TAG, "Backup filename lookup failed", e));
    }

    private static DocumentReference userDoc(String uid) {
        return FirebaseFirestore.getInstance().collection("users").document(uid);
    }

    private static CollectionReference projectsCol(String uid) {
        return userDoc(uid).collection("projects");
    }

    /** Call after login: save the profile and merge project metadata. */
    public static void onSignedIn(@NonNull Context context) {
        FirebaseUser user = AuthManager.getCurrentUser();
        if (user == null) return;
        saveProfile(user);
        syncProjects(user.getUid(), ExamPrepApp.getInstance().getDatabase());
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
            if (!task.isSuccessful()) {
                Log.w(TAG, "Profile read failed", task.getException());
                return;
            }
            boolean isNew = task.getResult() != null && !task.getResult().exists();
            if (isNew) data.put("createdAt", FieldValue.serverTimestamp());
            doc.set(data, SetOptions.merge())
                    .addOnFailureListener(e -> Log.w(TAG, "Profile save failed", e));
        });
    }

    public static void pushProject(@NonNull String uid, @androidx.annotation.Nullable Project p) {
        if (p == null) return;
        if (!uid.equals(UserPaths.currentUid()) || UserPaths.SIGNED_OUT.equals(uid)) return;
        Map<String, Object> data = new HashMap<>();
        data.put("name", p.getName());
        data.put("description", p.getDescription());
        data.put("language", p.getLanguage());
        data.put("detailLevel", p.getDetailLevel());
        data.put("createdAt", p.getCreatedAt());
        data.put("updatedAt", p.getUpdatedAt());

        projectsCol(uid).document(String.valueOf(p.getId()))
                .set(data, SetOptions.merge())
                .addOnFailureListener(e -> Log.w(TAG, "Project push failed", e));
    }

    public static void deleteProject(@NonNull String uid, long projectId) {
        if (!uid.equals(UserPaths.currentUid()) || UserPaths.SIGNED_OUT.equals(uid)) return;
        Map<String, Object> tombstone = new HashMap<>();
        tombstone.put("deleted", true);
        tombstone.put("updatedAt", new Date());
        projectsCol(uid).document(String.valueOf(projectId))
                .set(tombstone)
                .addOnFailureListener(e -> Log.w(TAG, "Project delete failed", e));
    }

    /** Merge cloud metadata on login, then upload local-only or newer projects. */
    private static void syncProjects(@NonNull String uid, @NonNull AppDatabase database) {
        projectsCol(uid).get().addOnSuccessListener(snapshot -> IO.execute(() -> {
            if (!uid.equals(UserPaths.currentUid())) return;
            ProjectDao dao = database.projectDao();
            java.util.Set<Long> deleted = new java.util.HashSet<>();
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                try {
                    long id = Long.parseLong(doc.getId());
                    Project local = dao.getProjectByIdSync(id);
                    if (Boolean.TRUE.equals(doc.getBoolean("deleted"))) {
                        deleted.add(id);
                        if (local != null) dao.delete(local);
                        continue;
                    }
                    String name = doc.getString("name");
                    if (name == null) continue;
                    Date created = doc.getDate("createdAt");
                    Date updated = doc.getDate("updatedAt");
                    if (local != null && (updated == null || !updated.after(local.getUpdatedAt()))) continue;
                    String lang = doc.getString("language");
                    String detail = doc.getString("detailLevel");
                    Project remote = new Project(id, name, doc.getString("description"),
                            created != null ? created : new Date(), updated != null ? updated : new Date(),
                            lang != null ? lang : "en", detail != null ? detail : "standard");
                    if (local == null) dao.insert(remote); else dao.update(remote);
                } catch (Exception e) {
                    Log.w(TAG, "Skipping bad project doc " + doc.getId(), e);
                }
            }
            for (Project local : dao.getAllProjectsSync()) {
                if (!deleted.contains(local.getId())) pushProject(uid, local);
            }
        })).addOnFailureListener(e -> Log.w(TAG, "Project sync failed; local data retained", e));
    }
}
