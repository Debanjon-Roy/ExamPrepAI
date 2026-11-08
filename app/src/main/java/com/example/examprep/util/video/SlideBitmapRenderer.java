package com.example.examprep.util.video;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import androidx.annotation.NonNull;

import com.example.examprep.data.model.slide.SlideOutline;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class SlideBitmapRenderer {

    public static final int VIDEO_WIDTH = 1280;
    public static final int VIDEO_HEIGHT = 720;

    // Theme Colors
    private static final int COLOR_NAVY_DARK = Color.parseColor("#1A2C3D");
    private static final int COLOR_PRIMARY_BLUE = Color.parseColor("#204060");
    private static final int COLOR_AMBER = Color.parseColor("#C97A3E");
    private static final int COLOR_AMBER_LIGHT = Color.parseColor("#F5E8DD");
    private static final int COLOR_BG_LIGHT = Color.parseColor("#F5F7FA");
    private static final int COLOR_CARD_BG = Color.parseColor("#EBF2F7");
    private static final int COLOR_WHITE = Color.WHITE;
    private static final int COLOR_TEXT_DARK = Color.parseColor("#1D2A3A");
    private static final int COLOR_TEXT_MUTED = Color.parseColor("#5A6C7C");
    private static final int COLOR_BORDER = Color.parseColor("#204060");

    public static Bitmap renderSlideToBitmap(
            @NonNull SlideOutline slide,
            int slideNum,
            int totalSlides,
            @NonNull String projectName
    ) {
        Bitmap bitmap = Bitmap.createBitmap(VIDEO_WIDTH, VIDEO_HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        if ("title".equalsIgnoreCase(slide.getLayout())) {
            renderTitleSlide(canvas, slide, slideNum, totalSlides, projectName);
        } else if ("agenda".equalsIgnoreCase(slide.getLayout())) {
            renderAgendaSlide(canvas, slide, slideNum, totalSlides, projectName);
        } else {
            renderContentSlide(canvas, slide, slideNum, totalSlides, projectName);
        }

        return bitmap;
    }

    public static File saveBitmapToFile(Bitmap bitmap, File targetFile) throws IOException {
        try (FileOutputStream out = new FileOutputStream(targetFile)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out);
        }
        return targetFile;
    }

    private static void renderTitleSlide(
            Canvas canvas,
            SlideOutline slide,
            int slideNum,
            int totalSlides,
            String projectName
    ) {
        // Dark Navy Background
        canvas.drawColor(COLOR_NAVY_DARK);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Top decorative amber line
        paint.setColor(COLOR_AMBER);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(100, 90, 360, 98, paint);

        // Title
        String title = slide.getHeader() != null && !slide.getHeader().isEmpty() ? slide.getHeader() : projectName;
        TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(COLOR_WHITE);
        titlePaint.setTextSize(48f);
        titlePaint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));

        StaticLayout titleLayout = StaticLayout.Builder.obtain(title, 0, title.length(), titlePaint, 1080)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setMaxLines(3)
                .build();

        canvas.save();
        canvas.translate(100, 150);
        titleLayout.draw(canvas);
        canvas.restore();

        // Subtitle
        String subtitle = slide.getSubheader() != null && !slide.getSubheader().isEmpty()
                ? slide.getSubheader() : "Comprehensive Topic Review & Exam Preparation";
        TextPaint subPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        subPaint.setColor(Color.parseColor("#B0C4DE"));
        subPaint.setTextSize(26f);
        subPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));

        StaticLayout subLayout = StaticLayout.Builder.obtain(subtitle, 0, subtitle.length(), subPaint, 1080)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setMaxLines(2)
                .build();

        canvas.save();
        canvas.translate(100, 420);
        subLayout.draw(canvas);
        canvas.restore();

        // Footer Metadata badge
        paint.setColor(COLOR_AMBER);
        paint.setTextSize(18f);
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        canvas.drawText("EXAMPREP AI • NARRATED VIDEO MASTERCLASS", 100, 630, paint);
    }

    private static void renderAgendaSlide(
            Canvas canvas,
            SlideOutline slide,
            int slideNum,
            int totalSlides,
            String projectName
    ) {
        canvas.drawColor(COLOR_BG_LIGHT);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Header Title
        paint.setColor(COLOR_PRIMARY_BLUE);
        paint.setTextSize(36f);
        paint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        canvas.drawText(slide.getHeader(), 90, 80, paint);

        // Subtitle
        paint.setColor(COLOR_TEXT_MUTED);
        paint.setTextSize(18f);
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
        canvas.drawText("Agenda & Core Topics Covered in this Video", 90, 115, paint);

        // Topics cards
        List<String> bullets = slide.getBulletPoints();
        int y = 165;
        int cardHeight = 62;
        int cardGap = 16;

        for (int i = 0; i < bullets.size() && i < 6; i++) {
            String topic = bullets.get(i);

            // Number pill
            paint.setColor(COLOR_PRIMARY_BLUE);
            paint.setStyle(Paint.Style.FILL);
            RectF numPill = new RectF(90, y, 150, y + cardHeight);
            canvas.drawRoundRect(numPill, 8, 8, paint);

            paint.setColor(COLOR_WHITE);
            paint.setTextSize(22f);
            paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(String.valueOf(i + 1), 120, y + 40, paint);

            // Topic text box
            paint.setColor(COLOR_WHITE);
            RectF card = new RectF(165, y, 1190, y + cardHeight);
            canvas.drawRoundRect(card, 8, 8, paint);

            paint.setColor(COLOR_BORDER);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f);
            canvas.drawRoundRect(card, 8, 8, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(COLOR_TEXT_DARK);
            paint.setTextSize(20f);
            paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
            paint.setTextAlign(Paint.Align.LEFT);

            // Truncate if too long
            String displayTopic = topic.length() > 85 ? topic.substring(0, 82) + "..." : topic;
            canvas.drawText(displayTopic, 185, y + 38, paint);

            y += cardHeight + cardGap;
        }

        renderFooter(canvas, slideNum, totalSlides, projectName);
    }

    private static void renderContentSlide(
            Canvas canvas,
            SlideOutline slide,
            int slideNum,
            int totalSlides,
            String projectName
    ) {
        canvas.drawColor(COLOR_BG_LIGHT);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Header Title
        paint.setColor(COLOR_PRIMARY_BLUE);
        paint.setTextSize(34f);
        paint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        canvas.drawText(slide.getHeader(), 80, 75, paint);

        // Subheader
        if (slide.getSubheader() != null && !slide.getSubheader().isEmpty()) {
            paint.setColor(COLOR_TEXT_MUTED);
            paint.setTextSize(18f);
            paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
            canvas.drawText(slide.getSubheader(), 80, 110, paint);
        }

        // General Knowledge badge
        if (slide.isFromGeneralKnowledge()) {
            paint.setColor(COLOR_AMBER_LIGHT);
            paint.setStyle(Paint.Style.FILL);
            RectF badgeRect = new RectF(890, 48, 1200, 88);
            canvas.drawRoundRect(badgeRect, 20, 20, paint);

            paint.setColor(COLOR_AMBER);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f);
            canvas.drawRoundRect(badgeRect, 20, 20, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(13f);
            paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("General Knowledge Note", 1045, 73, paint);
        }

        boolean hasCallout = (slide.getCalloutContent() != null && !slide.getCalloutContent().trim().isEmpty());
        int bulletsWidth = hasCallout ? 660 : 1080;

        // Draw Bullets on Left
        List<String> bullets = slide.getBulletPoints();
        int curY = 155;
        TextPaint bulletPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        bulletPaint.setColor(COLOR_TEXT_DARK);
        bulletPaint.setTextSize(20f);
        bulletPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));

        if (bullets != null) {
            for (String b : bullets) {
                if (b == null || b.trim().isEmpty() || curY > 580) continue;

                // Amber bullet dot
                paint.setColor(COLOR_AMBER);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(92, curY + 12, 5, paint);

                // Bullet text layout
                StaticLayout bl = StaticLayout.Builder.obtain(b, 0, b.length(), bulletPaint, bulletsWidth - 35)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setMaxLines(3)
                        .build();

                canvas.save();
                canvas.translate(110, curY);
                bl.draw(canvas);
                canvas.restore();

                curY += bl.getHeight() + 20;
            }
        }

        // Draw Callout Box on Right
        if (hasCallout) {
            int cx = 780;
            int cy = 155;
            int cw = 420;
            int ch = 450;

            // Box Fill
            paint.setColor(COLOR_CARD_BG);
            paint.setStyle(Paint.Style.FILL);
            RectF cRect = new RectF(cx, cy, cx + cw, cy + ch);
            canvas.drawRoundRect(cRect, 14, 14, paint);

            // Box Border
            paint.setColor(COLOR_BORDER);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f);
            canvas.drawRoundRect(cRect, 14, 14, paint);

            // Callout Pill Header
            paint.setColor(COLOR_PRIMARY_BLUE);
            paint.setStyle(Paint.Style.FILL);
            RectF pill = new RectF(cx + 25, cy + 25, cx + cw - 25, cy + 70);
            canvas.drawRoundRect(pill, 8, 8, paint);

            String cTitle = slide.getCalloutTitle() != null && !slide.getCalloutTitle().isEmpty()
                    ? slide.getCalloutTitle() : "EXAM ESSENTIAL";
            paint.setColor(COLOR_WHITE);
            paint.setTextSize(16f);
            paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(cTitle, cx + (cw / 2), cy + 54, paint);

            // Callout Text
            TextPaint cp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            cp.setColor(COLOR_TEXT_DARK);
            cp.setTextSize(17f);
            cp.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));

            StaticLayout cl = StaticLayout.Builder.obtain(slide.getCalloutContent(), 0, slide.getCalloutContent().length(), cp, cw - 50)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setMaxLines(12)
                    .build();

            canvas.save();
            canvas.translate(cx + 25, cy + 90);
            cl.draw(canvas);
            canvas.restore();
        }

        renderFooter(canvas, slideNum, totalSlides, projectName);
    }

    private static void renderFooter(Canvas canvas, int slideNum, int totalSlides, String projectName) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.parseColor("#8C9BAE"));
        paint.setTextSize(14f);
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
        paint.setTextAlign(Paint.Align.RIGHT);

        String footer = projectName + " • Slide " + slideNum + " of " + totalSlides;
        canvas.drawText(footer, 1200, 680, paint);
    }
}
