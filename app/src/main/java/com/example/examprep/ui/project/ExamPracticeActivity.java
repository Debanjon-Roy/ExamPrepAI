package com.example.examprep.ui.project;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.example.examprep.R;
import com.example.examprep.data.model.exam.ExamQuestion;
import com.example.examprep.data.model.exam.ExamQuiz;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class ExamPracticeActivity extends AppCompatActivity {

    public static final String EXTRA_QUIZ = "extra_quiz";

    private ExamQuiz quiz;
    private int currentIndex = 0;
    private int correctCount = 0;
    private final List<Boolean> answersRecorded = new ArrayList<>();

    private ProgressBar progressBar;
    private TextView textCounter;
    private TextView textDifficulty;
    private TextView textTopic;
    private TextView textContent;
    private LinearLayout layoutMcq;
    private LinearLayout layoutFreeform;
    private MaterialButton btnReveal;
    private MaterialCardView cardFeedback;
    private TextView textFeedbackTitle;
    private TextView textFeedbackExp;
    private MaterialButton btnPrev;
    private MaterialButton btnNext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exam_practice);

        quiz = (ExamQuiz) getIntent().getSerializableExtra(EXTRA_QUIZ);
        if (quiz == null || quiz.getQuestions().isEmpty()) {
            finish();
            return;
        }

        for (int i = 0; i < quiz.getQuestions().size(); i++) {
            answersRecorded.add(null);
        }

        Toolbar toolbar = findViewById(R.id.toolbar_practice);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(quiz.getExamTitle());
        }

        progressBar = findViewById(R.id.progress_quiz);
        textCounter = findViewById(R.id.text_q_counter);
        textDifficulty = findViewById(R.id.text_q_difficulty);
        textTopic = findViewById(R.id.text_q_topic);
        textContent = findViewById(R.id.text_q_content);
        layoutMcq = findViewById(R.id.layout_mcq_options);
        layoutFreeform = findViewById(R.id.layout_freeform);
        btnReveal = findViewById(R.id.btn_reveal_answer);
        cardFeedback = findViewById(R.id.card_feedback);
        textFeedbackTitle = findViewById(R.id.text_feedback_title);
        textFeedbackExp = findViewById(R.id.text_feedback_explanation);
        btnPrev = findViewById(R.id.btn_prev);
        btnNext = findViewById(R.id.btn_next);

        btnPrev.setOnClickListener(v -> {
            if (currentIndex > 0) {
                currentIndex--;
                renderQuestion();
            }
        });

        btnNext.setOnClickListener(v -> {
            if (currentIndex < quiz.getQuestions().size() - 1) {
                currentIndex++;
                renderQuestion();
            } else {
                showFinishDialog();
            }
        });

        btnReveal.setOnClickListener(v -> {
            ExamQuestion q = quiz.getQuestions().get(currentIndex);
            cardFeedback.setVisibility(View.VISIBLE);
            textFeedbackTitle.setText("Model Answer: " + q.getCorrectAnswer());
            textFeedbackTitle.setTextColor(getColor(R.color.primary));
            textFeedbackExp.setText(q.getExplanation().isEmpty() ? "No additional notes provided." : q.getExplanation());
        });

        renderQuestion();
    }

    private void renderQuestion() {
        ExamQuestion q = quiz.getQuestions().get(currentIndex);
        int total = quiz.getQuestions().size();

        int progress = (int) (((currentIndex + 1) / (float) total) * 100);
        progressBar.setProgress(progress);

        textCounter.setText(String.format("Question %d of %d", currentIndex + 1, total));
        textDifficulty.setText(capitalize(q.getDifficulty()));

        if (q.getSourceTopic() != null && !q.getSourceTopic().isEmpty()) {
            textTopic.setText("Topic: " + q.getSourceTopic());
            textTopic.setVisibility(View.VISIBLE);
        } else {
            textTopic.setVisibility(View.GONE);
        }

        textContent.setText(q.getQuestion());

        btnPrev.setEnabled(currentIndex > 0);
        btnNext.setText(currentIndex == total - 1 ? "Finish & Score" : "Next");

        cardFeedback.setVisibility(View.GONE);

        if (q.isMcq() && !q.getOptions().isEmpty()) {
            layoutMcq.setVisibility(View.VISIBLE);
            layoutFreeform.setVisibility(View.GONE);
            renderMcqOptions(q);
        } else {
            layoutMcq.setVisibility(View.GONE);
            layoutFreeform.setVisibility(View.VISIBLE);
            if (q.isMcq()) {
                textContent.append("\n\nThis saved question is missing its answer choices. Generate a new exam to replace it.");
            }
        }
    }

    private void renderMcqOptions(ExamQuestion q) {
        layoutMcq.removeAllViews();
        List<String> options = q.getOptions();
        String correctKey = q.getCorrectAnswer().trim();

        List<MaterialButton> optionButtons = new ArrayList<>();

        for (int i = 0; i < options.size(); i++) {
            String optText = options.get(i);
            MaterialButton btn = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            btn.setText(optText);
            btn.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
            btn.setCornerRadius(24);
            btn.setTextColor(getColor(R.color.on_surface));
            btn.setStrokeColor(ColorStateList.valueOf(getColor(R.color.card_stroke)));
            btn.setStrokeWidth(2);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            lp.setMargins(0, 0, 0, 16);
            btn.setLayoutParams(lp);

            final int optIndex = i;
            btn.setOnClickListener(v -> {
                // Disable all options
                for (MaterialButton b : optionButtons) {
                    b.setEnabled(false);
                }

                boolean isCorrect = isOptionCorrect(optText, optIndex, correctKey);

                if (isCorrect) {
                    btn.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.success)));
                    btn.setTextColor(getColor(R.color.on_primary));
                    btn.setStrokeColor(ColorStateList.valueOf(getColor(R.color.success)));

                    if (answersRecorded.get(currentIndex) == null) {
                        correctCount++;
                        answersRecorded.set(currentIndex, true);
                    }

                    textFeedbackTitle.setText("✓ Correct! (Answer: " + correctKey + ")");
                    textFeedbackTitle.setTextColor(getColor(R.color.success));
                } else {
                    btn.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.error)));
                    btn.setTextColor(getColor(R.color.on_primary));
                    btn.setStrokeColor(ColorStateList.valueOf(getColor(R.color.error)));

                    // Highlight the right option in green
                    for (int j = 0; j < options.size(); j++) {
                        if (isOptionCorrect(options.get(j), j, correctKey)) {
                            optionButtons.get(j).setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.success)));
                            optionButtons.get(j).setTextColor(getColor(R.color.on_primary));
                            optionButtons.get(j).setStrokeColor(ColorStateList.valueOf(getColor(R.color.success)));
                        }
                    }

                    if (answersRecorded.get(currentIndex) == null) {
                        answersRecorded.set(currentIndex, false);
                    }

                    textFeedbackTitle.setText("✗ Incorrect. (Correct Answer: " + correctKey + ")");
                    textFeedbackTitle.setTextColor(getColor(R.color.error));
                }

                cardFeedback.setVisibility(View.VISIBLE);
                textFeedbackExp.setText(q.getExplanation().isEmpty() ? "No additional explanation provided." : q.getExplanation());
            });

            optionButtons.add(btn);
            layoutMcq.addView(btn);
        }
    }

    private boolean isOptionCorrect(String optionText, int optionIndex, String correctKey) {
        String cleanOpt = optionText.trim().toUpperCase();
        String cleanKey = correctKey.trim().toUpperCase();

        char optLetter = (char) ('A' + optionIndex);
        if (cleanKey.equals(String.valueOf(optLetter))) {
            return true;
        }

        if (cleanOpt.startsWith(cleanKey + ".") || cleanOpt.startsWith(cleanKey + ")") || cleanOpt.startsWith(cleanKey + " ")) {
            return true;
        }

        return cleanOpt.equals(cleanKey);
    }

    private void showFinishDialog() {
        int total = quiz.getQuestions().size();
        int scorePct = (int) (((float) correctCount / total) * 100);

        String message = String.format("Score: %d / %d (%d%%)\n\nGreat practice session! Would you like to restart or return to the project?",
                correctCount, total, scorePct);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Practice Complete!")
                .setMessage(message)
                .setPositiveButton("Finish", (d, w) -> finish())
                .setNeutralButton("Restart", (d, w) -> {
                    currentIndex = 0;
                    correctCount = 0;
                    for (int i = 0; i < quiz.getQuestions().size(); i++) {
                        answersRecorded.set(i, null);
                    }
                    renderQuestion();
                })
                .show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return "";
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
