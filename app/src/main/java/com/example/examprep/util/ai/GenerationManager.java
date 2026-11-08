package com.example.examprep.util.ai;

import com.example.examprep.util.UserPaths;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.example.examprep.ExamPrepApp;
import com.example.examprep.data.db.AppDatabase;
import com.example.examprep.data.db.GenerationCacheDao;
import com.example.examprep.data.db.ProjectDao;
import com.example.examprep.data.db.ProjectFileDao;
import com.example.examprep.data.model.GenerationCache;
import com.example.examprep.data.model.Project;
import com.example.examprep.data.model.ProjectFile;

import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GenerationManager {

    public interface GenerationCallback {
        void onProgress(String message);
        void onSuccess(String resultJson, TokenUsage usage, boolean fromCache);
        void onError(String errorMessage);
    }

    public interface PdfGenerationCallback {
        void onProgress(String message);
        void onSuccess(java.io.File pdfFile, TokenUsage usage, boolean fromCache);
        void onError(String errorMessage);
    }

    public interface ExamGenerationCallback {
        void onProgress(String message);
        void onSuccess(com.example.examprep.data.model.exam.ExamQuiz quiz, java.io.File pdfFile, TokenUsage usage, boolean fromCache);
        void onError(String errorMessage);
    }

    public interface PresentationGenerationCallback {
        void onProgress(String message);
        void onSuccess(com.example.examprep.data.model.slide.PresentationDeck deck, java.io.File pptxFile, TokenUsage usage, boolean fromCache);
        void onError(String errorMessage);
    }

    private final Context context;
    private final AiProvider aiProvider;
    private final AppDatabase database;
    private final java.io.File ownerFilesDir;
    private final String ownerUid;
    private static final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public GenerationManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.ownerFilesDir = UserPaths.filesDir(context);
        this.ownerUid = UserPaths.currentUid();
        this.aiProvider = new OpenAiClient(context);
        this.database = ExamPrepApp.getInstance().getDatabase();
    }

    public void generateOrFetch(
            long projectId,
            @NonNull String generationType,
            @NonNull String systemInstruction,
            @NonNull String userPrompt,
            @NonNull GenerationCallback callback
    ) {
        executor.execute(() -> {
            postProgress(callback, "Checking for a saved result...");

            // 1. Load project and all extracted text
            Project project = database.projectDao().getProjectByIdSync(projectId);
            if (project == null) {
                postError(callback, "Project not found");
                return;
            }

            List<ProjectFile> files = database.projectFileDao().getFilesForProjectSync(projectId);
            StringBuilder materialsBuilder = new StringBuilder();

            for (ProjectFile file : files) {
                String text = file.getExtractedText() != null ? file.getExtractedText() : file.getOcrText();
                if (text != null && !text.trim().isEmpty()) {
                    materialsBuilder.append("=== Source Material: ").append(file.getFileName()).append(" ===\n");
                    materialsBuilder.append(text.trim()).append("\n\n");
                }
            }

            String fullMaterials = materialsBuilder.toString().trim();
            if (fullMaterials.isEmpty()) {
                postError(callback, "No extracted text found in project files. Please add materials or wait for text extraction to finish.");
                return;
            }

            // 2. Check local Room cache
            String cacheKeyData = OpenAiModelConfig.MODEL + "\n" + fullMaterials + "\n" + generationType + "\n" + systemInstruction + "\n" + userPrompt + "\n" + project.getLanguage();
            String inputHash = HashUtils.sha256(cacheKeyData);

            GenerationCacheDao cacheDao = database.generationCacheDao();
            GenerationCache cached = cacheDao.getCachedResult(projectId, generationType, inputHash);

            if (cached != null && cached.getResultJson() != null && !cached.getResultJson().isEmpty()) {
                postProgress(callback, "Loading your saved result...");
                postSuccess(callback, cached.getResultJson(), TokenUsage.zero(), true);
                return;
            }

            // 3. Check for chunking if materials are enormous (>100K tokens)
            List<String> chunks = TextChunker.chunkText(fullMaterials);
            if (chunks.size() > 1) {
                postProgress(callback, "Preparing a large set of materials. This may take a little longer...");
            } else {
                postProgress(callback, "Preparing your materials...");
            }

            // For single-chunk (standard cases up to ~75K tokens):
            String materialsToSend = chunks.get(0);

            aiProvider.generateStructuredText(
                    systemInstruction,
                    materialsToSend,
                    userPrompt,
                    new AiProvider.Callback() {
                        @Override
                        public void onProgress(String statusMessage) {
                            postProgress(callback, statusMessage);
                        }

                        @Override
                        public void onSuccess(@NonNull AiResponse response) {
                            // Save to Room cache
                            executor.execute(() -> {
                                GenerationCache newCache = new GenerationCache(projectId, generationType, inputHash);
                                newCache.setResultJson(response.getContent());
                                newCache.setTokenInputCount((int) response.getTokenUsage().getInputTokens());
                                newCache.setTokenOutputCount((int) response.getTokenUsage().getOutputTokens());
                                newCache.setCostUsd(response.getTokenUsage().getCostUsd());
                                newCache.setCreatedAt(new Date());
                                cacheDao.insert(newCache);
                            });

                            postSuccess(callback, response.getContent(), response.getTokenUsage(), false);
                        }

                        @Override
                        public void onError(@NonNull String errorMessage, Throwable error) {
                            postError(callback, errorMessage);
                        }
                    }
            );
        });
    }

    public void testAiConnection(@NonNull GenerationCallback callback) {
        aiProvider.testConnection(new AiProvider.Callback() {
            @Override
            public void onProgress(String statusMessage) {
                postProgress(callback, statusMessage);
            }

            @Override
            public void onSuccess(@NonNull AiResponse response) {
                postSuccess(callback, response.getContent(), response.getTokenUsage(), false);
            }

            @Override
            public void onError(@NonNull String errorMessage, Throwable error) {
                postError(callback, errorMessage);
            }
        });
    }

    public java.io.File getLatestGeneratedPdf(long projectId) {
        return latestExport(projectId, "guide");
    }

    public void generateStudyGuidePdf(
            long projectId,
            String language,
            String detailLevel,
            boolean verifyCompleteness,
            PdfGenerationCallback callback
    ) {
        String langName = language.equals("bn") ? "Bengali (বাংলা)" : "English";
        String systemInstruction = "You are ExamForge. Create a complete, exhaustive study guide PDF outline covering every single topic found in the source materials. " +
                "Language must be " + langName + ". Detail level: " + detailLevel + ". " +
                "Do NOT invent facts outside the materials; clearly label anything added from general knowledge under 'general_knowledge_additions'. " +
                "Output strictly JSON with this schema: " +
                "{\"title\":\"Title\",\"subtitle\":\"Subtitle\",\"topics_covered\":[\"topic 1\",\"topic 2\"]," +
                "\"sections\":[{\"heading\":\"1. Heading\",\"summary\":\"Summary text\",\"key_concepts\":[{\"concept\":\"Term\",\"explanation\":\"Meaning\"}]," +
                "\"content_paragraphs\":[\"Paragraph 1\",\"Paragraph 2\"],\"table\":{\"headers\":[\"Col1\",\"Col2\"],\"rows\":[[\"A\",\"B\"]]}," +
                "\"important_notes\":[\"Note 1\"],\"general_knowledge_additions\":[]}]}";

        String userPrompt = "Generate the complete study guide JSON covering all uploaded materials in " + langName + ".";

        generateOrFetch(projectId, "study_guide_" + detailLevel, systemInstruction, userPrompt, new GenerationCallback() {
            @Override
            public void onProgress(String message) {
                mainHandler.post(() -> callback.onProgress(message));
            }

            @Override
            public void onSuccess(String resultJson, TokenUsage usage, boolean fromCache) {
                executor.execute(() -> {
                    String finalJson = resultJson;

                    // Gap verification step if live call
                    if (verifyCompleteness && !fromCache) {
                        mainHandler.post(() -> callback.onProgress("Verifying topic completeness against source materials..."));
                        finalJson = runVerificationGapFiller(projectId, resultJson, langName);
                    }

                    mainHandler.post(() -> callback.onProgress("Preparing your PDF..."));

                    try {
                        Project project = database.projectDao().getProjectByIdSync(projectId);
                        String projectName = project != null ? project.getName() : "Project";

                        java.io.File pdfFile = com.example.examprep.util.pdf.StudyGuidePdfGenerator.generatePdf(
                                context,
                                projectId,
                                projectName,
                                finalJson,
                                language, ownerFilesDir
                        );

                        com.example.examprep.data.cloud.StudyGuideBackup.save(
                                ownerUid, projectId, projectName, language, detailLevel, finalJson, pdfFile);
                        mainHandler.post(() -> callback.onSuccess(pdfFile, usage, fromCache));
                    } catch (Exception e) {
                        android.util.Log.e("GenerationManager", "PDF rendering failed", e);
                        mainHandler.post(() -> callback.onError("Could not create the PDF. Please try again."));
                    }
                });
            }

            @Override
            public void onError(String errorMessage) {
                mainHandler.post(() -> callback.onError(errorMessage));
            }
        });
    }

    private String runVerificationGapFiller(long projectId, String originalJson, String langName) {
        try {
            List<ProjectFile> files = database.projectFileDao().getFilesForProjectSync(projectId);
            StringBuilder sb = new StringBuilder();
            for (ProjectFile f : files) {
                String text = f.getExtractedText() != null ? f.getExtractedText() : f.getOcrText();
                if (text != null && !text.trim().isEmpty()) {
                    sb.append(text).append("\n\n");
                }
            }

            String materials = sb.toString();
            String verifySystem = "You are a quality assurance auditor for academic study guides. Compare the generated study guide against the source materials. " +
                    "Find any topics, theorems, or definitions present in the source that were omitted. " +
                    "Return JSON: {\"gaps_found\": true/false, \"missing_sections\": [{\"heading\":\"...\",\"summary\":\"...\",\"key_concepts\":[],\"content_paragraphs\":[\"...\"],\"important_notes\":[]}]}";

            String verifyUser = "Source Materials:\n" + materials + "\n\nExisting Guide Outline:\n" + originalJson +
                    "\n\nIdentify missed topics in " + langName + " and fill gaps.";

            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.atomic.AtomicReference<String> ref = new java.util.concurrent.atomic.AtomicReference<>(null);

            aiProvider.generateStructuredText(verifySystem, "", verifyUser, new AiProvider.Callback() {
                @Override
                public void onProgress(String statusMessage) {}

                @Override
                public void onSuccess(@NonNull AiResponse response) {
                    ref.set(response.getContent());
                    latch.countDown();
                }

                @Override
                public void onError(@NonNull String errorMessage, Throwable error) {
                    latch.countDown();
                }
            });

            latch.await(60, java.util.concurrent.TimeUnit.SECONDS);
            String vJson = ref.get();
            if (vJson != null) {
                com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(originalJson).getAsJsonObject();
                com.google.gson.JsonObject vObj = com.google.gson.JsonParser.parseString(vJson).getAsJsonObject();

                if (vObj.has("gaps_found") && vObj.get("gaps_found").getAsBoolean() && vObj.has("missing_sections")) {
                    com.google.gson.JsonArray missing = vObj.getAsJsonArray("missing_sections");
                    if (missing.size() > 0 && root.has("sections")) {
                        com.google.gson.JsonArray sections = root.getAsJsonArray("sections");
                        for (com.google.gson.JsonElement m : missing) {
                            sections.add(m);
                        }
                        return root.toString();
                    }
                }
            }
        } catch (Exception ignored) {}

        return originalJson;
    }

    public java.io.File getLatestExamPdf(long projectId) {
        return latestExport(projectId, "exam");
    }

    public com.example.examprep.data.model.exam.ExamQuiz getLatestExamQuiz(long projectId) {
        com.example.examprep.data.model.GenerationCache cached = database.generationCacheDao().getLatestCachedResult(projectId, "exam_questions");
        if (cached != null && cached.getResultJson() != null) {
            return com.example.examprep.data.model.exam.ExamQuiz.fromJson(cached.getResultJson());
        }
        return null;
    }

    public void generateExamQuestions(
            long projectId,
            int count,
            String difficulty,
            String type,
            String language,
            ExamGenerationCallback callback
    ) {
        String langName = language.equals("bn") ? "Bengali (বাংলা)" : "English";
        String systemInstruction = "You are an expert university examiner. Generate realistic exam practice questions strictly based on the uploaded materials. " +
                "Language must be " + langName + ". Target difficulty: " + difficulty + ". Target type: " + type + ". " +
                "Include step-by-step reasoning explaining why the correct answer is right and why others are wrong under 'explanation'. " +
                "For every multiple-choice question use type 'mcq', include exactly four nonempty strings in 'options' " +
                "labeled A. through D., and set 'correct_answer' to the correct letter. Never omit MCQ options. " +
                "For written questions use type 'short_answer' or 'essay' with an empty options array. " +
                "For written questions, correct_answer must contain the full model answer, never a letter or an empty value; explanation must include the marking rubric and key points. " +
                "Output strictly JSON with this schema: " +
                "{\"exam_title\":\"Exam Practice Test\",\"total_questions\":" + count + ",\"difficulty\":\"" + difficulty + "\",\"language\":\"" + language + "\"," +
                "\"questions\":[{\"id\":1,\"type\":\"" + (type.equals("mixed") ? "mcq" : type) + "\",\"difficulty\":\"" + difficulty + "\",\"question\":\"Question text\"," +
                "\"options\":[\"A. Option 1\",\"B. Option 2\",\"C. Option 3\",\"D. Option 4\"],\"correct_answer\":\"B\",\"explanation\":\"Detailed step by step logic...\",\"source_topic\":\"Topic Name\"}]}";

        String userPrompt = "Generate " + count + " probable exam questions covering the source materials now.";

        generateOrFetch(projectId, "exam_questions", systemInstruction, userPrompt, new GenerationCallback() {
            @Override
            public void onProgress(String message) {
                mainHandler.post(() -> callback.onProgress(message));
            }

            @Override
            public void onSuccess(String resultJson, TokenUsage usage, boolean fromCache) {
                executor.execute(() -> {
                    mainHandler.post(() -> callback.onProgress("Preparing your questions and answer key..."));
                    try {
                        com.example.examprep.data.model.exam.ExamQuiz quiz = com.example.examprep.data.model.exam.ExamQuiz.fromJson(resultJson);
                        for (com.example.examprep.data.model.exam.ExamQuestion question : quiz.getQuestions()) {
                            if (question.isMcq() && question.getOptions().size() < 2) {
                                database.generationCacheDao().deleteCacheByType(projectId, "exam_questions");
                                throw new IllegalArgumentException("Question " + question.getId()
                                        + " is missing its answer choices. Generate a new exam to try again.");
                            }
                        }
                        Project project = database.projectDao().getProjectByIdSync(projectId);
                        String projectName = project != null ? project.getName() : "Practice Exam";

                        java.io.File pdfFile = com.example.examprep.util.pdf.ExamPdfGenerator.generateExamPdf(
                                context,
                                projectId,
                                projectName,
                                quiz, ownerFilesDir
                        );

                        com.example.examprep.data.cloud.StudyGuideBackup.saveQuestions(
                                ownerUid, projectId, projectName, language, resultJson, pdfFile);
                        mainHandler.post(() -> callback.onSuccess(quiz, pdfFile, usage, fromCache));
                    } catch (Exception e) {
                        android.util.Log.e("GenerationManager", "Exam rendering failed", e);
                        mainHandler.post(() -> callback.onError("Could not create the questions and answer key. Please generate them again."));
                    }
                });
            }

            @Override
            public void onError(String errorMessage) {
                mainHandler.post(() -> callback.onError(errorMessage));
            }
        });
    }

    public java.io.File getLatestPresentationFile(long projectId) {
        return latestExport(projectId, "slides");
    }

    public java.io.File getLatestVideoFile(long projectId) {
        return latestExport(projectId, "video");
    }

    public com.example.examprep.data.model.slide.PresentationDeck getLatestPresentationDeck(long projectId) {
        com.example.examprep.data.model.GenerationCache cached = database.generationCacheDao().getLatestCachedResult(projectId, "presentation_outline");
        if (cached != null && cached.getResultJson() != null) {
            return com.example.examprep.data.model.slide.PresentationDeck.fromJson(cached.getResultJson());
        }
        return null;
    }

    public void generatePresentationDeck(
            long projectId,
            String language,
            String detailLevel,
            PresentationGenerationCallback callback
    ) {
        String langName = language.equals("bn") ? "Bengali (বাংলা)" : "English";
        int targetSlideCount = "summary".equals(detailLevel) ? 6 : ("exhaustive".equals(detailLevel) ? 14 : 9);

        String systemInstruction = "You are an expert presentation designer and university lecturer. " +
                "Generate a professional, structured presentation deck covering all key concepts from the uploaded study material. " +
                "Language must be " + langName + ". Target slide count: approximately " + targetSlideCount + " slides. " +
                "Ground all facts strictly in the materials. If any background knowledge is added, set \"from_general_knowledge\": true on that slide. " +
                "IMPORTANT: For every slide, provide a comprehensive, engaging 'speaker_notes' script (at least 2-4 sentences explaining the slide like a university professor would during a lecture). This will be used for narration audio. " +
                "Include meaningful editable graphics on at least half of content slides. Add an optional visual object: " +
                "\"visual\":{\"type\":\"process|concept|comparison|bar|line|pie\",\"title\":\"What the graphic explains\",\"unit\":\"Source unit\",\"labels\":[\"Short label\",\"Short label\"],\"values\":[12,24]}. " +
                "Prefer actual charts over text boxes when the source provides numerical data: bar for comparing quantities, line for ordered trends, pie only for nonnegative parts of a whole. " +
                "Charts require 2-8 labels and exactly one finite numeric value per label. Copy quantities and units faithfully from the source. " +
                "Never fabricate chart values or imply that unrelated categories form a trend. If suitable data is absent, use a concept, comparison or process diagram. " +
                "Do not add values to non-chart diagrams. PPTX applies subtle fade transitions and a graphic entrance animation automatically. " +
                "Use process for 2-4 ordered steps, concept for a central idea followed by 1-3 related ideas, and comparison for 2-4 contrasting concepts. " +
                "Use source-grounded labels of at most 8 words each. Never invent data, relationships or image URLs. " +
                "On visual slides keep bullet_points to 2-3 short supporting points; explain details in speaker_notes. Omit visual when no meaningful graphic fits. " +
                "Output strictly JSON with this schema: " +
                "{\"presentation_title\":\"Title of Deck\",\"subtitle\":\"Core Review\",\"language\":\"" + language + "\",\"total_slides\":" + targetSlideCount + "," +
                "\"slides\":[" +
                "{\"slide_number\":1,\"layout\":\"title\",\"header\":\"Project Title\",\"subheader\":\"Comprehensive Topic Review\",\"speaker_notes\":\"Welcome to this review session...\"}," +
                "{\"slide_number\":2,\"layout\":\"agenda\",\"header\":\"Agenda & Learning Objectives\",\"bullet_points\":[\"Topic 1: ...\",\"Topic 2: ...\"],\"speaker_notes\":\"Here is an overview of the key concepts we will explore today...\"}," +
                "{\"slide_number\":3,\"layout\":\"content\",\"header\":\"Topic Title\",\"subheader\":\"Key Mechanisms & Concepts\",\"bullet_points\":[\"Core point 1\",\"Key formula / principle\",\"Application\"],\"callout_title\":\"Exam Essential\",\"callout_content\":\"Crucial takeaway to remember...\",\"speaker_notes\":\"Let us examine this critical principle in detail...\",\"from_general_knowledge\":false}," +
                "{\"slide_number\":4,\"layout\":\"summary\",\"header\":\"Summary & Exam Takeaways\",\"bullet_points\":[\"Core takeaway 1\",\"Core takeaway 2\"],\"callout_title\":\"Next Steps\",\"callout_content\":\"Practice with exam questions\",\"speaker_notes\":\"To summarize our review...\"}" +
                "]}";

        String userPrompt = "Build the structured presentation deck and narration outline covering all source materials in " + langName + " now.";

        generateOrFetch(projectId, "presentation_outline", systemInstruction, userPrompt, new GenerationCallback() {
            @Override
            public void onProgress(String message) {
                mainHandler.post(() -> callback.onProgress(message));
            }

            @Override
            public void onSuccess(String resultJson, TokenUsage usage, boolean fromCache) {
                executor.execute(() -> {
                    mainHandler.post(() -> callback.onProgress("Preparing your presentation..."));
                    try {
                        com.example.examprep.data.cloud.RestoredPresentation.verifyJson(resultJson);
                        com.example.examprep.data.model.slide.PresentationDeck deck =
                                com.example.examprep.data.model.slide.PresentationDeck.fromJson(resultJson);
                        Project project = database.projectDao().getProjectByIdSync(projectId);
                        String projectName = project != null ? project.getName() : "Study Deck";

                        java.io.File pptxFile = com.example.examprep.util.pptx.PptxGenerator.generatePresentation(
                                context,
                                projectId,
                                projectName,
                                deck, ownerFilesDir
                        );

                        com.example.examprep.data.cloud.StudyGuideBackup.savePresentation(
                                ownerUid, projectId, projectName, language, resultJson, pptxFile);
                        mainHandler.post(() -> callback.onSuccess(deck, pptxFile, usage, fromCache));
                    } catch (Exception e) {
                        android.util.Log.e("GenerationManager", "Presentation rendering failed", e);
                        mainHandler.post(() -> callback.onError("Could not create the presentation. Please try again."));
                    }
                });
            }

            @Override
            public void onError(String errorMessage) {
                mainHandler.post(() -> callback.onError(errorMessage));
            }
        });
    }

    private void postProgress(GenerationCallback callback, String msg) {
        mainHandler.post(() -> callback.onProgress(msg));
    }

    private java.io.File latestExport(long projectId, String kind) {
        try {
            for (com.example.examprep.util.GeneratedFiles.Item item :
                    new com.example.examprep.util.GeneratedFiles(ownerFilesDir, projectId).list()) {
                if (kind.equals(item.kind)) return item.file;
            }
        } catch (java.io.IOException error) {
            android.util.Log.w("GenerationManager", "Could not list saved files", error);
        }
        return null;
    }

    private void postSuccess(GenerationCallback callback, String json, TokenUsage usage, boolean fromCache) {
        mainHandler.post(() -> callback.onSuccess(json, usage, fromCache));
    }

    private void postError(GenerationCallback callback, String error) {
        mainHandler.post(() -> callback.onError(error));
    }
}
