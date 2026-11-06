package com.example.examprep.util.video;

import com.example.examprep.util.UserPaths;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;

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
import com.example.examprep.data.model.GenerationCache;
import com.example.examprep.data.model.Project;
import com.example.examprep.data.model.slide.PresentationDeck;
import com.example.examprep.data.model.slide.SlideOutline;
import com.example.examprep.ui.MainActivity;
import com.example.examprep.util.ai.AiProvider;
import com.example.examprep.util.ai.AiResponse;
import com.example.examprep.util.ai.OpenAiClient;
import com.example.examprep.util.ai.HashUtils;
import com.example.examprep.util.ai.JsonCleaner;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class VideoGenerationWorker extends Worker {

    public static final String KEY_PROJECT_ID = "project_id";
    public static final String KEY_LANGUAGE = "language";
    public static final String KEY_DETAIL_LEVEL = "detail_level";
    public static final String KEY_OUTPUT_VIDEO_PATH = "output_video_path";

    private static final String CHANNEL_ID = "examprep_video_generation_channel";
    private static final int NOTIFICATION_ID = 2002;

    public VideoGenerationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
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

        if (projectId == -1) {
            return Result.failure();
        }

        updateProgressNotification("Initializing video production studio...", 5);

        AppDatabase db = ExamPrepApp.getInstance().getDatabase();
        Project project = db.projectDao().getProjectByIdSync(projectId);
        if (project == null) {
            return Result.failure();
        }

        try {
            // 1. Fetch or generate presentation slide outline
            updateProgressNotification("Generating slide outline and narration scripts with ChatGPT...", 15);
            PresentationDeck deck = fetchOrGenerateDeck(projectId, project, language, detailLevel);
            if (deck == null || deck.getSlides().isEmpty()) {
                return Result.failure();
            }

            // 2. Initialize TextToSpeech engine
            updateProgressNotification("Initializing speech synthesizer...", 25);
            CountDownLatch ttsInitLatch = new CountDownLatch(1);
            AtomicReference<TextToSpeech> ttsRef = new AtomicReference<>();
            Handler mainHandler = new Handler(Looper.getMainLooper());

            mainHandler.post(() -> {
                final TextToSpeech[] holder = new TextToSpeech[1];
                holder[0] = new TextToSpeech(getApplicationContext(), status -> {
                    if (status == TextToSpeech.SUCCESS) {
                        ttsRef.set(holder[0]);
                    }
                    ttsInitLatch.countDown();
                });
            });

            ttsInitLatch.await(10, TimeUnit.SECONDS);
            TextToSpeech tts = ttsRef.get();
            if (tts == null) {
                return Result.failure();
            }

            Locale targetLocale = "bn".equals(language) ? new Locale("bn", "BD") : Locale.US;

            File tempDir = new File(getApplicationContext().getCacheDir(), "video_temp_" + System.currentTimeMillis());
            if (!tempDir.exists()) tempDir.mkdirs();

            List<SlideshowVideoEncoder.SlideMedia> slideMediaList = new ArrayList<>();
            List<SlideOutline> slides = deck.getSlides();
            int totalSlides = slides.size();

            // 3. Render slide images and synthesize audio per slide
            for (int i = 0; i < totalSlides; i++) {
                SlideOutline slide = slides.get(i);
                int slideNum = i + 1;
                int progressPercent = 30 + (int) (((float) slideNum / totalSlides) * 40f);
                updateProgressNotification("Synthesizing audio & slides (" + slideNum + " of " + totalSlides + ")...", progressPercent);

                // A. Render 1280x720 Bitmap
                Bitmap slideBitmap = SlideBitmapRenderer.renderSlideToBitmap(slide, slideNum, totalSlides, project.getName());

                // B. Synthesize Narration WAV
                String notes = slide.getSpeakerNotes();
                if (notes == null || notes.trim().isEmpty()) {
                    notes = slide.getHeader() + ". " + (slide.getSubheader() != null ? slide.getSubheader() : "");
                }

                File audioFile = new File(tempDir, "slide_audio_" + slideNum + ".wav");
                boolean synthOk = TtsNarrationHelper.synthesizeToFileSync(tts, notes, audioFile, targetLocale, 20);

                long durationMs = TtsNarrationHelper.getAudioDurationMs(audioFile);
                if (!synthOk || durationMs < 2000) {
                    durationMs = 4000; // fallback 4 seconds per slide
                } else {
                    durationMs += 800; // 0.8s breathing pause at end of each slide
                }

                slideMediaList.add(new SlideshowVideoEncoder.SlideMedia(slideBitmap, audioFile, durationMs));
            }

            // Shutdown TTS
            mainHandler.post(tts::shutdown);

            // 4. Encode Slideshow to MP4 using Hardware MediaCodec + MediaMuxer
            File outputDir = new File(UserPaths.filesDir(getApplicationContext()), "generated/" + projectId);
            if (!outputDir.exists()) outputDir.mkdirs();

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File finalVideoFile = new File(outputDir, "ExamPrep_Video_" + timestamp + ".mp4");

            updateProgressNotification("Encoding 720p HD MP4 video with hardware MediaCodec...", 75);

            SlideshowVideoEncoder.encodeSlideshow(slideMediaList, finalVideoFile, (currentSlide, total, status) -> {
                int percent = 75 + (int) (((float) currentSlide / total) * 20f);
                updateProgressNotification(status, percent);
            });

            // 5. Clean up temporary bitmaps & WAV files
            for (SlideshowVideoEncoder.SlideMedia sm : slideMediaList) {
                if (sm.bitmap != null && !sm.bitmap.isRecycled()) {
                    sm.bitmap.recycle();
                }
            }
            deleteRecursive(tempDir);

            // 6. Notify complete
            postCompleteNotification(project.getName(), finalVideoFile);

            return Result.success(new Data.Builder()
                    .putString(KEY_OUTPUT_VIDEO_PATH, finalVideoFile.getAbsolutePath())
                    .build());

        } catch (Exception e) {
            updateProgressNotification("Video production failed: " + e.getMessage(), 0);
            return Result.failure();
        }
    }

    private PresentationDeck fetchOrGenerateDeck(long projectId, Project project, String language, String detailLevel) throws Exception {
        AppDatabase db = ExamPrepApp.getInstance().getDatabase();
        GenerationCache cached = db.generationCacheDao().getLatestCachedResult(projectId, "presentation_outline");
        if (cached != null && cached.getResultJson() != null) {
            return PresentationDeck.fromJson(cached.getResultJson());
        }

        // Build deck with AI
        AiProvider aiProvider = new OpenAiClient(getApplicationContext());
        String langName = language.equals("bn") ? "Bengali (বাংলা)" : "English";
        int targetSlideCount = "summary".equals(detailLevel) ? 6 : ("exhaustive".equals(detailLevel) ? 14 : 9);

        String systemInstruction = "You are an expert presentation designer and university lecturer. " +
                "Generate a professional, structured presentation deck covering all key concepts from the uploaded study material. " +
                "Language must be " + langName + ". Target slide count: approximately " + targetSlideCount + " slides. " +
                "IMPORTANT: For every slide, provide a comprehensive, engaging 'speaker_notes' lecture script. " +
                "Output strictly JSON with this schema: " +
                "{\"presentation_title\":\"Title\",\"subtitle\":\"Review\",\"language\":\"" + language + "\",\"total_slides\":" + targetSlideCount + "," +
                "\"slides\":[{\"slide_number\":1,\"layout\":\"title\",\"header\":\"Title\",\"subheader\":\"Topic Review\",\"speaker_notes\":\"Welcome to this review session...\"}]}";

        // Combine text from files
        List<com.example.examprep.data.model.ProjectFile> files = db.projectFileDao().getFilesForProjectSync(projectId);
        StringBuilder sb = new StringBuilder();
        for (com.example.examprep.data.model.ProjectFile f : files) {
            String t = f.getExtractedText() != null ? f.getExtractedText() : f.getOcrText();
            if (t != null && !t.trim().isEmpty()) {
                sb.append("--- File: ").append(f.getFileName()).append(" ---\n").append(t).append("\n\n");
            }
        }

        String userPrompt = "Build the structured presentation deck with lecture narration speaker notes in " + langName + " now.";

        AtomicReference<AiResponse> respRef = new AtomicReference<>(null);
        AtomicReference<String> errorRef = new AtomicReference<>(null);
        CountDownLatch latch = new CountDownLatch(1);

        aiProvider.generateStructuredText(systemInstruction, sb.toString(), userPrompt, new AiProvider.Callback() {
            @Override
            public void onProgress(String statusMessage) {
                updateProgressNotification(statusMessage, 20);
            }

            @Override
            public void onSuccess(@NonNull AiResponse response) {
                respRef.set(response);
                latch.countDown();
            }

            @Override
            public void onError(@NonNull String errorMessage, Throwable error) {
                errorRef.set(errorMessage);
                latch.countDown();
            }
        });

        latch.await(3, TimeUnit.MINUTES);
        AiResponse resp = respRef.get();
        if (resp == null) {
            throw new java.io.IOException("AI Generation failed: " + errorRef.get());
        }

        String cleanedJson = JsonCleaner.cleanAndValidateJson(resp.getContent());

        // Cache in Room
        GenerationCache entry = new GenerationCache(
                projectId,
                "presentation_outline",
                HashUtils.sha256(sb.toString() + detailLevel + language)
        );
        entry.setResultJson(cleanedJson);
        entry.setTokenInputCount((int) resp.getTokenUsage().getInputTokens());
        entry.setTokenOutputCount((int) resp.getTokenUsage().getOutputTokens());
        entry.setCostUsd(resp.getTokenUsage().getCostUsd());
        entry.setCreatedAt(new Date());
        db.generationCacheDao().insert(entry);

        return PresentationDeck.fromJson(cleanedJson);
    }

    private void updateProgressNotification(String statusText, int progressPercent) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                .setContentTitle("ExamPrep AI • Video Studio")
                .setContentText(statusText)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, progressPercent, progressPercent == 0);

        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(getApplicationContext(), 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        builder.setContentIntent(pi);

        setForegroundAsync(new ForegroundInfo(NOTIFICATION_ID, builder.build()));
    }

    private void postCompleteNotification(String projectName, File videoFile) {
        NotificationManager nm = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        Uri uri = FileProvider.getUriForFile(
                getApplicationContext(),
                getApplicationContext().getPackageName() + ".fileprovider",
                videoFile
        );

        Intent playIntent = new Intent(Intent.ACTION_VIEW);
        playIntent.setDataAndType(uri, "video/mp4");
        playIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        PendingIntent playPi = PendingIntent.getActivity(getApplicationContext(), (int) System.currentTimeMillis(), playIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        long sizeMb = videoFile.length() / (1024 * 1024);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                .setContentTitle("🎬 Narrated Video Ready!")
                .setContentText(projectName + " • " + sizeMb + " MB MP4 Video Produced")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setAutoCancel(true)
                .setContentIntent(playPi)
                .addAction(android.R.drawable.ic_media_play, "Play Video", playPi);

        nm.notify((int) System.currentTimeMillis(), builder.build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "ExamPrep Video Production",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Background rendering and encoding for narrated videos");
            NotificationManager nm = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        fileOrDirectory.delete();
    }
}
