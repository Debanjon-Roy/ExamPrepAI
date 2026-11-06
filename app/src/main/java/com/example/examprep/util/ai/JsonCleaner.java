package com.example.examprep.util.ai;

import androidx.annotation.NonNull;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

public class JsonCleaner {

    @NonNull
    public static String cleanAndValidateJson(@NonNull String rawResponse) throws JsonSyntaxException {
        String trimmed = rawResponse.trim();

        // 1. Remove markdown fences
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }

        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        trimmed = trimmed.trim();

        // 2. Locate outermost JSON object { ... } or array [ ... ]
        int firstBrace = trimmed.indexOf('{');
        int firstBracket = trimmed.indexOf('[');

        int start = -1;
        int end = -1;

        if (firstBrace != -1 && (firstBracket == -1 || firstBrace < firstBracket)) {
            start = firstBrace;
            end = trimmed.lastIndexOf('}');
        } else if (firstBracket != -1) {
            start = firstBracket;
            end = trimmed.lastIndexOf(']');
        }

        if (start != -1 && end != -1 && end > start) {
            trimmed = trimmed.substring(start, end + 1).trim();
        }

        // 3. Validate syntax using Gson
        JsonElement element = JsonParser.parseString(trimmed);
        if (!element.isJsonObject() && !element.isJsonArray()) {
            throw new JsonSyntaxException("Parsed JSON is neither an object nor an array");
        }

        return trimmed;
    }
}
