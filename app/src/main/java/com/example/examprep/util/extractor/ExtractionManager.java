package com.example.examprep.util.extractor;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.example.examprep.ExamPrepApp;
import com.example.examprep.data.db.AppDatabase;
import com.example.examprep.data.db.ProjectFileDao;
import com.example.examprep.data.model.ProjectFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExtractionManager {

    public interface ExtractionCallback {
        void onProgress(long fileId, String statusMessage);
        void onComplete(long fileId, boolean success, String previewSnippet);
    }

    private static final ExecutorService executor = Executors.newFixedThreadPool(2);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static void extractFileAsync(@NonNull Context context, @NonNull ProjectFile projectFile,
                                        @NonNull ExtractionCallback callback) {
        executor.execute(() -> {
            postProgress(callback, projectFile.getId(), "Extracting text...");

            File localFile = new File(projectFile.getFileUri());
            TextExtractorResult result;

            String fileName = projectFile.getFileName().toLowerCase();
            String mimeType = projectFile.getMimeType().toLowerCase();

            try {
                if (fileName.endsWith(".pdf") || mimeType.contains("pdf")) {
                    postProgress(callback, projectFile.getId(), "Parsing PDF pages...");
                    result = PdfExtractor.extract(localFile);
                } else if (fileName.endsWith(".pptx") || mimeType.contains("presentation")) {
                    postProgress(callback, projectFile.getId(), "Parsing PowerPoint slides...");
                    result = PptxExtractor.extract(localFile);
                } else if (fileName.endsWith(".xlsx") || mimeType.contains("sheet")) {
                    postProgress(callback, projectFile.getId(), "Parsing Excel workbook...");
                    result = XlsxExtractor.extract(localFile);
                } else if (fileName.endsWith(".csv") || mimeType.contains("csv")) {
                    postProgress(callback, projectFile.getId(), "Reading CSV data...");
                    try (InputStream is = new FileInputStream(localFile)) {
                        result = CsvExtractor.extract(is);
                    }
                } else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")
                        || fileName.endsWith(".png") || mimeType.startsWith("image/")) {
                    postProgress(callback, projectFile.getId(), "Running on-device OCR...");
                    result = ImageOcrExtractor.extractFromUri(context, Uri.fromFile(localFile));
                } else {
                    result = TextExtractorResult.failure("Unsupported file format: " + fileName);
                }
            } catch (Exception e) {
                result = TextExtractorResult.failure("Extraction error: " + e.getMessage());
            }

            // Update database
            AppDatabase db = ExamPrepApp.getInstance().getDatabase();
            ProjectFileDao fileDao = db.projectFileDao();

            boolean success = result.isSuccess() && result.getText() != null && !result.getText().isEmpty();
            String status = success ? "done" : "failed";

            if (success) {
                if (result.getMethod() == TextExtractorResult.Method.OCR) {
                    fileDao.updateOcrText(projectFile.getId(), result.getText(), status);
                } else {
                    fileDao.updateExtractedText(projectFile.getId(), result.getText(), status);
                }
            } else {
                fileDao.updateExtractedText(projectFile.getId(), null, status);
            }

            final boolean finalSuccess = success;
            final String snippet = success
                    ? (result.getText().length() > 120 ? result.getText().substring(0, 120) + "..." : result.getText())
                    : result.getErrorMessage();

            mainHandler.post(() -> callback.onComplete(projectFile.getId(), finalSuccess, snippet));
        });
    }

    private static void postProgress(ExtractionCallback callback, long fileId, String message) {
        mainHandler.post(() -> callback.onProgress(fileId, message));
    }
}
