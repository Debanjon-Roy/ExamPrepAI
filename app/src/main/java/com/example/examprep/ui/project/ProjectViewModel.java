package com.example.examprep.ui.project;

import com.example.examprep.util.UserPaths;

import android.app.Application;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.examprep.ExamPrepApp;
import com.example.examprep.data.db.ProjectDao;
import com.example.examprep.data.db.ProjectFileDao;
import com.example.examprep.data.model.Project;
import com.example.examprep.data.model.ProjectFile;
import com.example.examprep.util.extractor.ExtractionManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProjectViewModel extends AndroidViewModel {

    private final ProjectDao projectDao;
    private final ProjectFileDao fileDao;
    private final LiveData<Project> project;
    private final LiveData<List<ProjectFile>> files;
    private final long projectId;
    private final String ownerUid = UserPaths.currentUid();
    private final File ownerFilesDir;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private final MutableLiveData<String> statusMessage = new MutableLiveData<>();

    public ProjectViewModel(@NonNull Application application, long projectId) {
        super(application);
        this.projectId = projectId;
        ownerFilesDir = UserPaths.filesDir(application);
        projectDao = ((ExamPrepApp) application).getDatabase().projectDao();
        fileDao = ((ExamPrepApp) application).getDatabase().projectFileDao();
        project = projectDao.getProjectLive(projectId);
        files = fileDao.getFilesForProject(projectId);
    }

    public LiveData<Project> getProject() {
        return project;
    }

    public LiveData<List<ProjectFile>> getFiles() {
        return files;
    }

    public LiveData<String> getStatusMessage() {
        return statusMessage;
    }

    public long getProjectId() {
        return projectId;
    }

    public void importFiles(@NonNull List<Uri> uris) {
        ioExecutor.execute(() -> {
            for (Uri uri : uris) {
                importSingleUri(uri);
            }
        });
    }

    public void importCameraPhoto(@NonNull Uri photoUri, @NonNull File photoFile) {
        ioExecutor.execute(() -> {
            if (!photoFile.exists() || photoFile.length() == 0) {
                statusMessage.postValue("Camera photo capture failed");
                return;
            }

            String fileName = photoFile.getName();
            long fileSize = photoFile.length();
            String mimeType = "image/jpeg";

            ProjectFile projectFile = new ProjectFile(
                    projectId,
                    fileName,
                    photoFile.getAbsolutePath(),
                    mimeType,
                    fileSize
            );
            projectFile.setTextExtractionStatus("extracting");
            long id = fileDao.insert(projectFile);
            projectFile.setId(id);

            // Update project timestamp
            projectDao.updateTimestamp(projectId, new Date());
            com.example.examprep.data.cloud.CloudSync.pushProject(ownerUid, projectDao.getProjectByIdSync(projectId));

            // Run OCR
            runExtraction(projectFile);
        });
    }

    private void importSingleUri(Uri uri) {
        ContentResolver cr = getApplication().getContentResolver();
        String displayName = "file_" + System.currentTimeMillis();
        long fileSize = 0;

        try (Cursor cursor = cr.query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (nameIndex != -1) {
                    displayName = cursor.getString(nameIndex);
                }
                if (sizeIndex != -1) {
                    fileSize = cursor.getLong(sizeIndex);
                }
            }
        } catch (Exception ignored) {}

        String mimeType = cr.getType(uri);
        if (mimeType == null) {
            String ext = MimeTypeMap.getFileExtensionFromUrl(displayName);
            if (ext != null) {
                mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase());
            }
            if (mimeType == null) {
                mimeType = "application/octet-stream";
            }
        }

        // Copy file to project folder
        File projectDir = new File(ownerFilesDir, "projects/" + projectId);
        if (!projectDir.exists()) {
            projectDir.mkdirs();
        }

        File destinationFile = new File(projectDir, System.currentTimeMillis() + "_" + displayName);
        try (InputStream is = cr.openInputStream(uri);
             OutputStream os = new FileOutputStream(destinationFile)) {
            if (is == null) return;
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                os.write(buffer, 0, read);
            }
            fileSize = destinationFile.length();
        } catch (Exception e) {
            statusMessage.postValue("Failed to copy file: " + displayName);
            return;
        }

        ProjectFile projectFile = new ProjectFile(
                projectId,
                displayName,
                destinationFile.getAbsolutePath(),
                mimeType,
                fileSize
        );
        projectFile.setTextExtractionStatus("extracting");
        long id = fileDao.insert(projectFile);
        projectFile.setId(id);

        projectDao.updateTimestamp(projectId, new Date());
            com.example.examprep.data.cloud.CloudSync.pushProject(ownerUid, projectDao.getProjectByIdSync(projectId));

        runExtraction(projectFile);
    }

    public void reExtractFile(@NonNull ProjectFile file) {
        ioExecutor.execute(() -> {
            fileDao.updateExtractedText(file.getId(), null, "extracting");
            file.setTextExtractionStatus("extracting");
            runExtraction(file);
        });
    }

    public void deleteFile(@NonNull ProjectFile file) {
        ioExecutor.execute(() -> {
            try {
                File local = new File(file.getFileUri());
                if (local.exists()) {
                    local.delete();
                }
            } catch (Exception ignored) {}
            fileDao.delete(file);
        });
    }

    private void runExtraction(ProjectFile projectFile) {
        ExtractionManager.extractFileAsync(getApplication(), projectFile, new ExtractionManager.ExtractionCallback() {
            @Override
            public void onProgress(long fileId, String message) {
                statusMessage.postValue(message);
            }

            @Override
            public void onComplete(long fileId, boolean success, String previewSnippet) {
                if (success) {
                    statusMessage.postValue("Extracted: " + projectFile.getFileName());
                } else {
                    statusMessage.postValue("Extraction failed for " + projectFile.getFileName());
                }
            }
        });
    }
}
