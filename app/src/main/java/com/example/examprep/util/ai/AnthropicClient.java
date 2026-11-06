package com.example.examprep.util.ai;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.examprep.ExamPrepApp;
import com.example.examprep.data.db.AppDatabase;
import com.example.examprep.data.model.CostRecord;
import com.example.examprep.util.SecurePrefs;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class AnthropicClient implements AiProvider {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2026-10-21";
    private static final String MODEL = "claude-haiku-5-5";
    private static final int MAX_TOKENS = 4096;

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    private final SecurePrefs securePrefs;
    private final OkHttpClient httpClient;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();

    public AnthropicClient(@NonNull Context context) {
        this.securePrefs = new SecurePrefs(context);
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(100, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public void generateStructuredText(
            @NonNull String systemInstruction,
            @NonNull String materialsText,
            @NonNull String userPrompt,
            @NonNull Callback callback
    ) {
        if (!securePrefs.hasApiKey()) {
            postError(callback, "No Anthropic API key configured. Please set your key in Settings.", null);
            return;
        }

        if (securePrefs.isDailyCapReached()) {
            postError(callback, String.format(
                    "Daily spending cap reached ($%.2f / $%.2f). Adjust your cap in Settings to continue.",
                    securePrefs.getTodaySpending(), securePrefs.getDailyCap()), null);
            return;
        }

        postProgress(callback, "Connecting to Claude Haiku 5.5...");

        // Construct payload with prompt caching enabled for the study materials block
        JsonObject requestBody = buildRequestBody(systemInstruction, materialsText, userPrompt);

        executeRequest(requestBody, new InternalCallback() {
            @Override
            public void onSuccess(String rawText, TokenUsage usage) {
                postProgress(callback, "Validating structured JSON...");
                try {
                    String cleanJson = JsonCleaner.cleanAndValidateJson(rawText);
                    recordCost(usage);
                    postSuccess(callback, new AiResponse(cleanJson, usage, false));
                } catch (JsonSyntaxException e) {
                    // Retry once on malformed output
                    postProgress(callback, "Malformed JSON received. Retrying with format correction...");
                    retryWithCorrection(systemInstruction, materialsText, userPrompt, rawText, e.getMessage(), callback);
                }
            }

            @Override
            public void onError(String errorMessage, Throwable throwable) {
                postError(callback, errorMessage, throwable);
            }
        });
    }

    private void retryWithCorrection(
            String systemInstruction,
            String materialsText,
            String originalPrompt,
            String malformedOutput,
            String parseError,
            Callback callback
    ) {
        String fixPrompt = originalPrompt + "\n\nCRITICAL: Your previous response contained JSON syntax errors ("
                + parseError + "). Output ONLY valid, strict JSON matching the schema. No markdown formatting, no conversational text.";

        JsonObject retryBody = buildRequestBody(systemInstruction, materialsText, fixPrompt);

        executeRequest(retryBody, new InternalCallback() {
            @Override
            public void onSuccess(String rawText, TokenUsage usage) {
                try {
                    String cleanJson = JsonCleaner.cleanAndValidateJson(rawText);
                    recordCost(usage);
                    postSuccess(callback, new AiResponse(cleanJson, usage, false));
                } catch (JsonSyntaxException e) {
                    recordCost(usage);
                    postError(callback, "AI failed to return valid JSON after retry: " + e.getMessage(), e);
                }
            }

            @Override
            public void onError(String errorMessage, Throwable throwable) {
                postError(callback, errorMessage, throwable);
            }
        });
    }

    @Override
    public void testConnection(@NonNull Callback callback) {
        if (!securePrefs.hasApiKey()) {
            postError(callback, "No API key entered yet.", null);
            return;
        }

        postProgress(callback, "Testing Anthropic connection...");

        JsonObject payload = new JsonObject();
        payload.addProperty("model", MODEL);
        payload.addProperty("max_tokens", 50);

        JsonArray messages = new JsonArray();
        JsonObject msg = new JsonObject();
        msg.addProperty("role", "user");
        msg.addProperty("content", "Respond strictly with JSON: {\"status\":\"ok\",\"model\":\"claude-haiku-5-5\"}");
        messages.add(msg);
        payload.add("messages", messages);

        executeRequest(payload, new InternalCallback() {
            @Override
            public void onSuccess(String rawText, TokenUsage usage) {
                recordCost(usage);
                try {
                    String clean = JsonCleaner.cleanAndValidateJson(rawText);
                    postSuccess(callback, new AiResponse(clean, usage, false));
                } catch (Exception e) {
                    postSuccess(callback, new AiResponse("{\"status\":\"connected\",\"raw\":\"" + rawText.replace("\"", "\\\"") + "\"}", usage, false));
                }
            }

            @Override
            public void onError(String errorMessage, Throwable throwable) {
                postError(callback, errorMessage, throwable);
            }
        });
    }

    private JsonObject buildRequestBody(String systemInstruction, String materialsText, String userPrompt) {
        JsonObject payload = new JsonObject();
        payload.addProperty("model", MODEL);
        payload.addProperty("max_tokens", MAX_TOKENS);

        // System prompt
        JsonArray systemArray = new JsonArray();
        JsonObject systemBlock = new JsonObject();
        systemBlock.addProperty("type", "text");
        systemBlock.addProperty("text", systemInstruction);
        systemArray.add(systemBlock);
        payload.add("system", systemArray);

        // Messages with prompt caching on materials block
        JsonArray messages = new JsonArray();
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");

        JsonArray contentArray = new JsonArray();

        // Materials block with cache_control: {"type": "ephemeral"}
        if (materialsText != null && !materialsText.trim().isEmpty()) {
            JsonObject materialsBlock = new JsonObject();
            materialsBlock.addProperty("type", "text");
            materialsBlock.addProperty("text", "--- STUDY MATERIALS (Grounded Source of Truth) ---\n" + materialsText);

            JsonObject cacheControl = new JsonObject();
            cacheControl.addProperty("type", "ephemeral");
            materialsBlock.add("cache_control", cacheControl);

            contentArray.add(materialsBlock);
        }

        // Instruction block
        JsonObject promptBlock = new JsonObject();
        promptBlock.addProperty("type", "text");
        promptBlock.addProperty("text", userPrompt);
        contentArray.add(promptBlock);

        userMessage.add("content", contentArray);
        messages.add(userMessage);

        payload.add("messages", messages);
        return payload;
    }

    private interface InternalCallback {
        void onSuccess(String rawText, TokenUsage usage);
        void onError(String errorMessage, Throwable throwable);
    }

    private void executeRequest(JsonObject payloadJson, InternalCallback callback) {
        String apiKey = securePrefs.getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            callback.onError("API key is missing", null);
            return;
        }

        RequestBody body = RequestBody.create(payloadJson.toString(), JSON_MEDIA_TYPE);

        Request request = new Request.Builder()
                .url(API_URL)
                .header("x-api-key", apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .header("content-type", "application/json")
                .post(body)
                .build();

        httpClient.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                callback.onError("Network connection failure: " + e.getMessage(), e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (ResponseBody responseBody = response.body()) {
                    if (responseBody == null) {
                        callback.onError("Received empty response from API", null);
                        return;
                    }

                    String responseString = responseBody.string();

                    if (!response.isSuccessful()) {
                        String parsedError = parseApiError(response.code(), responseString);
                        callback.onError(parsedError, null);
                        return;
                    }

                    // Parse successful response
                    JsonObject resJson = JsonParser.parseString(responseString).getAsJsonObject();

                    // Extract text content
                    StringBuilder textBuilder = new StringBuilder();
                    if (resJson.has("content")) {
                        JsonArray contentArray = resJson.getAsJsonArray("content");
                        for (JsonElement item : contentArray) {
                            if (item.isJsonObject()) {
                                JsonObject obj = item.getAsJsonObject();
                                if ("text".equals(obj.get("type").getAsString()) && obj.has("text")) {
                                    textBuilder.append(obj.get("text").getAsString());
                                }
                            }
                        }
                    }

                    // Extract token usage
                    TokenUsage usage = parseUsage(resJson);

                    callback.onSuccess(textBuilder.toString(), usage);
                } catch (Exception e) {
                    callback.onError("Failed to parse API response: " + e.getMessage(), e);
                }
            }
        });
    }

    private TokenUsage parseUsage(JsonObject resJson) {
        if (!resJson.has("usage")) {
            return TokenUsage.zero();
        }

        JsonObject usageObj = resJson.getAsJsonObject("usage");
        long inputTokens = usageObj.has("input_tokens") ? usageObj.get("input_tokens").getAsLong() : 0;
        long outputTokens = usageObj.has("output_tokens") ? usageObj.get("output_tokens").getAsLong() : 0;
        long cacheCreationTokens = usageObj.has("cache_creation_input_tokens") && !usageObj.get("cache_creation_input_tokens").isJsonNull()
                ? usageObj.get("cache_creation_input_tokens").getAsLong() : 0;
        long cacheReadTokens = usageObj.has("cache_read_input_tokens") && !usageObj.get("cache_read_input_tokens").isJsonNull()
                ? usageObj.get("cache_read_input_tokens").getAsLong() : 0;

        return new TokenUsage(inputTokens, outputTokens, cacheCreationTokens, cacheReadTokens);
    }

    private String parseApiError(int code, String body) {
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            if (json.has("error") && json.get("error").isJsonObject()) {
                JsonObject err = json.getAsJsonObject("error");
                String type = err.has("type") ? err.get("type").getAsString() : "";
                String message = err.has("message") ? err.get("message").getAsString() : "";

                switch (code) {
                    case 401:
                        return "Invalid Anthropic API Key. Please verify in Settings.";
                    case 429:
                        return "Anthropic API rate limit reached. Please wait a moment.";
                    case 529:
                        return "Anthropic servers temporarily overloaded. Please try again shortly.";
                    default:
                        return type + ": " + message;
                }
            }
        } catch (Exception ignored) {}

        return "Anthropic API Error (HTTP " + code + ")";
    }

    private void recordCost(TokenUsage usage) {
        securePrefs.addSpending(usage.getCostUsd());

        // Update Room cost_records in background
        new Thread(() -> {
            try {
                String today = LocalDate.now().toString();
                AppDatabase db = ExamPrepApp.getInstance().getDatabase();
                CostRecord existing = db.costRecordDao().getCostRecordForDate(today);

                if (existing == null) {
                    CostRecord newRecord = new CostRecord(today);
                    newRecord.setInputTokens(usage.getInputTokens());
                    newRecord.setOutputTokens(usage.getOutputTokens());
                    newRecord.setCacheWriteTokens(usage.getCacheCreationInputTokens());
                    newRecord.setCacheReadTokens(usage.getCacheReadInputTokens());
                    newRecord.setCostUsd(usage.getCostUsd());
                    newRecord.setRequestCount(1);
                    db.costRecordDao().insert(newRecord);
                } else {
                    existing.setInputTokens(existing.getInputTokens() + usage.getInputTokens());
                    existing.setOutputTokens(existing.getOutputTokens() + usage.getOutputTokens());
                    existing.setCacheWriteTokens(existing.getCacheWriteTokens() + usage.getCacheCreationInputTokens());
                    existing.setCacheReadTokens(existing.getCacheReadTokens() + usage.getCacheReadInputTokens());
                    existing.setCostUsd(existing.getCostUsd() + usage.getCostUsd());
                    existing.setRequestCount(existing.getRequestCount() + 1);
                    db.costRecordDao().insert(existing);
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void postProgress(Callback callback, String status) {
        mainHandler.post(() -> callback.onProgress(status));
    }

    private void postSuccess(Callback callback, AiResponse response) {
        mainHandler.post(() -> callback.onSuccess(response));
    }

    private void postError(Callback callback, String message, Throwable error) {
        mainHandler.post(() -> callback.onError(message, error));
    }
}
