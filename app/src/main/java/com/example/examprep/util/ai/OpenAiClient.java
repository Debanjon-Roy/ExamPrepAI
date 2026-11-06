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

public class OpenAiClient implements AiProvider {

    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String MODEL = "gpt-4o-mini";
    private static final int MAX_TOKENS = 4096;

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    private final SecurePrefs securePrefs;
    private final OkHttpClient httpClient;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();

    public OpenAiClient(@NonNull Context context) {
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
            postError(callback, "No ChatGPT / OpenAI API key configured. Please set your key in Settings.", null);
            return;
        }

        if (securePrefs.isDailyCapReached()) {
            postError(callback, String.format(
                    "Daily spending cap reached ($%.2f / $%.2f). Adjust your cap in Settings to continue.",
                    securePrefs.getTodaySpending(), securePrefs.getDailyCap()), null);
            return;
        }

        postProgress(callback, "Connecting to ChatGPT (GPT-4o mini)...");

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
                + parseError + "). Output ONLY valid, strict JSON matching the requested schema without markdown backticks or extra text.";

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
                    postError(callback, "ChatGPT failed to return valid JSON after retry: " + e.getMessage(), e);
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

        postProgress(callback, "Testing ChatGPT / OpenAI connection...");

        JsonObject payload = new JsonObject();
        payload.addProperty("model", MODEL);
        payload.addProperty("max_tokens", 60);

        JsonObject responseFormat = new JsonObject();
        responseFormat.addProperty("type", "json_object");
        payload.add("response_format", responseFormat);

        JsonArray messages = new JsonArray();
        JsonObject sysMsg = new JsonObject();
        sysMsg.addProperty("role", "system");
        sysMsg.addProperty("content", "You are an assistant. Respond strictly with JSON.");
        messages.add(sysMsg);

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", "Return JSON: {\"status\":\"ok\",\"model\":\"gpt-4o-mini\"}");
        messages.add(userMsg);

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
        payload.addProperty("temperature", 0.2);

        // OpenAI JSON response format requires the word "json" to appear in the prompt
        JsonObject responseFormat = new JsonObject();
        responseFormat.addProperty("type", "json_object");
        payload.add("response_format", responseFormat);

        String effectiveSystem = systemInstruction;
        if (!effectiveSystem.toLowerCase().contains("json")) {
            effectiveSystem = effectiveSystem + " You must output valid JSON.";
        }

        JsonArray messages = new JsonArray();

        // System message
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", effectiveSystem);
        messages.add(systemMessage);

        // User message (materials + prompt)
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");

        StringBuilder contentBuilder = new StringBuilder();
        if (materialsText != null && !materialsText.trim().isEmpty()) {
            contentBuilder.append("--- STUDY MATERIALS (Grounded Source of Truth) ---\n")
                    .append(materialsText.trim())
                    .append("\n\n--- INSTRUCTIONS ---\n");
        }
        contentBuilder.append(userPrompt);

        userMessage.addProperty("content", contentBuilder.toString());
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
            callback.onError("ChatGPT API key is missing. Please add it in Settings.", null);
            return;
        }

        RequestBody body = RequestBody.create(payloadJson.toString(), JSON_MEDIA_TYPE);

        Request request = new Request.Builder()
                .url(API_URL)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
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
                        callback.onError("Received empty response from ChatGPT API", null);
                        return;
                    }

                    String responseString = responseBody.string();

                    if (!response.isSuccessful()) {
                        String parsedError = parseApiError(response.code(), responseString);
                        callback.onError(parsedError, null);
                        return;
                    }

                    JsonObject resJson = JsonParser.parseString(responseString).getAsJsonObject();

                    // Extract text content from choices[0].message.content
                    String contentText = "";
                    if (resJson.has("choices") && resJson.getAsJsonArray("choices").size() > 0) {
                        JsonObject firstChoice = resJson.getAsJsonArray("choices").get(0).getAsJsonObject();
                        if (firstChoice.has("message") && firstChoice.getAsJsonObject("message").has("content")) {
                            JsonElement contentElem = firstChoice.getAsJsonObject("message").get("content");
                            if (!contentElem.isJsonNull()) {
                                contentText = contentElem.getAsString();
                            }
                        }
                    }

                    // Extract token usage
                    TokenUsage usage = parseUsage(resJson);

                    callback.onSuccess(contentText, usage);
                } catch (Exception e) {
                    callback.onError("Failed to parse ChatGPT API response: " + e.getMessage(), e);
                }
            }
        });
    }

    private TokenUsage parseUsage(JsonObject resJson) {
        if (!resJson.has("usage")) {
            return TokenUsage.zero();
        }

        JsonObject usageObj = resJson.getAsJsonObject("usage");
        long promptTokens = usageObj.has("prompt_tokens") ? usageObj.get("prompt_tokens").getAsLong() : 0;
        long completionTokens = usageObj.has("completion_tokens") ? usageObj.get("completion_tokens").getAsLong() : 0;
        long cachedTokens = 0;

        if (usageObj.has("prompt_tokens_details") && usageObj.get("prompt_tokens_details").isJsonObject()) {
            JsonObject details = usageObj.getAsJsonObject("prompt_tokens_details");
            if (details.has("cached_tokens")) {
                cachedTokens = details.get("cached_tokens").getAsLong();
            }
        }

        return new TokenUsage(promptTokens, completionTokens, 0, cachedTokens);
    }

    private String parseApiError(int code, String body) {
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            if (json.has("error") && json.get("error").isJsonObject()) {
                JsonObject err = json.getAsJsonObject("error");
                String message = err.has("message") ? err.get("message").getAsString() : "";
                String type = err.has("type") ? err.get("type").getAsString() : "";
                String errorCode = err.has("code") && !err.get("code").isJsonNull() ? err.get("code").getAsString() : "";

                switch (code) {
                    case 401:
                        return "Invalid ChatGPT / OpenAI API Key. Please verify your key in Settings.";
                    case 429:
                        if (message.toLowerCase().contains("quota") || "insufficient_quota".equalsIgnoreCase(errorCode)) {
                            return "OpenAI account quota exceeded. Please check your OpenAI account billing / credits balance.";
                        }
                        return "ChatGPT API rate limit reached. Please wait a moment and try again.";
                    case 500:
                    case 503:
                        return "OpenAI servers temporarily overloaded. Please try again shortly.";
                    default:
                        if (!message.isEmpty()) {
                            return message;
                        }
                        return type + (errorCode.isEmpty() ? "" : " (" + errorCode + ")");
                }
            }
        } catch (Exception ignored) {}

        return "ChatGPT API Error (HTTP " + code + ")";
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
