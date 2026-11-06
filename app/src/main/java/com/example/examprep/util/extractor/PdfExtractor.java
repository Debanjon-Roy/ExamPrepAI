package com.example.examprep.util.extractor;

import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;

import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.io.File;

public class PdfExtractor {

    private static final int MIN_CHARS_FOR_NATIVE_TEXT = 35;
    private static final int MAX_PAGES_TO_PROCESS = 150; // Safety guard against runaway 1000-page books

    public static TextExtractorResult extract(File pdfFile) {
        if (pdfFile == null || !pdfFile.exists()) {
            return TextExtractorResult.failure("PDF file does not exist");
        }

        PDDocument document = null;
        PdfRenderer renderer = null;
        ParcelFileDescriptor pfd = null;

        StringBuilder fullText = new StringBuilder();
        int totalPages = 0;
        int ocrPageCount = 0;
        int nativePageCount = 0;

        try {
            document = PDDocument.load(pdfFile);
            totalPages = document.getNumberOfPages();
            int pagesToProcess = Math.min(totalPages, MAX_PAGES_TO_PROCESS);

            PDFTextStripper stripper = new PDFTextStripper();

            // Lazy open PdfRenderer only if needed for scanned pages
            for (int page = 1; page <= pagesToProcess; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = "";
                try {
                    pageText = stripper.getText(document).trim();
                } catch (Exception ignored) {}

                // If native text extraction returned meaningful text
                if (pageText.length() >= MIN_CHARS_FOR_NATIVE_TEXT) {
                    fullText.append("--- Page ").append(page).append(" ---\n");
                    fullText.append(pageText).append("\n\n");
                    nativePageCount++;
                } else {
                    // Page appears to be scanned or contains image text. Fall back to OCR!
                    if (renderer == null) {
                        try {
                            pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY);
                            renderer = new PdfRenderer(pfd);
                        } catch (Exception e) {
                            // Renderer not available, keep whatever text we had
                        }
                    }

                    String ocrResult = null;
                    if (renderer != null && (page - 1) < renderer.getPageCount()) {
                        PdfRenderer.Page pdfPage = null;
                        Bitmap bitmap = null;
                        try {
                            pdfPage = renderer.openPage(page - 1);
                            // Render at ~1.5x resolution for good OCR accuracy without OOM
                            int width = (int) (pdfPage.getWidth() * 1.5f);
                            int height = (int) (pdfPage.getHeight() * 1.5f);
                            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                            pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

                            TextExtractorResult ocr = ImageOcrExtractor.extractFromBitmap(bitmap);
                            if (ocr.isSuccess() && ocr.getText() != null && !ocr.getText().isEmpty()) {
                                ocrResult = ocr.getText();
                                ocrPageCount++;
                            }
                        } catch (Exception ignored) {
                        } finally {
                            if (pdfPage != null) {
                                pdfPage.close();
                            }
                            if (bitmap != null && !bitmap.isRecycled()) {
                                bitmap.recycle();
                            }
                        }
                    }

                    fullText.append("--- Page ").append(page).append(" ");
                    if (ocrResult != null && !ocrResult.isEmpty()) {
                        fullText.append("(OCR) ---\n").append(ocrResult).append("\n\n");
                    } else if (!pageText.isEmpty()) {
                        fullText.append("---\n").append(pageText).append("\n\n");
                    } else {
                        fullText.append("---\n[No readable text on this page]\n\n");
                    }
                }
            }

            if (totalPages > MAX_PAGES_TO_PROCESS) {
                fullText.append("\n[Document truncated after ").append(MAX_PAGES_TO_PROCESS)
                        .append(" of ").append(totalPages).append(" pages to preserve memory]\n");
            }

            String content = fullText.toString().trim();
            if (content.isEmpty()) {
                return TextExtractorResult.failure("Could not extract text or OCR any pages in PDF");
            }

            TextExtractorResult.Method method;
            if (ocrPageCount > 0 && nativePageCount > 0) {
                method = TextExtractorResult.Method.HYBRID;
            } else if (ocrPageCount > 0) {
                method = TextExtractorResult.Method.OCR;
            } else {
                method = TextExtractorResult.Method.NATIVE_TEXT;
            }

            return TextExtractorResult.success(content, method, totalPages);

        } catch (Exception e) {
            return TextExtractorResult.failure("PDF extraction error: " + e.getMessage());
        } finally {
            if (document != null) {
                try {
                    document.close();
                } catch (Exception ignored) {}
            }
            if (renderer != null) {
                try {
                    renderer.close();
                } catch (Exception ignored) {}
            }
            if (pfd != null) {
                try {
                    pfd.close();
                } catch (Exception ignored) {}
            }
        }
    }
}
