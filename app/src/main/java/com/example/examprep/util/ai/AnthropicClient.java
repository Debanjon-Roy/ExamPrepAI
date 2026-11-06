package com.example.examprep.util.ai;

import android.content.Context;
import androidx.annotation.NonNull;

/**
 * Legacy wrapper maintaining backwards compatibility.
 * Delegates directly to {@link OpenAiClient} (ChatGPT).
 */
public class AnthropicClient implements AiProvider {

    private final OpenAiClient delegate;

    public AnthropicClient(@NonNull Context context) {
        this.delegate = new OpenAiClient(context);
    }

    @Override
    public void generateStructuredText(
            @NonNull String systemInstruction,
            @NonNull String materialsText,
            @NonNull String userPrompt,
            @NonNull Callback callback
    ) {
        delegate.generateStructuredText(systemInstruction, materialsText, userPrompt, callback);
    }

    @Override
    public void testConnection(@NonNull Callback callback) {
        delegate.testConnection(callback);
    }
}
