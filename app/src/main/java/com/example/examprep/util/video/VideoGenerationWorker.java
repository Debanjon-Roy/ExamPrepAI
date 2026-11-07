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
    public static final String KEY_ERROR = "error";

    private String ownerUid;
    private AppDatabase ownerDatabase;
    private long lastNotificationAt;

    private static final String CHANNEL_ID = "examprep_video_generation_channel";
    private static final int NOTIFICATION_ID = 2002;

    public VideoGenerationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        createNotificationChannel();
    }

    @NonNull
    @Override
    public Result doWork() {
        ownerUid = getInputData().getString("owner_uid");
        if (ownerUid == null || !ownerUid.equals(UserPaths.currentUid())) return Result.failure();
        ownerDatabase = AppDatabase.getInstance(getApplicationContext(), ownerUid);
        long projectId = getInputData().getLong(KEY_PROJECT_ID, -1);
        String language = getInputData().getString(KEY_LANGUAGE);
        if (language == null) language = "en";
        String detailLevel = getInputData().getString(KEY_DETAIL_LEVEL);
        if (detailLevel == null) detailLevel = "standard";

        if (projectId == -1) {
            return Result.failure();
        }

        updateProgressNotification("Initializing video production studio...", 5);

        AppDatabase db = ownerDatabase;
        Project project = db.projectDao().getProjectByIdSync(projectId);
        if (project == null) {
            return Result.failure();
        }

        final AtomicReference<TextToSpeech> ttsRef = new AtomicReference<>();
        Handler mainHandler = new Handler(Looper.getMainLooper());
        File tempDir = null;
        File finalVideoFile = null;
        boolean complete = false;
        List<SlideshowVideoEncoder.SlideMedia> slideMediaList = new ArrayList<>();
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

            mainHandler.post(() -> {
                final TextToSpeech[] holder = new TextToSpeech[1];
                holder[0] = new TextToSpeech(getApplicationContext(), status -> {
                    if (status == TextToSpeech.SUCCESS) {
                        if (isStopped()) holder[0].shutdown();
                        else ttsRef.set(holder[0]);
                    }
                    ttsInitLatch.countDown();
                });
            });

            ttsInitLatch.await(10, TimeUnit.SECONDS);
            TextToSpeech tts = ttsRef.get();
            if (tts == null) throw new java.io.IOException("Speech synthesis could not start. Please check your text-to-speech engine.");

            Locale targetLocale = "bn".equals(language) ? new Locale("bn", "BD") : Locale.US;

            tempDir = new File(getApplicationContext().getCacheDir(), "video_temp_" + System.currentTimeMillis());
            if (!tempDir.exists()) tempDir.mkdirs();

            List<SlideOutline> slides = deck.getSlides();
            int totalSlides = slides.size();

            // 3. Render slide images and synthesize audio per slide
            for (int i = 0; i < totalSlides; i++) {
                if (isStopped() || !ownerUid.equals(UserPaths.currentUid())) {
                    throw new java.util.concurrent.CancellationException("Video generation cancelled");
                }
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
                if (!synthOk) {
                    audioFile.delete(); // A partial WAV must not be treated as successful narration.
                }
                if (!synthOk || durationMs < 2000) {
                    durationMs = 4000; // fallback 4 seconds per slide
                } else {
                    durationMs += 800; // 0.8s breathing pause at end of each slide
                }

                slideMediaList.add(new SlideshowVideoEncoder.SlideMedia(slideBitmap, audioFile, durationMs));
            }

            // Shutdown TTS
            mainHandler.post(tts::shutdown);
            ttsRef.set(null);

            // 4. Encode Slideshow to MP4 using Hardware MediaCodec + MediaMuxer
            File outputDir = new File(UserPaths.filesDir(getApplicationContext(), ownerUid), "generated/" + projectId);
            if (!outputDir.exists()) outputDir.mkdirs();

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            finalVideoFile = new File(outputDir, "ExamForge_Video_" + timestamp + ".mp4");

            updateProgressNotification("Encoding 720p HD MP4 video with hardware MediaCodec...", 75);

            SlideshowVideoEncoder.encodeSlideshow(slideMediaList, finalVideoFile, (currentSlide, total, status) -> {
                int percent;
                if (status.startsWith("Encoding narration")) percent = 71 + currentSlide * 5 / total;
                else if (status.startsWith("Combining")) percent = 97;
                else percent = 77 + (currentSlide - 1) * 18 / total;
                updateProgressNotification(status, percent);
            }, () -> isStopped() || !ownerUid.equals(UserPaths.currentUid()));

            // 6. Notify complete
            postCompleteNotification(project.getName(), finalVideoFile);
            complete = true;

            return Result.success(new Data.Builder()
                    .putString(KEY_OUTPUT_VIDEO_PATH, finalVideoFile.getAbsolutePath())
                    .build());

        } catch (Exception e) {
            android.util.Log.e("VideoGenerationWorker", "Video production failed", e);
            String message = e.getMessage() != null ? e.getMessage() : "The device could not encode this video.";
            if (!isStopped()) postFailureNotification(message);
            return Result.failure(new Data.Builder().putString(KEY_ERROR, message).build());
        } finally {
            TextToSpeech tts = ttsRef.getAndSet(null);
            if (tts != null) mainHandler.post(tts::shutdown);
            for (SlideshowVideoEncoder.SlideMedia media : slideMediaList) {
                if (media.bitmap != null && !media.bitmap.isRecycled()) media.bitmap.recycle();
            }
            if (tempDir != null) deleteRecursive(tempDir);
            if (!complete && finalVideoFile != null) finalVideoFile.delete();
        }
    }

    private PresentationDeck fetchOrGenerateDeck(long projectId, Project project, String language, String detailLevel) throws Exception {
        AppDatabase db = ownerDatabase;
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
        if (isStopped()) return;
        long now = android.os.SystemClock.elapsedRealtime();
        if (lastNotificationAt != 0 && now - lastNotificationAt < 500) return;
        lastNotificationAt = now;
        setProgressAsync(new Data.Builder().putString("status", statusText).putInt("progress", progressPercent).build());
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                .setContentTitle("ExamForge • Video Studio")
                .setContentText(statusText)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, progressPercent, progressPercent == 0);

        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(getApplicationContext(), 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        builder.setContentIntent(pi);

        if (Build.VERSION.SDK_INT >= 35) {
            setForegroundAsync(new ForegroundInfo(NOTIFICATION_ID, builder.build(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING));
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            setForegroundAsync(new ForegroundInfo(NOTIFICATION_ID, builder.build(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC));
        } else {
            setForegroundAsync(new ForegroundInfo(NOTIFICATION_ID, builder.build()));
        }
    }

    private void postFailureNotification(String message) {
        NotificationManager manager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        manager.notify(NOTIFICATION_ID + 1, new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("ExamForge video generation failed")
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setAutoCancel(true).build());
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
                    "ExamForge Video Production",
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
