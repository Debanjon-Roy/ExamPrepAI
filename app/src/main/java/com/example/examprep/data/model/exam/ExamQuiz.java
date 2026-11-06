package com.example.examprep.data.model.exam;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ExamQuiz implements Serializable {
    private String examTitle;
    private int totalQuestions;
    private String difficulty;
    private String language;
    private List<ExamQuestion> questions = new ArrayList<>();

    public ExamQuiz() {}

    public static ExamQuiz fromJson(String jsonString) {
        ExamQuiz quiz = new ExamQuiz();
        try {
            JsonObject root = JsonParser.parseString(jsonString).getAsJsonObject();
            if (root.has("exam_title")) {
                quiz.setExamTitle(root.get("exam_title").getAsString());
            } else if (root.has("title")) {
                quiz.setExamTitle(root.get("title").getAsString());
            }

            if (root.has("total_questions")) {
                quiz.setTotalQuestions(root.get("total_questions").getAsInt());
            }
            if (root.has("difficulty")) {
                quiz.setDifficulty(root.get("difficulty").getAsString());
            }
            if (root.has("language")) {
                quiz.setLanguage(root.get("language").getAsString());
            }

            List<ExamQuestion> qList = new ArrayList<>();
            if (root.has("questions") && root.get("questions").isJsonArray()) {
                JsonArray arr = root.getAsJsonArray("questions");
                int index = 1;
                for (JsonElement elem : arr) {
                    if (elem.isJsonObject()) {
                        JsonObject qObj = elem.getAsJsonObject();
                        ExamQuestion q = new ExamQuestion();
                        q.setId(qObj.has("id") ? qObj.get("id").getAsInt() : index);
                        q.setType(qObj.has("type") ? qObj.get("type").getAsString() : "mcq");
                        q.setDifficulty(qObj.has("difficulty") ? qObj.get("difficulty").getAsString() : "medium");
                        q.setQuestion(qObj.has("question") ? qObj.get("question").getAsString() : "");

                        JsonElement choices = qObj.has("options") ? qObj.get("options") : qObj.get("choices");
                        q.setOptions(parseOptions(choices));

                        if (qObj.has("correct_answer")) {
                            q.setCorrectAnswer(qObj.get("correct_answer").getAsString());
                        } else if (qObj.has("answer")) {
                            q.setCorrectAnswer(qObj.get("answer").getAsString());
                        }

                        if (qObj.has("explanation")) {
                            q.setExplanation(qObj.get("explanation").getAsString());
                        }
                        if (qObj.has("source_topic")) {
                            q.setSourceTopic(qObj.get("source_topic").getAsString());
                        }

                        qList.add(q);
                        index++;
                    }
                }
            }
            quiz.setQuestions(qList);
            quiz.setTotalQuestions(qList.size());
        } catch (Exception ignored) {}

        return quiz;
    }

    private static List<String> parseOptions(JsonElement value) {
        List<String> options = new ArrayList<>();
        if (value == null || value.isJsonNull()) return options;
        if (value.isJsonArray()) {
            for (JsonElement item : value.getAsJsonArray()) {
                if (item.isJsonPrimitive()) {
                    options.add(item.getAsString());
                } else if (item.isJsonObject()) {
                    JsonObject object = item.getAsJsonObject();
                    JsonElement text = object.has("text") ? object.get("text") : object.get("value");
                    JsonElement label = object.has("label") ? object.get("label") : object.get("id");
                    if (text != null && text.isJsonPrimitive()) {
                        options.add(label != null && label.isJsonPrimitive()
                                ? label.getAsString() + ". " + text.getAsString() : text.getAsString());
                    }
                }
            }
        } else if (value.isJsonObject()) {
            // Labeled maps may arrive in any JSON property order; preserve A/B/C/D order.
            java.util.Map<String, JsonElement> sorted = new java.util.TreeMap<>(value.getAsJsonObject().asMap());
            for (java.util.Map.Entry<String, JsonElement> entry : sorted.entrySet()) {
                if (entry.getValue().isJsonPrimitive()) {
                    options.add(entry.getKey() + ". " + entry.getValue().getAsString());
                }
            }
        }
        return options;
    }

    public String getExamTitle() {
        return examTitle != null ? examTitle : "Practice Exam";
    }

    public void setExamTitle(String examTitle) {
        this.examTitle = examTitle;
    }

    public int getTotalQuestions() {
        return totalQuestions;
    }

    public void setTotalQuestions(int totalQuestions) {
        this.totalQuestions = totalQuestions;
    }

    public String getDifficulty() {
        return difficulty != null ? difficulty : "medium";
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public String getLanguage() {
        return language != null ? language : "en";
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public List<ExamQuestion> getQuestions() {
        return questions != null ? questions : new ArrayList<>();
    }

    public void setQuestions(List<ExamQuestion> questions) {
        this.questions = questions;
    }
}
