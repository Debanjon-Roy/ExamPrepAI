package com.example.examprep.util.pdf;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.examprep.ExamPrepApp;
import com.example.examprep.R;
import com.example.examprep.data.db.AppDatabase;
import com.example.examprep.data.model.Project;
import com.example.examprep.data.model.ProjectFile;
import com.example.examprep.ui.MainActivity;
import com.example.examprep.util.ai.AiProvider;
import com.example.examprep.util.ai.AiResponse;
import com.example.examprep.util.ai.OpenAiClient;
import com.example.examprep.util.ai.GenerationManager;
import com.example.examprep.util.ai.HashUtils;
import com.example.examprep.util.ai.JsonCleaner;
import com.example.examprep.util.ai.TokenUsage;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class StudyGuideWorker extends Worker {

    public static final String KEY_PROJECT_ID = "project_id";
    public static final String KEY_LANGUAGE = "language";
    public static final String KEY_DETAIL_LEVEL = "detail_level";
    public static final String KEY_VERIFY = "verify_completeness";
    public static final String KEY_OUTPUT_PDF_PATH = "output_pdf_path";

    private static final String CHANNEL_ID = "examprep_generation_channel";
    private static final int NOTIFICATION_ID = 1001;

    public StudyGuideWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        createNotificationChannel();
    }

    @NonNull
    @Override
    public Result doWork() {
        long projectId = getInputData().getLong(KEY_PROJECT_ID, -1);
        String language = getInputData().getString(KEY_LANGUAGE);
        if (language == null) language = "en";
        String detailLevel = getInputData().getString(KEY_DETAIL_LEVEL);
        if (detailLevel == null) detailLevel = "standard";
        boolean verify = getInputData().getBoolean(KEY_VERIFY, true);

        if (projectId == -1) {
            return Result.failure();
        }

        NotificationManager nm = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        updateProgressNotification("Starting study guide generation...", 10);

        AppDatabase db = ExamPrepApp.getInstance().getDatabase();
        Project project = db.projectDao().getProjectByIdSync(projectId);
        if (project == null) {
            return Result.failure();
        }

        List<ProjectFile> files = db.projectFileDao().getFilesForProjectSync(projectId);
        StringBuilder sb = new StringBuilder();
        for (ProjectFile f : files) {
            String text = f.getExtractedText() != null ? f.getExtractedText() : f.getOcrText();
            if (text != null && !text.trim().isEmpty()) {
                sb.append("=== Source: ").append(f.getFileName()).append(" ===\n").append(text.trim()).append("\n\n");
            }
        }
        String materials = sb.toString().trim();
        if (materials.isEmpty()) {
            return Result.failure();
        }

        updateProgressNotification("Analyzing materials with ChatGPT (GPT-4o mini)...", 30);

        String langName = language.equals("bn") ? "Bengali (বাংলা)" : "English";
        String systemPrompt = "You are ExamPrep AI. Create a complete, exhaustive study guide PDF outline covering every single topic found in the source materials. " +
                "Language must be " + langName + ". Detail level: " + detailLevel + ". " +
                "Do NOT invent facts outside the materials; clearly label anything added from general knowledge under 'general_knowledge_additions'. " +
                "Output strictly JSON with this schema: " +
                "{\"title\":\"Title\",\"subtitle\":\"Subtitle\",\"topics_covered\":[\"topic 1\",\"topic 2\"]," +
                "\"sections\":[{\"heading\":\"1. Heading\",\"summary\":\"Summary text\",\"key_concepts\":[{\"concept\":\"Term\",\"explanation\":\"Meaning\"}]," +
                "\"content_paragraphs\":[\"Paragraph 1\",\"Paragraph 2\"],\"table\":{\"headers\":[\"Col1\",\"Col2\"],\"rows\":[[\"A\",\"B\"]]}," +
                "\"important_notes\":[\"Note 1\"],\"general_knowledge_additions\":[]}]}";

        String userPrompt = "Generate the complete study guide JSON now based on all provided materials.";

        OpenAiClient client = new OpenAiClient(getApplicationContext());
        AtomicReference<String> guideJsonRef = new AtomicReference<>(null);
        AtomicReference<String> errorRef = new AtomicReference<>(null);
        CountDownLatch latch = new CountDownLatch(1);

        client.generateStructuredText(systemPrompt, materials, userPrompt, new AiProvider.Callback() {
            @Override
            public void onProgress(String statusMessage) {
                updateProgressNotification(statusMessage, 45);
            }

            @Override
            public void onSuccess(@NonNull AiResponse response) {
                guideJsonRef.set(response.getContent());
                latch.countDown();
            }

            @Override
            public void onError(@NonNull String errorMessage, Throwable error) {
                errorRef.set(errorMessage);
                latch.countDown();
            }
        });

        try {
            latch.await(3, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            return Result.failure();
        }

        String initialJson = guideJsonRef.get();
        if (initialJson == null) {
            return Result.failure(new Data.Builder().putString("error", errorRef.get()).build());
        }

        // Verification Step (Gap filling)
        if (verify) {
            updateProgressNotification("Verifying completeness against source materials...", 65);
            String verifiedJson = runVerificationStep(client, materials, initialJson, langName);
            if (verifiedJson != null) {
                initialJson = verifiedJson;
            }
        }

        // Render PDF with iText 7
        updateProgressNotification("Rendering styled PDF with iText...", 85);
        try {
            File pdfFile = StudyGuidePdfGenerator.generatePdf(
                    getApplicationContext(),
                    projectId,
                    project.getName(),
                    initialJson,
                    language
            );

            showCompletionNotification(project.getName(), pdfFile);

            return Result.success(new Data.Builder()
                    .putString(KEY_OUTPUT_PDF_PATH, pdfFile.getAbsolutePath())
                    .build());
        } catch (Exception e) {
            return Result.failure(new Data.Builder().putString("error", "PDF generation failed: " + e.getMessage()).build());
        }
    }

    private String runVerificationStep(OpenAiClient client, String materials, String existingJson, String langName) {
        String verifySystem = "You are a quality-assurance verifier for study guides. Compare the generated study guide against the source materials. " +
                "Find any topics, theorems, or definitions present in the source that were omitted. " +
                "Return JSON: {\"gaps_found\": true/false, \"missing_sections\": [{\"heading\":\"...\",\"summary\":\"...\",\"key_concepts\":[],\"content_paragraphs\":[\"...\"],\"important_notes\":[]}]}";

        String verifyUser = "Source Materials:\n" + materials + "\n\nExisting Guide Outline:\n" + existingJson +
                "\n\nIdentify missed topics in " + langName + " and fill gaps.";

        CountDownLatch verifyLatch = new CountDownLatch(1);
        AtomicReference<String> verifyResultRef = new AtomicReference<>(null);

        client.generateStructuredText(verifySystem, "", verifyUser, new AiProvider.Callback() {
            @Override
            public void onProgress(String statusMessage) {}

            @Override
            public void onSuccess(@NonNull AiResponse response) {
                verifyResultRef.set(response.getContent());
                verifyLatch.countDown();
            }

            @Override
            public void onError(@NonNull String errorMessage, Throwable error) {
                verifyLatch.countDown();
            }
        });

        try {
            verifyLatch.await(90, TimeUnit.SECONDS);
            String vJson = verifyResultRef.get();
            if (vJson != null) {
                JsonObject root = JsonParser.parseString(existingJson).getAsJsonObject();
                JsonObject vObj = JsonParser.parseString(vJson).getAsJsonObject();

                if (vObj.has("gaps_found") && vObj.get("gaps_found").getAsBoolean() && vObj.has("missing_sections")) {
                    JsonArray missing = vObj.getAsJsonArray("missing_sections");
                    if (missing.size() > 0 && root.has("sections")) {
                        JsonArray sections = root.getAsJsonArray("sections");
                        for (JsonElement m : missing) {
                            sections.add(m);
                        }
                        return root.toString();
                    }
                }
            }
        } catch (Exception ignored) {}

        return existingJson;
    }

    private void updateProgressNotification(String message, int progress) {
        NotificationManager nm = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_agenda)
                .setContentTitle("ExamPrep AI")
                .setContentText(message)
                .setProgress(100, progress, false)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        nm.notify(NOTIFICATION_ID, builder.build());
    }

    private void showCompletionNotification(String projectName, File pdfFile) {
        NotificationManager nm = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);

        Uri pdfUri = FileProvider.getUriForFile(
                getApplicationContext(),
                getApplicationContext().getPackageName() + ".fileprovider",
                pdfFile
        );

        Intent viewIntent = new Intent(Intent.ACTION_VIEW);
        viewIntent.setDataAndType(pdfUri, "application/pdf");
        viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent pi = PendingIntent.getActivity(
                getApplicationContext(),
                0,
                viewIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_agenda)
                .setContentTitle("Study Guide Ready")
                .setContentText(projectName + " PDF is ready. Tap to view.")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        nm.notify(NOTIFICATION_ID, builder.build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Study Guide Generation",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Shows background progress when generating study guides");
            NotificationManager nm = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }
}
