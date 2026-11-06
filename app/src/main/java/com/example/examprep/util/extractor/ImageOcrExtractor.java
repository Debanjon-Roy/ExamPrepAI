package com.example.examprep.util.extractor;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

public class ImageOcrExtractor {

    private static TextRecognizer getRecognizer() {
        return TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    }

    public static TextExtractorResult extractFromBitmap(@NonNull Bitmap bitmap) {
        try {
            InputImage image = InputImage.fromBitmap(bitmap, 0);
            TextRecognizer recognizer = getRecognizer();
            Text visionText = Tasks.await(recognizer.process(image));
            String resultText = visionText.getText().trim();

            if (resultText.isEmpty()) {
                return TextExtractorResult.failure("No text recognized in image");
            }

            // Quality heuristic check
            if (isOcrQualityPoor(resultText)) {
                return new TextExtractorResult(true, resultText, TextExtractorResult.Method.OCR, 1,
                        "Low confidence / sparse text detected");
            }

            return TextExtractorResult.success(resultText, TextExtractorResult.Method.OCR, 1);
        } catch (Exception e) {
            return TextExtractorResult.failure("OCR failed: " + e.getMessage());
        }
    }

    public static TextExtractorResult extractFromUri(@NonNull Context context, @NonNull Uri imageUri) {
        try {
            InputImage image = InputImage.fromFilePath(context, imageUri);
            TextRecognizer recognizer = getRecognizer();
            Text visionText = Tasks.await(recognizer.process(image));
            String resultText = visionText.getText().trim();

            if (resultText.isEmpty()) {
                return TextExtractorResult.failure("No text detected in photo");
            }

            return TextExtractorResult.success(resultText, TextExtractorResult.Method.OCR, 1);
        } catch (Exception e) {
            return TextExtractorResult.failure("OCR failed: " + e.getMessage());
        }
    }

    public static boolean isOcrQualityPoor(String text) {
        if (text == null || text.trim().length() < 15) {
            return true;
        }
        // Count letters vs non-alphanumeric noise
        int letters = 0;
        for (char c : text.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                letters++;
            }
        }
        double ratio = (double) letters / text.length();
        return ratio < 0.45; // If less than 45% alphanumeric, likely noise
    }
}
