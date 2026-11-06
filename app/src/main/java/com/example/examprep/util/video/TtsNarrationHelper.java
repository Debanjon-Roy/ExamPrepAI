package com.example.examprep.util.video;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class TtsNarrationHelper {

    public interface LanguageCheckCallback {
        void onResult(boolean isAvailable, boolean needsDownload, String message);
    }

    public static void checkLanguageAvailability(
            @NonNull Context context,
            @NonNull String langCode,
            @NonNull LanguageCheckCallback callback
    ) {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        final TextToSpeech[] ttsHolder = new TextToSpeech[1];

        ttsHolder[0] = new TextToSpeech(context.getApplicationContext(), status -> {
            if (status != TextToSpeech.SUCCESS) {
                mainHandler.post(() -> callback.onResult(false, false, "Text-to-Speech service unavailable"));
                return;
            }

            Locale targetLocale = "bn".equals(langCode) ? new Locale("bn", "BD") : Locale.US;
            int avail = ttsHolder[0].isLanguageAvailable(targetLocale);

            boolean isAvail = (avail == TextToSpeech.LANG_AVAILABLE ||
                    avail == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
                    avail == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE);

            boolean needsDownload = (avail == TextToSpeech.LANG_MISSING_DATA);

            String message;
            if (isAvail) {
                message = "TTS voice available";
            } else if (needsDownload) {
                message = "Voice data is missing for " + targetLocale.getDisplayName() + ". Please install voice data in Android settings.";
            } else {
                message = "Language " + targetLocale.getDisplayName() + " is not supported by your current TTS engine.";
            }

            ttsHolder[0].shutdown();
            mainHandler.post(() -> callback.onResult(isAvail, needsDownload, message));
        });
    }

    public static long getAudioDurationMs(File audioFile) {
        if (audioFile == null || !audioFile.exists() || audioFile.length() == 0) {
            return 3000; // fallback 3 seconds
        }
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(audioFile.getAbsolutePath());
            String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (durationStr != null) {
                return Long.parseLong(durationStr);
            }
        } catch (Exception ignored) {
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {}
        }
        // rough estimate from file size if WAV 16-bit 22.05kHz mono: ~44KB/s
        long bytes = audioFile.length();
        long estimated = (bytes * 1000L) / 44100L;
        return Math.max(3000, estimated);
    }

    public static boolean synthesizeToFileSync(
            @NonNull TextToSpeech tts,
            @NonNull String text,
            @NonNull File targetFile,
            @NonNull Locale locale,
            long timeoutSeconds
    ) {
        tts.setLanguage(locale);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean(false);
        String utteranceId = "utterance_" + System.currentTimeMillis();

        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String id) {}

            @Override
            public void onDone(String id) {
                if (utteranceId.equals(id)) {
                    success.set(true);
                    latch.countDown();
                }
            }

            @Override
            public void onError(String id) {
                if (utteranceId.equals(id)) {
                    success.set(false);
                    latch.countDown();
                }
            }
        });

        Bundle params = new Bundle();
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
        int result = tts.synthesizeToFile(text, params, targetFile, utteranceId);
        if (result != TextToSpeech.SUCCESS) {
            return false;
        }

        try {
            boolean done = latch.await(timeoutSeconds, TimeUnit.SECONDS);
            return done && success.get() && targetFile.exists() && targetFile.length() > 100;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
