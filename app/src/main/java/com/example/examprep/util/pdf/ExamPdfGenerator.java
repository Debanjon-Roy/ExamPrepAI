package com.example.examprep.util.pdf;

import com.example.examprep.util.UserPaths;

import android.content.Context;

import androidx.annotation.NonNull;

import com.example.examprep.data.model.exam.ExamQuestion;
import com.example.examprep.data.model.exam.ExamQuiz;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Div;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.properties.AreaBreakType;
import com.itextpdf.layout.properties.UnitValue;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ExamPdfGenerator {

    private static final DeviceRgb COLOR_PRIMARY = new DeviceRgb(32, 64, 96);       // #204060
    private static final DeviceRgb COLOR_PRIMARY_LIGHT = new DeviceRgb(63, 97, 136); // #3F6188
    private static final DeviceRgb COLOR_ACCENT = new DeviceRgb(201, 122, 62);      // #C97A3E
    private static final DeviceRgb COLOR_BG_CALLOUT = new DeviceRgb(240, 244, 248);  // Slate tint
    private static final DeviceRgb COLOR_BG_NOTE = new DeviceRgb(251, 245, 238);     // Parchment tint
    private static final DeviceRgb COLOR_TEXT_DARK = new DeviceRgb(28, 40, 51);      // Deep charcoal
    private static final DeviceRgb COLOR_TEXT_MUTED = new DeviceRgb(93, 109, 126);   // Slate gray

    public static File generateExamPdf(
            @NonNull Context context,
            long projectId,
            @NonNull String projectName,
            @NonNull ExamQuiz quiz
    ) throws Exception {
        return generateExamPdf(context, projectId, projectName, quiz, UserPaths.filesDir(context));
    }

    public static File generateExamPdf(
            @NonNull Context context,
            long projectId,
            @NonNull String projectName,
            @NonNull ExamQuiz quiz,
            @NonNull File ownerFilesDir
    ) throws Exception {

        File outputDir = new File(ownerFilesDir, "generated/" + projectId);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        String fileName = "ExamPractice_" + System.currentTimeMillis() + ".pdf";
        File pdfFile = new File(outputDir, fileName);

        PdfFont fontBold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont fontItalic = PdfFontFactory.createFont(StandardFonts.HELVETICA_OBLIQUE);
        PdfFont fontRegular = PdfFontFactory.createFont(StandardFonts.HELVETICA);

        try (FileOutputStream fos = new FileOutputStream(pdfFile);
             PdfWriter writer = new PdfWriter(fos);
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document doc = new Document(pdfDoc)) {

            doc.setMargins(36, 40, 36, 40);

            // ================= PART 1: QUESTION PAPER =================

            // Header Banner
            Div header = new Div()
                    .setBackgroundColor(COLOR_BG_CALLOUT)
                    .setBorder(new SolidBorder(COLOR_PRIMARY, 1.5f))
                    .setPadding(14)
                    .setMarginBottom(16);

            header.add(new Paragraph(quiz.getExamTitle())
                    .setFontSize(20)
                    .setFont(fontBold)
                    .setFontColor(COLOR_PRIMARY)
                    .setMarginBottom(2));

            String dateStr = new SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(new Date());
            String meta = String.format("Subject: %s  •  Difficulty: %s  •  Total Questions: %d  •  Date: %s",
                    projectName, capitalize(quiz.getDifficulty()), quiz.getQuestions().size(), dateStr);

            header.add(new Paragraph(meta)
                    .setFontSize(9)
                    .setFont(fontRegular)
                    .setFontColor(COLOR_TEXT_MUTED));

            doc.add(header);

            // Instructions Box
            Div instructions = new Div()
                    .setBackgroundColor(COLOR_BG_NOTE)
                    .setBorderLeft(new SolidBorder(COLOR_ACCENT, 3f))
                    .setBorderTop(Border.NO_BORDER)
                    .setBorderRight(Border.NO_BORDER)
                    .setBorderBottom(Border.NO_BORDER)
                    .setPadding(8)
                    .setMarginBottom(16);

            instructions.add(new Paragraph("Instructions for Candidate")
                    .setFontSize(10)
                    .setFont(fontBold)
                    .setFontColor(COLOR_ACCENT)
                    .setMarginBottom(2));

            instructions.add(new Paragraph("• Read each question carefully. For Multiple Choice Questions, select the single best answer.\n" +
                    "• Complete all questions in Part 1 before consulting the Answer Key & Explanations in Part 2.")
                    .setFontSize(8.5f)
                    .setFont(fontRegular)
                    .setFontColor(COLOR_TEXT_DARK));

            doc.add(instructions);

            // Print Questions
            for (int i = 0; i < quiz.getQuestions().size(); i++) {
                ExamQuestion q = quiz.getQuestions().get(i);

                Paragraph qPara = new Paragraph()
                        .add(new Text("Q" + (i + 1) + ". ").setFont(fontBold).setFontSize(11).setFontColor(COLOR_PRIMARY))
                        .add(new Text(q.getQuestion()).setFont(fontBold).setFontSize(10.5f).setFontColor(COLOR_TEXT_DARK))
                        .setMarginBottom(4);

                if (!q.getSourceTopic().isEmpty()) {
                    qPara.add(new Text("  [" + q.getSourceTopic() + "]").setFont(fontItalic).setFontSize(8).setFontColor(COLOR_TEXT_MUTED));
                }

                doc.add(qPara);

                if (q.isMcq() && !q.getOptions().isEmpty()) {
                    for (String opt : q.getOptions()) {
                        Paragraph optPara = new Paragraph("   [   ]  " + opt)
                                .setFontSize(9.5f)
                                .setFont(fontRegular)
                                .setFontColor(COLOR_TEXT_DARK)
                                .setMarginBottom(2);
                        doc.add(optPara);
                    }
                } else if ("short_answer".equalsIgnoreCase(q.getType())) {
                    // Blank lines for short answer
                    Paragraph space = new Paragraph("\n_________________________________________________________________________________\n_________________________________________________________________________________\n")
                            .setFontSize(8)
                            .setFontColor(COLOR_TEXT_MUTED);
                    doc.add(space);
                } else {
                    // Blank lines for essay
                    Paragraph space = new Paragraph("\n_________________________________________________________________________________\n_________________________________________________________________________________\n_________________________________________________________________________________\n_________________________________________________________________________________\n")
                            .setFontSize(8)
                            .setFontColor(COLOR_TEXT_MUTED);
                    doc.add(space);
                }

                doc.add(new Paragraph().setMarginBottom(10));
            }

            // ================= PART 2: ANSWER KEY & EXPLANATIONS =================
            doc.add(new AreaBreak(AreaBreakType.NEXT_PAGE));

            Div answerHeader = new Div()
                    .setBackgroundColor(COLOR_BG_CALLOUT)
                    .setBorder(new SolidBorder(COLOR_PRIMARY, 1.5f))
                    .setPadding(12)
                    .setMarginBottom(16);

            answerHeader.add(new Paragraph("Part 2: Answer Key & Detailed Explanations")
                    .setFontSize(18)
                    .setFont(fontBold)
                    .setFontColor(COLOR_PRIMARY));

            answerHeader.add(new Paragraph("Grounded in uploaded study materials. Review step-by-step logic for every question.")
                    .setFontSize(9)
                    .setFont(fontItalic)
                    .setFontColor(COLOR_TEXT_MUTED));

            doc.add(answerHeader);

            // Quick Answer Summary Table
            Table summaryTable = new Table(UnitValue.createPercentArray(new float[]{15, 25, 60}))
                    .useAllAvailableWidth()
                    .setMarginBottom(18);

            summaryTable.addHeaderCell(createHeaderCell("Question", fontBold));
            summaryTable.addHeaderCell(createHeaderCell("Type", fontBold));
            summaryTable.addHeaderCell(createHeaderCell("Correct Answer", fontBold));

            for (int i = 0; i < quiz.getQuestions().size(); i++) {
                ExamQuestion q = quiz.getQuestions().get(i);
                summaryTable.addCell(createDataCell("Q" + (i + 1), fontBold, COLOR_PRIMARY));
                summaryTable.addCell(createDataCell(capitalize(q.getType()), fontRegular, COLOR_TEXT_DARK));
                summaryTable.addCell(createDataCell(q.getCorrectAnswer(), fontBold, COLOR_ACCENT));
            }

            doc.add(summaryTable);

            // Step-by-Step Explanations
            Paragraph expHeader = new Paragraph("Detailed Explanations & Solutions")
                    .setFontSize(13)
                    .setFont(fontBold)
                    .setFontColor(COLOR_PRIMARY)
                    .setMarginBottom(8);
            doc.add(expHeader);

            for (int i = 0; i < quiz.getQuestions().size(); i++) {
                ExamQuestion q = quiz.getQuestions().get(i);

                Div expBox = new Div()
                        .setBackgroundColor(COLOR_BG_CALLOUT)
                        .setBorderLeft(new SolidBorder(COLOR_PRIMARY, 3f))
                        .setBorderTop(Border.NO_BORDER)
                        .setBorderRight(Border.NO_BORDER)
                        .setBorderBottom(Border.NO_BORDER)
                        .setPadding(8)
                        .setMarginBottom(10);

                expBox.add(new Paragraph("Q" + (i + 1) + ": " + q.getQuestion())
                        .setFontSize(10)
                        .setFont(fontBold)
                        .setFontColor(COLOR_PRIMARY)
                        .setMarginBottom(3));

                expBox.add(new Paragraph("✓ Correct Answer: " + q.getCorrectAnswer())
                        .setFontSize(9.5f)
                        .setFont(fontBold)
                        .setFontColor(COLOR_ACCENT)
                        .setMarginBottom(3));

                if (!q.getExplanation().isEmpty()) {
                    expBox.add(new Paragraph("Explanation: " + q.getExplanation())
                            .setFontSize(9)
                            .setFont(fontRegular)
                            .setFontColor(COLOR_TEXT_DARK));
                }

                doc.add(expBox);
            }
        }

        return pdfFile;
    }

    private static Cell createHeaderCell(String text, PdfFont font) {
        return new Cell()
                .add(new Paragraph(text).setFont(font).setFontSize(9.5f).setFontColor(ColorConstants.WHITE))
                .setBackgroundColor(COLOR_PRIMARY)
                .setPadding(5);
    }

    private static Cell createDataCell(String text, PdfFont font, DeviceRgb color) {
        return new Cell()
                .add(new Paragraph(text).setFont(font).setFontSize(9).setFontColor(color))
                .setPadding(4);
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return "";
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
