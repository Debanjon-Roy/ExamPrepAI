package com.example.examprep.ui.project;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import com.example.examprep.R;
import com.example.examprep.data.model.Project;
import com.example.examprep.data.model.ProjectFile;
import com.example.examprep.data.model.exam.ExamQuiz;
import com.example.examprep.data.model.slide.PresentationDeck;
import com.example.examprep.ui.settings.SettingsActivity;
import com.example.examprep.util.SecurePrefs;
import com.example.examprep.util.ai.GenerationManager;
import com.example.examprep.util.ai.TokenUsage;
import com.example.examprep.util.video.TtsNarrationHelper;
import com.example.examprep.util.video.VideoGenerationWorker;
import android.speech.tts.TextToSpeech;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ProjectFragment extends Fragment {

    private ProjectViewModel viewModel;
    private FileAdapter fileAdapter;
    private long projectId;

    private ActivityResultLauncher<String[]> filePickerLauncher;
    private ActivityResultLauncher<Uri> cameraLauncher;
    private ActivityResultLauncher<String> cameraPermissionLauncher;

    private Uri pendingCameraUri;
    private File pendingCameraFile;
    private GenerationManager generationManager;
    private static final java.util.concurrent.ExecutorService CARD_IO = java.util.concurrent.Executors.newFixedThreadPool(2);
    private final android.os.Handler uiHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final java.util.List<AlertDialog> generationDialogs = new java.util.ArrayList<>();

    private MaterialCardView cardLatestGuide;
    private TextView textGuideMeta;
    private File currentPdfFile;

    private MaterialCardView cardLatestExam;
    private TextView textExamTitle;
    private TextView textExamMeta;
    private File currentExamPdfFile;
    private ExamQuiz currentExamQuiz;

    private MaterialCardView cardLatestSlides;
    private TextView textSlidesTitle;
    private TextView textSlidesMeta;
    private File currentPptxFile;
    private PresentationDeck currentPresentationDeck;

    private MaterialCardView cardLatestVideo;
    private TextView textVideoTitle;
    private TextView textVideoMeta;
    private File currentVideoFile;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            projectId = getArguments().getLong("projectId", -1);
        }

        generationManager = new GenerationManager(requireContext());

        // 1. SAF file picker launcher (multi-select)
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenMultipleDocuments(),
                uris -> {
                    if (uris != null && !uris.isEmpty()) {
                        viewModel.importFiles(uris);
                        Toast.makeText(requireContext(), "Importing " + uris.size() + " file(s)...", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        // 2. Camera picture launcher
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                success -> {
                    if (Boolean.TRUE.equals(success) && pendingCameraUri != null && pendingCameraFile != null) {
                        viewModel.importCameraPhoto(pendingCameraUri, pendingCameraFile);
                        Toast.makeText(requireContext(), "Photo captured, extracting text...", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        // 3. Camera runtime permission launcher
        cameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (Boolean.TRUE.equals(isGranted)) {
                        launchCamera();
                    } else {
                        Toast.makeText(requireContext(), "Camera permission required to capture notes", Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_project, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView textTitleLarge = view.findViewById(R.id.text_project_title_large);
        TextView badgeLang = view.findViewById(R.id.badge_language);
        TextView badgeDetail = view.findViewById(R.id.badge_detail);
        TextView textProjectInfo = view.findViewById(R.id.text_project_info);

        cardLatestGuide = view.findViewById(R.id.card_latest_guide);
        textGuideMeta = view.findViewById(R.id.text_guide_meta);
        MaterialButton btnOpenGuide = view.findViewById(R.id.btn_open_guide);
        MaterialButton btnShareGuide = view.findViewById(R.id.btn_share_guide);

        cardLatestExam = view.findViewById(R.id.card_latest_exam);
        textExamTitle = view.findViewById(R.id.text_exam_title);
        textExamMeta = view.findViewById(R.id.text_exam_meta);
        MaterialButton btnStartPractice = view.findViewById(R.id.btn_start_practice);
        MaterialButton btnOpenExamPdf = view.findViewById(R.id.btn_open_exam_pdf);
        MaterialButton btnShareExamPdf = view.findViewById(R.id.btn_share_exam_pdf);

        cardLatestSlides = view.findViewById(R.id.card_latest_slides);
        textSlidesTitle = view.findViewById(R.id.text_slides_title);
        textSlidesMeta = view.findViewById(R.id.text_slides_meta);
        MaterialButton btnOpenSlides = view.findViewById(R.id.btn_open_slides);
        MaterialButton btnShareSlides = view.findViewById(R.id.btn_share_slides);

        cardLatestVideo = view.findViewById(R.id.card_latest_video);
        textVideoTitle = view.findViewById(R.id.text_video_title);
        textVideoMeta = view.findViewById(R.id.text_video_meta);
        MaterialButton btnPlayVideo = view.findViewById(R.id.btn_play_video);
        MaterialButton btnShareVideo = view.findViewById(R.id.btn_share_video);

        RecyclerView recyclerFiles = view.findViewById(R.id.recycler_files);
        TextView emptyFiles = view.findViewById(R.id.text_empty_files);
        MaterialButton btnAddFiles = view.findViewById(R.id.btn_add_files);

        MaterialButton btnGenerateGuide = view.findViewById(R.id.btn_generate_guide);
        MaterialButton btnGenerateSlides = view.findViewById(R.id.btn_generate_slides);
        MaterialButton btnGenerateQuestions = view.findViewById(R.id.btn_generate_questions);
        MaterialButton btnGenerateVideo = view.findViewById(R.id.btn_generate_video);
        MaterialButton btnTestAi = view.findViewById(R.id.btn_test_ai);

        recyclerFiles.setLayoutManager(new LinearLayoutManager(requireContext()));
        fileAdapter = new FileAdapter(
                this::showFilePreviewDialog,
                this::showDeleteFileDialog
        );
        recyclerFiles.setAdapter(fileAdapter);

        viewModel = new ViewModelProvider(this, new ProjectViewModelFactory(
                requireActivity().getApplication(), projectId)).get(ProjectViewModel.class);

        viewModel.getProject().observe(getViewLifecycleOwner(), project -> {
            if (project != null) {
                if (isAdded() && getActivity() != null) {
                    requireActivity().setTitle(project.getName());
                }
                textTitleLarge.setText(project.getName());
                badgeLang.setText("bn".equals(project.getLanguage()) ? "বাংলা (Bengali)" : "English");
                badgeDetail.setText(capitalize(project.getDetailLevel() != null ? project.getDetailLevel() : "standard") + " Detail");

                String desc = project.getDescription();
                if (desc != null && !desc.trim().isEmpty()) {
                    textProjectInfo.setText(desc.trim());
                    textProjectInfo.setVisibility(View.VISIBLE);
                } else {
                    textProjectInfo.setVisibility(View.GONE);
                }
            }
        });

        viewModel.getFiles().observe(getViewLifecycleOwner(), files -> {
            fileAdapter.submitList(files);
            boolean isEmpty = files == null || files.isEmpty();
            emptyFiles.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            recyclerFiles.setVisibility(isEmpty ? View.GONE : View.VISIBLE);

            boolean hasFiles = !isEmpty;
            btnGenerateGuide.setEnabled(hasFiles);
            btnGenerateSlides.setEnabled(hasFiles);
            btnGenerateQuestions.setEnabled(hasFiles);
            btnGenerateVideo.setEnabled(hasFiles);
        });

        viewModel.getStatusMessage().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });

        // Check for previously generated PDF, Exam, Slides and Video
        refreshLatestPdfCard();
        refreshLatestExamCard();
        refreshLatestSlidesCard();
        refreshLatestVideoCard();

        btnOpenGuide.setOnClickListener(v -> openPdf(currentPdfFile));
        btnShareGuide.setOnClickListener(v -> sharePdf(currentPdfFile));

        btnStartPractice.setOnClickListener(v -> {
            if (currentExamQuiz != null) {
                Intent intent = new Intent(requireContext(), ExamPracticeActivity.class);
                intent.putExtra(ExamPracticeActivity.EXTRA_QUIZ, currentExamQuiz);
                startActivity(intent);
            } else {
                Toast.makeText(requireContext(), "No exam quiz data loaded yet", Toast.LENGTH_SHORT).show();
            }
        });
        btnOpenExamPdf.setOnClickListener(v -> openPdf(currentExamPdfFile));
        btnShareExamPdf.setOnClickListener(v -> sharePdf(currentExamPdfFile));

        btnOpenSlides.setOnClickListener(v -> openPptx(currentPptxFile));
        btnShareSlides.setOnClickListener(v -> sharePptx(currentPptxFile));

        btnPlayVideo.setOnClickListener(v -> playVideo(currentVideoFile));
        btnShareVideo.setOnClickListener(v -> shareVideo(currentVideoFile));

        btnAddFiles.setOnClickListener(v -> showAddMaterialsDialog());

        SecurePrefs securePrefs = new SecurePrefs(requireContext());

        // Stage 4: Generate Study Guide PDF
        btnGenerateGuide.setOnClickListener(v -> {
            if (!securePrefs.hasApiKey()) {
                showMissingApiKeyDialog();
                return;
            }
            showGenerateGuideOptionsDialog();
        });

        // Stage 3: Test AI Pipeline
        btnTestAi.setOnClickListener(v -> {
            if (!securePrefs.hasApiKey()) {
                showMissingApiKeyDialog();
                return;
            }
            runAiTestPipeline();
        });

        // Stage 6: Generate Presentation Slides (.pptx)
        btnGenerateSlides.setOnClickListener(v -> {
            if (!securePrefs.hasApiKey()) {
                showMissingApiKeyDialog();
                return;
            }
            showGenerateSlidesOptionsDialog();
        });

        // Stage 5: Generate Exam Practice Questions
        btnGenerateQuestions.setOnClickListener(v -> {
            if (!securePrefs.hasApiKey()) {
                showMissingApiKeyDialog();
                return;
            }
            showGenerateQuestionsOptionsDialog();
        });

        // Stage 7: Generate Narrated Video
        btnGenerateVideo.setOnClickListener(v -> {
            if (!securePrefs.hasApiKey()) {
                showMissingApiKeyDialog();
                return;
            }
            showGenerateVideoOptionsDialog();
        });
    }

    @Override
    public void onDestroyView() {
        for (AlertDialog dialog : new java.util.ArrayList<>(generationDialogs)) dialog.dismiss();
        generationDialogs.clear();
        uiHandler.removeCallbacksAndMessages(null);
        cardLatestGuide = null;
        cardLatestExam = null;
        cardLatestSlides = null;
        cardLatestVideo = null;
        super.onDestroyView();
    }

    private void refreshLatestPdfCard() {
        final View requestedView = getView();
        CARD_IO.execute(() -> {
            try {
                File pdf = generationManager.getLatestGeneratedPdf(projectId);
                uiHandler.post(() -> {
                    if (!isAdded() || requestedView == null || getView() != requestedView) return;
                    currentPdfFile = pdf;
                    if (cardLatestGuide != null) {
                        if (currentPdfFile != null && currentPdfFile.exists()) {
                            cardLatestGuide.setVisibility(View.VISIBLE);
                            long sizeKb = currentPdfFile.length() / 1024;
                            String dateStr = new SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()).format(new Date(currentPdfFile.lastModified()));
                            if (textGuideMeta != null) {
                                textGuideMeta.setText(String.format("%s • %,d KB", dateStr, sizeKb));
                            }
                        } else {
                            cardLatestGuide.setVisibility(View.GONE);
                        }
                    }
                });
            } catch (Exception e) {
                android.util.Log.e("ProjectFragment", "Error refreshing PDF card", e);
            }
        });
    }

    private void refreshLatestExamCard() {
        final View requestedView = getView();
        CARD_IO.execute(() -> {
            try {
                File pdf = generationManager.getLatestExamPdf(projectId);
                ExamQuiz quiz = generationManager.getLatestExamQuiz(projectId);

                uiHandler.post(() -> {
                    if (!isAdded() || requestedView == null || getView() != requestedView) return;
                    currentExamPdfFile = pdf;
                    currentExamQuiz = quiz;

                    if (cardLatestExam == null) return;

                    if (currentExamQuiz != null) {
                        cardLatestExam.setVisibility(View.VISIBLE);
                        if (textExamTitle != null) {
                            if (currentExamQuiz.getExamTitle() != null && !currentExamQuiz.getExamTitle().isEmpty()) {
                                textExamTitle.setText(currentExamQuiz.getExamTitle());
                            } else {
                                textExamTitle.setText("Exam Practice Test Ready");
                            }
                        }

                        int count = currentExamQuiz.getQuestions() != null ? currentExamQuiz.getQuestions().size() : 0;
                        String diff = currentExamQuiz.getDifficulty() != null ? capitalize(currentExamQuiz.getDifficulty()) : "Mixed";
                        String meta = count + " Questions • " + diff + " Difficulty";

                        if (currentExamPdfFile != null && currentExamPdfFile.exists()) {
                            long sizeKb = currentExamPdfFile.length() / 1024;
                            meta += String.format(Locale.getDefault(), " • Printable PDF (%,d KB)", sizeKb);
                        }
                        if (textExamMeta != null) textExamMeta.setText(meta);
                    } else if (currentExamPdfFile != null && currentExamPdfFile.exists()) {
                        cardLatestExam.setVisibility(View.VISIBLE);
                        if (textExamTitle != null) textExamTitle.setText("Exam Practice Paper Ready");
                        long sizeKb = currentExamPdfFile.length() / 1024;
                        if (textExamMeta != null) textExamMeta.setText(String.format(Locale.getDefault(), "Printable PDF (%,d KB)", sizeKb));
                    } else {
                        cardLatestExam.setVisibility(View.GONE);
                    }
                });
            } catch (Exception e) {
                android.util.Log.e("ProjectFragment", "Error refreshing Exam card", e);
            }
        });
    }

    private void refreshLatestSlidesCard() {
        final View requestedView = getView();
        CARD_IO.execute(() -> {
            try {
                File pptx = generationManager.getLatestPresentationFile(projectId);
                PresentationDeck deck = generationManager.getLatestPresentationDeck(projectId);

                uiHandler.post(() -> {
                    if (!isAdded() || requestedView == null || getView() != requestedView) return;
                    currentPptxFile = pptx;
                    currentPresentationDeck = deck;

                    if (cardLatestSlides == null) return;

                    if (currentPresentationDeck != null) {
                        cardLatestSlides.setVisibility(View.VISIBLE);
                        if (textSlidesTitle != null) {
                            if (currentPresentationDeck.getPresentationTitle() != null && !currentPresentationDeck.getPresentationTitle().isEmpty()) {
                                textSlidesTitle.setText(currentPresentationDeck.getPresentationTitle());
                            } else {
                                textSlidesTitle.setText("Presentation Deck Ready");
                            }
                        }

                        int count = currentPresentationDeck.getSlides() != null ? currentPresentationDeck.getSlides().size() : 0;
                        String meta = count + " Slides • Widescreen 16:9 • Narration Script Included";

                        if (currentPptxFile != null && currentPptxFile.exists()) {
                            long sizeKb = currentPptxFile.length() / 1024;
                            meta += String.format(Locale.getDefault(), " (%,d KB)", sizeKb);
                        }
                        if (textSlidesMeta != null) textSlidesMeta.setText(meta);
                    } else if (currentPptxFile != null && currentPptxFile.exists()) {
                        cardLatestSlides.setVisibility(View.VISIBLE);
                        if (textSlidesTitle != null) textSlidesTitle.setText("Presentation Deck Ready");
                        long sizeKb = currentPptxFile.length() / 1024;
                        if (textSlidesMeta != null) textSlidesMeta.setText(String.format(Locale.getDefault(), "PowerPoint (.pptx) • %,d KB", sizeKb));
                    } else {
                        cardLatestSlides.setVisibility(View.GONE);
                    }
                });
            } catch (Exception e) {
                android.util.Log.e("ProjectFragment", "Error refreshing Slides card", e);
            }
        });
    }

    private void refreshLatestVideoCard() {
        final View requestedView = getView();
        CARD_IO.execute(() -> {
            try {
                File video = generationManager.getLatestVideoFile(projectId);

                uiHandler.post(() -> {
                    if (!isAdded() || requestedView == null || getView() != requestedView) return;
                    currentVideoFile = video;
                    if (cardLatestVideo == null) return;

                    if (currentVideoFile != null && currentVideoFile.exists()) {
                        cardLatestVideo.setVisibility(View.VISIBLE);
                        long sizeMb = currentVideoFile.length() / (1024 * 1024);
                        if (sizeMb < 1) sizeMb = 1;
                        String dateStr = new SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()).format(new Date(currentVideoFile.lastModified()));
                        if (textVideoTitle != null) textVideoTitle.setText("Narrated Video Ready");
                        if (textVideoMeta != null) textVideoMeta.setText(String.format(Locale.getDefault(), "%s • 720p HD MP4 (%,d MB)", dateStr, sizeMb));
                    } else {
                        cardLatestVideo.setVisibility(View.GONE);
                    }
                });
            } catch (Exception e) {
                android.util.Log.e("ProjectFragment", "Error refreshing Video card", e);
            }
        });
    }

    private void showGenerateQuestionsOptionsDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_generate_questions_options, null);

        RadioGroup rgCount = dialogView.findViewById(R.id.rg_q_count);
        RadioGroup rgType = dialogView.findViewById(R.id.rg_q_type);
        RadioGroup rgDiff = dialogView.findViewById(R.id.rg_q_difficulty);
        RadioButton rbLangBn = dialogView.findViewById(R.id.rb_q_lang_bn);
        RadioButton rbLangEn = dialogView.findViewById(R.id.rb_q_lang_en);

        Project project = viewModel.getProject().getValue();
        if (project != null && "bn".equals(project.getLanguage())) {
            rbLangBn.setChecked(true);
        } else {
            rbLangEn.setChecked(true);
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setPositiveButton("Generate Questions", (dialog, which) -> {
                    int selectedCount = 10;
                    int countId = rgCount.getCheckedRadioButtonId();
                    if (countId == R.id.rb_count_5) selectedCount = 5;
                    else if (countId == R.id.rb_count_15) selectedCount = 15;
                    else if (countId == R.id.rb_count_20) selectedCount = 20;

                    String selectedType = "mixed";
                    int typeId = rgType.getCheckedRadioButtonId();
                    if (typeId == R.id.rb_type_mcq) selectedType = "mcq";
                    else if (typeId == R.id.rb_type_short) selectedType = "short";
                    else if (typeId == R.id.rb_type_essay) selectedType = "essay";

                    String selectedDiff = "medium";
                    int diffId = rgDiff.getCheckedRadioButtonId();
                    if (diffId == R.id.rb_diff_easy) selectedDiff = "easy";
                    else if (diffId == R.id.rb_diff_hard) selectedDiff = "hard";
                    else if (diffId == R.id.rb_diff_mixed) selectedDiff = "mixed";

                    String selectedLang = rbLangBn.isChecked() ? "bn" : "en";

                    startExamQuestionsGeneration(selectedCount, selectedDiff, selectedType, selectedLang);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startExamQuestionsGeneration(int count, String difficulty, String type, String language) {
        View progressView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_generation_progress, null);
        TextView title = progressView.findViewById(R.id.dialog_gen_title);
        ProgressBar progressBar = progressView.findViewById(R.id.dialog_gen_progress);
        TextView status = progressView.findViewById(R.id.dialog_gen_status);
        LinearLayout resultsLayout = progressView.findViewById(R.id.layout_gen_results);
        TextView costBadge = progressView.findViewById(R.id.dialog_gen_cost_badge);
        TextView tokensText = progressView.findViewById(R.id.dialog_gen_tokens);
        TextView jsonView = progressView.findViewById(R.id.dialog_gen_json);

        title.setText("Exam Practice Generation");
        status.setText("Reading source materials & generating exam questions...");

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(progressView)
                .setPositiveButton("Close", null)
                .setCancelable(false)
                .show();
        generationDialogs.add(dialog);
        dialog.setOnDismissListener(ignored -> generationDialogs.remove(dialog));

        generationManager.generateExamQuestions(projectId, count, difficulty, type, language, new GenerationManager.ExamGenerationCallback() {
            @Override
            public void onProgress(String message) {
                if (!isAdded() || getView() == null || !dialog.isShowing()) return;
                status.setText(message);
            }

            @Override
            public void onSuccess(ExamQuiz quiz, File pdfFile, TokenUsage usage, boolean fromCache) {
                if (!isAdded() || getView() == null || !dialog.isShowing()) return;
                progressBar.setVisibility(View.GONE);
                status.setText("✓ " + quiz.getQuestions().size() + " Questions & Solutions Ready!");
                resultsLayout.setVisibility(View.VISIBLE);

                if (fromCache) {
                    costBadge.setText("Local Cache: $0.00000 (Free)");
                    tokensText.setText("0 network tokens used • Loaded from Room database cache");
                } else {
                    costBadge.setText(String.format("Cost: $%.5f", usage.getCostUsd()));
                    tokensText.setText(String.format("Tokens: %d in / %d out (Cache: %d read, %d write)",
                            usage.getInputTokens(), usage.getOutputTokens(),
                            usage.getCacheReadInputTokens(), usage.getCacheCreationInputTokens()));
                }

                jsonView.setText("Questions Created: " + quiz.getQuestions().size()
                        + "\nPrintable PDF: " + pdfFile.getName()
                        + " (" + (pdfFile.length() / 1024) + " KB)");

                refreshLatestExamCard();

                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("Start Practice");
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    dialog.dismiss();
                    Intent intent = new Intent(requireContext(), ExamPracticeActivity.class);
                    intent.putExtra(ExamPracticeActivity.EXTRA_QUIZ, quiz);
                    startActivity(intent);
                });
            }

            @Override
            public void onError(String errorMessage) {
                if (!isAdded() || getView() == null || !dialog.isShowing()) return;
                progressBar.setVisibility(View.GONE);
                status.setText("Error: " + errorMessage);
                status.setTextColor(requireContext().getColor(R.color.error));
            }
        });
    }

    private void openPdf(File pdfFile) {
        if (pdfFile == null || !pdfFile.exists()) {
            Toast.makeText(requireContext(), "PDF file not found", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Uri uri = FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".fileprovider",
                    pdfFile
            );

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/pdf");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "No PDF viewer app found on device: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void sharePdf(File pdfFile) {
        if (pdfFile == null || !pdfFile.exists()) {
            Toast.makeText(requireContext(), "PDF file not found", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Uri uri = FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".fileprovider",
                    pdfFile
            );

            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("application/pdf");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.putExtra(Intent.EXTRA_SUBJECT, pdfFile.getName());
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "Share Study Guide"));
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Share failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void openPptx(File pptxFile) {
        if (pptxFile == null || !pptxFile.exists()) {
            Toast.makeText(requireContext(), "Presentation file not found", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Uri uri = FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".fileprovider",
                    pptxFile
            );

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.openxmlformats-officedocument.presentationml.presentation");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "No PPTX viewer app found (install PowerPoint or Google Slides): " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void sharePptx(File pptxFile) {
        if (pptxFile == null || !pptxFile.exists()) {
            Toast.makeText(requireContext(), "Presentation file not found", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Uri uri = FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".fileprovider",
                    pptxFile
            );

            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("application/vnd.openxmlformats-officedocument.presentationml.presentation");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.putExtra(Intent.EXTRA_SUBJECT, pptxFile.getName());
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "Share Presentation"));
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Share failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void playVideo(File videoFile) {
        if (videoFile == null || !videoFile.exists()) {
            Toast.makeText(requireContext(), "Video file not found", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Uri uri = FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".fileprovider",
                    videoFile
            );

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "video/mp4");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "No video player found: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void shareVideo(File videoFile) {
        if (videoFile == null || !videoFile.exists()) {
            Toast.makeText(requireContext(), "Video file not found", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Uri uri = FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".fileprovider",
                    videoFile
            );

            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("video/mp4");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.putExtra(Intent.EXTRA_SUBJECT, videoFile.getName());
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "Share Narrated Video"));
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Share failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showGenerateGuideOptionsDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_generate_guide_options, null);

        RadioButton rbEn = dialogView.findViewById(R.id.rb_lang_en);
        RadioButton rbBn = dialogView.findViewById(R.id.rb_lang_bn);
        RadioButton rbSummary = dialogView.findViewById(R.id.rb_detail_summary);
        RadioButton rbStandard = dialogView.findViewById(R.id.rb_detail_standard);
        RadioButton rbExhaustive = dialogView.findViewById(R.id.rb_detail_exhaustive);
        CheckBox cbVerify = dialogView.findViewById(R.id.cb_verify_completeness);

        // Pre-fill from project settings
        Project project = viewModel.getProject().getValue();
        if (project != null) {
            if ("bn".equals(project.getLanguage())) {
                rbBn.setChecked(true);
            } else {
                rbEn.setChecked(true);
            }

            if ("summary".equals(project.getDetailLevel())) {
                rbSummary.setChecked(true);
            } else if ("exhaustive".equals(project.getDetailLevel())) {
                rbExhaustive.setChecked(true);
            } else {
                rbStandard.setChecked(true);
            }
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setPositiveButton("Generate PDF", (dialog, which) -> {
                    String chosenLang = rbBn.isChecked() ? "bn" : "en";
                    String chosenDetail = rbSummary.isChecked() ? "summary" : (rbExhaustive.isChecked() ? "exhaustive" : "standard");
                    boolean verify = cbVerify.isChecked();

                    startStudyGuideGeneration(chosenLang, chosenDetail, verify);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startStudyGuideGeneration(String language, String detailLevel, boolean verifyCompleteness) {
        View progressView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_generation_progress, null);
        TextView title = progressView.findViewById(R.id.dialog_gen_title);
        ProgressBar progressBar = progressView.findViewById(R.id.dialog_gen_progress);
        TextView status = progressView.findViewById(R.id.dialog_gen_status);
        LinearLayout resultsLayout = progressView.findViewById(R.id.layout_gen_results);
        TextView costBadge = progressView.findViewById(R.id.dialog_gen_cost_badge);
        TextView tokensText = progressView.findViewById(R.id.dialog_gen_tokens);
        TextView jsonView = progressView.findViewById(R.id.dialog_gen_json);

        title.setText("Study Guide PDF Generation");
        status.setText("Reading source materials...");

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(progressView)
                .setPositiveButton("Close", null)
                .setCancelable(false)
                .show();
        generationDialogs.add(dialog);
        dialog.setOnDismissListener(ignored -> generationDialogs.remove(dialog));

        generationManager.generateStudyGuidePdf(projectId, language, detailLevel, verifyCompleteness, new GenerationManager.PdfGenerationCallback() {
            @Override
            public void onProgress(String message) {
                if (!isAdded() || getView() == null || !dialog.isShowing()) return;
                status.setText(message);
            }

            @Override
            public void onSuccess(File pdfFile, TokenUsage usage, boolean fromCache) {
                if (!isAdded() || getView() == null || !dialog.isShowing()) return;
                progressBar.setVisibility(View.GONE);
                status.setText("✓ Study Guide PDF Generated Successfully!");
                resultsLayout.setVisibility(View.VISIBLE);

                if (fromCache) {
                    costBadge.setText("Local Cache: $0.00000 (Free)");
                    tokensText.setText("0 network tokens used • Loaded from Room database cache");
                } else {
                    costBadge.setText(String.format("Cost: $%.5f", usage.getCostUsd()));
                    tokensText.setText(String.format("Tokens: %d in / %d out (Cache: %d read, %d write)",
                            usage.getInputTokens(), usage.getOutputTokens(),
                            usage.getCacheReadInputTokens(), usage.getCacheCreationInputTokens()));
                }

                jsonView.setText("Saved PDF:\n" + pdfFile.getAbsolutePath() + "\nSize: " + (pdfFile.length() / 1024) + " KB");

                refreshLatestPdfCard();

                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("Open PDF");
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    dialog.dismiss();
                    openPdf(pdfFile);
                });
            }

            @Override
            public void onError(String errorMessage) {
                if (!isAdded() || getView() == null || !dialog.isShowing()) return;
                progressBar.setVisibility(View.GONE);
                status.setText("Error: " + errorMessage);
                status.setTextColor(requireContext().getColor(R.color.error));
            }
        });
    }

    private void showGenerateSlidesOptionsDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_generate_slides_options, null);

        RadioButton rbSummary = dialogView.findViewById(R.id.rb_slides_summary);
        RadioButton rbStandard = dialogView.findViewById(R.id.rb_slides_standard);
        RadioButton rbExhaustive = dialogView.findViewById(R.id.rb_slides_exhaustive);
        RadioButton rbLangEn = dialogView.findViewById(R.id.rb_slides_lang_en);
        RadioButton rbLangBn = dialogView.findViewById(R.id.rb_slides_lang_bn);

        Project project = viewModel.getProject().getValue();
        if (project != null) {
            if ("bn".equals(project.getLanguage())) {
                rbLangBn.setChecked(true);
            } else {
                rbLangEn.setChecked(true);
            }

            if ("summary".equals(project.getDetailLevel())) {
                rbSummary.setChecked(true);
            } else if ("exhaustive".equals(project.getDetailLevel())) {
                rbExhaustive.setChecked(true);
            } else {
                rbStandard.setChecked(true);
            }
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setPositiveButton("Generate PPTX", (dialog, which) -> {
                    String chosenLang = rbLangBn.isChecked() ? "bn" : "en";
                    String chosenDetail = rbSummary.isChecked() ? "summary" : (rbExhaustive.isChecked() ? "exhaustive" : "standard");

                    startPresentationGeneration(chosenLang, chosenDetail);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startPresentationGeneration(String language, String detailLevel) {
        View progressView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_generation_progress, null);
        TextView title = progressView.findViewById(R.id.dialog_gen_title);
        ProgressBar progressBar = progressView.findViewById(R.id.dialog_gen_progress);
        TextView status = progressView.findViewById(R.id.dialog_gen_status);
        LinearLayout resultsLayout = progressView.findViewById(R.id.layout_gen_results);
        TextView costBadge = progressView.findViewById(R.id.dialog_gen_cost_badge);
        TextView tokensText = progressView.findViewById(R.id.dialog_gen_tokens);
        TextView jsonView = progressView.findViewById(R.id.dialog_gen_json);

        title.setText("Presentation Generation");
        status.setText("Reading source materials & generating slide outline...");

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(progressView)
                .setPositiveButton("Close", null)
                .setCancelable(false)
                .show();
        generationDialogs.add(dialog);
        dialog.setOnDismissListener(ignored -> generationDialogs.remove(dialog));

        generationManager.generatePresentationDeck(projectId, language, detailLevel, new GenerationManager.PresentationGenerationCallback() {
            @Override
            public void onProgress(String message) {
                if (!isAdded() || getView() == null || !dialog.isShowing()) return;
                status.setText(message);
            }

            @Override
            public void onSuccess(PresentationDeck deck, File pptxFile, TokenUsage usage, boolean fromCache) {
                if (!isAdded() || getView() == null || !dialog.isShowing()) return;
                progressBar.setVisibility(View.GONE);
                status.setText("✓ Presentation (.pptx) Created Successfully!");
                resultsLayout.setVisibility(View.VISIBLE);

                if (fromCache) {
                    costBadge.setText("Local Cache: $0.00000 (Free)");
                    tokensText.setText("0 network tokens used • Loaded from Room database cache");
                } else {
                    costBadge.setText(String.format("Cost: $%.5f", usage.getCostUsd()));
                    tokensText.setText(String.format("Tokens: %d in / %d out (Cache: %d read, %d write)",
                            usage.getInputTokens(), usage.getOutputTokens(),
                            usage.getCacheReadInputTokens(), usage.getCacheCreationInputTokens()));
                }

                jsonView.setText("Slides: " + deck.getSlides().size()
                        + "\nFile: " + pptxFile.getName()
                        + " (" + (pptxFile.length() / 1024) + " KB)"
                        + "\nSpeaker Notes / Narration Script included");

                refreshLatestSlidesCard();

                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("Open PPTX");
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    dialog.dismiss();
                    openPptx(pptxFile);
                });
            }

            @Override
            public void onError(String errorMessage) {
                if (!isAdded() || getView() == null || !dialog.isShowing()) return;
                progressBar.setVisibility(View.GONE);
                status.setText("Error: " + errorMessage);
                status.setTextColor(requireContext().getColor(R.color.error));
            }
        });
    }

    private void showGenerateVideoOptionsDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_generate_video_options, null);

        RadioButton rbSummary = dialogView.findViewById(R.id.rb_video_summary);
        RadioButton rbStandard = dialogView.findViewById(R.id.rb_video_standard);
        RadioButton rbExhaustive = dialogView.findViewById(R.id.rb_video_exhaustive);
        RadioButton rbLangEn = dialogView.findViewById(R.id.rb_video_lang_en);
        RadioButton rbLangBn = dialogView.findViewById(R.id.rb_video_lang_bn);
        RadioGroup rgLang = dialogView.findViewById(R.id.rg_video_lang);

        TextView textTtsTitle = dialogView.findViewById(R.id.text_tts_status_title);
        TextView textTtsDesc = dialogView.findViewById(R.id.text_tts_status_desc);
        MaterialButton btnInstallTts = dialogView.findViewById(R.id.btn_install_tts);

        btnInstallTts.setOnClickListener(v -> {
            try {
                Intent installIntent = new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                startActivity(installIntent);
            } catch (Exception e) {
                Toast.makeText(requireContext(), "Cannot open TTS installer: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        Runnable checkTts = () -> {
            String targetLang = rbLangBn.isChecked() ? "bn" : "en";
            textTtsTitle.setText("🔊 Checking TTS Voice Engine...");
            textTtsDesc.setText("Testing language pack availability...");
            btnInstallTts.setVisibility(View.GONE);

            TtsNarrationHelper.checkLanguageAvailability(requireContext(), targetLang, (isAvailable, needsDownload, message) -> {
                if (isAvailable) {
                    textTtsTitle.setText("✓ TTS Voice Available");
                    textTtsTitle.setTextColor(requireContext().getColor(R.color.primary));
                    textTtsDesc.setText(targetLang.equals("bn") ? "Bengali speech synthesis engine ready" : "English speech synthesis engine ready");
                    btnInstallTts.setVisibility(View.GONE);
                } else if (needsDownload) {
                    textTtsTitle.setText("⚠️ Voice Data Not Installed");
                    textTtsTitle.setTextColor(requireContext().getColor(R.color.accent_dark));
                    textTtsDesc.setText(message);
                    btnInstallTts.setVisibility(View.VISIBLE);
                } else {
                    textTtsTitle.setText("⚠️ Language Unsupported by Current TTS Engine");
                    textTtsTitle.setTextColor(requireContext().getColor(R.color.error));
                    textTtsDesc.setText(message);
                    btnInstallTts.setVisibility(View.VISIBLE);
                }
            });
        };

        rgLang.setOnCheckedChangeListener((group, checkedId) -> checkTts.run());

        Project project = viewModel.getProject().getValue();
        if (project != null) {
            if ("bn".equals(project.getLanguage())) {
                rbLangBn.setChecked(true);
            } else {
                rbLangEn.setChecked(true);
            }

            if ("summary".equals(project.getDetailLevel())) {
                rbSummary.setChecked(true);
            } else if ("exhaustive".equals(project.getDetailLevel())) {
                rbExhaustive.setChecked(true);
            } else {
                rbStandard.setChecked(true);
            }
        }

        checkTts.run();

        new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setPositiveButton("Start Production", (dialog, which) -> {
                    String chosenLang = rbLangBn.isChecked() ? "bn" : "en";
                    String chosenDetail = rbSummary.isChecked() ? "summary" : (rbExhaustive.isChecked() ? "exhaustive" : "standard");

                    startVideoGeneration(chosenLang, chosenDetail);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startVideoGeneration(String language, String detailLevel) {
        Data inputData = new Data.Builder()
                .putString("owner_uid", com.example.examprep.util.UserPaths.currentUid())
                .putLong(VideoGenerationWorker.KEY_PROJECT_ID, projectId)
                .putString(VideoGenerationWorker.KEY_LANGUAGE, language)
                .putString(VideoGenerationWorker.KEY_DETAIL_LEVEL, detailLevel)
                .build();

        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(VideoGenerationWorker.class)
                .setInputData(inputData)
                .addTag("video_gen_" + projectId)
                .build();

        WorkManager.getInstance(requireContext()).enqueue(workRequest);

        Toast.makeText(requireContext(), "🎬 Video generation started in background! Check notification for progress.", Toast.LENGTH_LONG).show();

        WorkManager.getInstance(requireContext())
                .getWorkInfoByIdLiveData(workRequest.getId())
                .observe(getViewLifecycleOwner(), workInfo -> {
                    if (workInfo != null) {
                        if (workInfo.getState() == WorkInfo.State.SUCCEEDED) {
                            refreshLatestVideoCard();
                            Toast.makeText(requireContext(), "🎬 Narrated Video is ready to watch!", Toast.LENGTH_SHORT).show();
                        } else if (workInfo.getState() == WorkInfo.State.FAILED) {
                            Toast.makeText(requireContext(), "Video generation failed", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void runAiTestPipeline() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_generation_progress, null);
        TextView title = dialogView.findViewById(R.id.dialog_gen_title);
        ProgressBar progressBar = dialogView.findViewById(R.id.dialog_gen_progress);
        TextView status = dialogView.findViewById(R.id.dialog_gen_status);
        LinearLayout resultsLayout = dialogView.findViewById(R.id.layout_gen_results);
        TextView costBadge = dialogView.findViewById(R.id.dialog_gen_cost_badge);
        TextView tokensText = dialogView.findViewById(R.id.dialog_gen_tokens);
        TextView jsonView = dialogView.findViewById(R.id.dialog_gen_json);

        title.setText("AI Pipeline Test");
        status.setText("Initializing request to ChatGPT (GPT-4o mini)...");

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setPositiveButton("Close", null)
                .setCancelable(false)
                .show();
        generationDialogs.add(dialog);
        dialog.setOnDismissListener(ignored -> generationDialogs.remove(dialog));

        List<ProjectFile> files = viewModel.getFiles().getValue();
        boolean hasExtractedText = false;
        if (files != null) {
            for (ProjectFile f : files) {
                if ((f.getExtractedText() != null && !f.getExtractedText().isEmpty())
                        || (f.getOcrText() != null && !f.getOcrText().isEmpty())) {
                    hasExtractedText = true;
                    break;
                }
            }
        }

        if (hasExtractedText) {
            String systemInstruction = "You are ExamForge. Analyze the uploaded study material and return strict JSON summarizing the content. Ground every point strictly in the provided material without hallucinating.";
            String userPrompt = "Return a JSON object with this exact schema: {\"title\": \"Title\", \"total_topics\": 3, \"core_topics\": [\"topic1\", \"topic2\"], \"key_takeaways\": [\"point1\", \"point2\"], \"verification_status\": \"grounded\"}";

            generationManager.generateOrFetch(projectId, "ai_test_summary", systemInstruction, userPrompt, new GenerationManager.GenerationCallback() {
                @Override
                public void onProgress(String message) {
                if (!isAdded() || getView() == null || !dialog.isShowing()) return;
                    status.setText(message);
                }

                @Override
                public void onSuccess(String resultJson, TokenUsage usage, boolean fromCache) {
                if (!isAdded() || getView() == null || !dialog.isShowing()) return;
                    progressBar.setVisibility(View.GONE);
                    status.setText(fromCache ? "✓ Loaded from local Room cache (zero cost)" : "✓ Live response received and validated");
                    resultsLayout.setVisibility(View.VISIBLE);

                    if (fromCache) {
                        costBadge.setText("Local Cache: $0.00000 (Free)");
                        tokensText.setText("0 network tokens used • Result cached in Room DB");
                    } else {
                        costBadge.setText(String.format("Cost: $%.5f", usage.getCostUsd()));
                        tokensText.setText(String.format("Tokens: %d in / %d out (Cache: %d read, %d write)",
                                usage.getInputTokens(), usage.getOutputTokens(),
                                usage.getCacheReadInputTokens(), usage.getCacheCreationInputTokens()));
                    }

                    jsonView.setText(resultJson);
                }

                @Override
                public void onError(String errorMessage) {
                if (!isAdded() || getView() == null || !dialog.isShowing()) return;
                    progressBar.setVisibility(View.GONE);
                    status.setText("Error: " + errorMessage);
                    status.setTextColor(requireContext().getColor(R.color.error));
                }
            });
        } else {
            generationManager.testAiConnection(new GenerationManager.GenerationCallback() {
                @Override
                public void onProgress(String message) {
                if (!isAdded() || getView() == null || !dialog.isShowing()) return;
                    status.setText(message);
                }

                @Override
                public void onSuccess(String resultJson, TokenUsage usage, boolean fromCache) {
                if (!isAdded() || getView() == null || !dialog.isShowing()) return;
                    progressBar.setVisibility(View.GONE);
                    status.setText("✓ Connection to ChatGPT / OpenAI API successful!");
                    resultsLayout.setVisibility(View.VISIBLE);

                    costBadge.setText(String.format("Cost: $%.5f", usage.getCostUsd()));
                    tokensText.setText(String.format("Tokens: %d in / %d out",
                            usage.getInputTokens(), usage.getOutputTokens()));
                    jsonView.setText(resultJson);
                }

                @Override
                public void onError(String errorMessage) {
                if (!isAdded() || getView() == null || !dialog.isShowing()) return;
                    progressBar.setVisibility(View.GONE);
                    status.setText("Error: " + errorMessage);
                    status.setTextColor(requireContext().getColor(R.color.error));
                }
            });
        }
    }

    private void showMissingApiKeyDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("ChatGPT API Key Required")
                .setMessage("Please enter your ChatGPT / OpenAI API key in Settings to use AI features.")
                .setPositiveButton("Open Settings", (d, w) -> {
                    startActivity(new Intent(requireContext(), SettingsActivity.class));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAddMaterialsDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_materials, null);
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .create();

        dialogView.findViewById(R.id.card_choose_files).setOnClickListener(v -> {
            dialog.dismiss();
            launchFilePicker();
        });

        dialogView.findViewById(R.id.card_take_photo).setOnClickListener(v -> {
            dialog.dismiss();
            checkCameraPermissionAndLaunch();
        });

        dialog.show();
        generationDialogs.add(dialog);
        dialog.setOnDismissListener(ignored -> generationDialogs.remove(dialog));
    }

    private void launchFilePicker() {
        String[] mimeTypes = new String[]{
                "application/pdf",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "text/csv",
                "text/comma-separated-values",
                "image/jpeg",
                "image/png",
                "*/*"
        };
        filePickerLauncher.launch(mimeTypes);
    }

    private void checkCameraPermissionAndLaunch() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            launchCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void launchCamera() {
        try {
            File cacheDir = new File(requireContext().getCacheDir(), "camera");
            if (!cacheDir.exists()) cacheDir.mkdirs();

            pendingCameraFile = new File(cacheDir, "scan_" + System.currentTimeMillis() + ".jpg");
            pendingCameraUri = FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".fileprovider",
                    pendingCameraFile
            );

            cameraLauncher.launch(pendingCameraUri);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Failed to open camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showFilePreviewDialog(@NonNull ProjectFile file) {
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_file_preview, null);

        TextView title = view.findViewById(R.id.dialog_preview_title);
        TextView meta = view.findViewById(R.id.dialog_preview_meta);
        TextView stats = view.findViewById(R.id.dialog_preview_stats);
        TextView content = view.findViewById(R.id.dialog_preview_content);

        title.setText(file.getFileName());

        long size = file.getFileSize();
        String sizeStr = size < 1024 ? size + " B"
                : size < 1024 * 1024 ? String.format("%.1f KB", size / 1024.0)
                : String.format("%.1f MB", size / (1024.0 * 1024.0));
        meta.setText(String.format("Format: %s • Size: %s", file.getMimeType(), sizeStr));

        String effectiveText = file.getExtractedText() != null ? file.getExtractedText() : file.getOcrText();
        if (effectiveText != null && !effectiveText.isEmpty()) {
            int chars = effectiveText.length();
            int words = effectiveText.split("\\s+").length;
            String type = file.getOcrText() != null ? "On-Device OCR" : "Text Stripper";
            stats.setText(String.format("✓ %s: %d words • %,d characters", type, words, chars));
            content.setText(effectiveText);
        } else {
            stats.setText("Status: " + file.getTextExtractionStatus());
            content.setText("[No text extracted yet or extraction failed]");
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setView(view)
                .setPositiveButton("Close", null)
                .setNeutralButton("Re-extract / OCR", (d, w) -> {
                    viewModel.reExtractFile(file);
                    Toast.makeText(requireContext(), "Re-extracting...", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Delete", (d, w) -> showDeleteFileDialog(file))
                .show();
    }

    private void showDeleteFileDialog(@NonNull ProjectFile file) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete Material")
                .setMessage("Remove \"" + file.getFileName() + "\" from this project?")
                .setPositiveButton("Delete", (d, w) -> viewModel.deleteFile(file))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return "";
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
