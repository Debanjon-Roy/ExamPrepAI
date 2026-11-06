package com.example.examprep.data.model.exam;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ExamQuestion implements Serializable {
    private int id;
    private String type; // "mcq", "short_answer", "essay"
    private String difficulty; // "easy", "medium", "hard"
    private String question;
    private List<String> options = new ArrayList<>();
    private String correctAnswer;
    private String explanation;
    private String sourceTopic;

    public ExamQuestion() {}

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getType() {
        return type != null ? type : "mcq";
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDifficulty() {
        return difficulty != null ? difficulty : "medium";
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public String getQuestion() {
        return question != null ? question : "";
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public List<String> getOptions() {
        return options != null ? options : new ArrayList<>();
    }

    public void setOptions(List<String> options) {
        this.options = options;
    }

    public String getCorrectAnswer() {
        return correctAnswer != null ? correctAnswer : "";
    }

    public void setCorrectAnswer(String correctAnswer) {
        this.correctAnswer = correctAnswer;
    }

    public String getExplanation() {
        return explanation != null ? explanation : "";
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public String getSourceTopic() {
        return sourceTopic != null ? sourceTopic : "";
    }

    public void setSourceTopic(String sourceTopic) {
        this.sourceTopic = sourceTopic;
    }

    public boolean isMcq() {
        String normalized = getType().trim().toLowerCase(java.util.Locale.ROOT)
                .replace('-', '_').replace(' ', '_');
        if ("short".equals(normalized) || "short_answer".equals(normalized)
                || "short_question".equals(normalized) || "essay".equals(normalized)) return false;
        return "mcq".equals(normalized) || "multiple_choice".equals(normalized)
                || "multiple_choice_question".equals(normalized) || !getOptions().isEmpty();
    }
}
