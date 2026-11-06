package com.example.examprep.util.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

public interface AiProvider {

    interface Callback {
        void onProgress(String statusMessage);
        void onSuccess(@NonNull AiResponse response);
        void onError(@NonNull String errorMessage, @Nullable Throwable error);
    }

    /**
     * Send prompt with materials. Implementations handle prompt caching,
     * token measurement, and retry logic.
     */
    void generateStructuredText(
            @NonNull String systemInstruction,
            @NonNull String materialsText,
            @NonNull String userPrompt,
            @NonNull Callback callback
    );

    /**
     * Test connection with minimal token usage (~20 tokens).
     */
    void testConnection(@NonNull Callback callback);
}
