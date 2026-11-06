package com.example.examprep.ui.project;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.example.examprep.R;
import com.example.examprep.data.model.ProjectFile;

public class FileAdapter extends ListAdapter<ProjectFile, FileAdapter.FileViewHolder> {

    public interface OnFileClickListener {
        void onClick(ProjectFile file);
    }

    public interface OnFileLongClickListener {
        void onLongClick(ProjectFile file);
    }

    private final OnFileClickListener clickListener;
    private final OnFileLongClickListener longClickListener;

    public FileAdapter(OnFileClickListener clickListener, OnFileLongClickListener longClickListener) {
        super(DIFF_CALLBACK);
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
    }

    private static final DiffUtil.ItemCallback<ProjectFile> DIFF_CALLBACK = new DiffUtil.ItemCallback<ProjectFile>() {
        @Override
        public boolean areItemsTheSame(@NonNull ProjectFile oldItem, @NonNull ProjectFile newItem) {
            return oldItem.getId() == newItem.getId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull ProjectFile oldItem, @NonNull ProjectFile newItem) {
            boolean textSame = (oldItem.getExtractedText() == null && newItem.getExtractedText() == null)
                    || (oldItem.getExtractedText() != null && oldItem.getExtractedText().equals(newItem.getExtractedText()));
            return oldItem.getFileName().equals(newItem.getFileName())
                    && oldItem.getTextExtractionStatus().equals(newItem.getTextExtractionStatus())
                    && textSame;
        }
    };

    @NonNull
    @Override
    public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_file, parent, false);
        return new FileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    class FileViewHolder extends RecyclerView.ViewHolder {
        private final ImageView iconFile;
        private final TextView textFileName;
        private final TextView textFileInfo;
        private final TextView textStatus;

        FileViewHolder(@NonNull View itemView) {
            super(itemView);
            iconFile = itemView.findViewById(R.id.icon_file);
            textFileName = itemView.findViewById(R.id.text_file_name);
            textFileInfo = itemView.findViewById(R.id.text_file_info);
            textStatus = itemView.findViewById(R.id.text_file_status);

            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && clickListener != null) {
                    clickListener.onClick(getItem(pos));
                }
            });

            itemView.setOnLongClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && longClickListener != null) {
                    longClickListener.onLongClick(getItem(pos));
                    return true;
                }
                return false;
            });
        }

        void bind(ProjectFile file) {
            textFileName.setText(file.getFileName());

            // Format file size
            long size = file.getFileSize();
            String sizeStr;
            if (size < 1024) sizeStr = size + " B";
            else if (size < 1024 * 1024) sizeStr = String.format("%.1f KB", size / 1024.0);
            else sizeStr = String.format("%.1f MB", size / (1024.0 * 1024.0));
            textFileInfo.setText(file.getMimeType() + " • " + sizeStr);

            // Status indicator with word count if available
            String effectiveText = file.getExtractedText() != null ? file.getExtractedText() : file.getOcrText();
            int wordCount = 0;
            if (effectiveText != null && !effectiveText.isEmpty()) {
                wordCount = effectiveText.split("\\s+").length;
            }

            switch (file.getTextExtractionStatus()) {
                case "done":
                    if (file.getOcrText() != null) {
                        textStatus.setText(String.format("✓ OCR (%d w)", wordCount));
                    } else {
                        textStatus.setText(String.format("✓ Text (%d w)", wordCount));
                    }
                    textStatus.setTextColor(itemView.getContext().getColor(R.color.success));
                    break;
                case "ocr_needed":
                    textStatus.setText("⏳ Needs OCR");
                    textStatus.setTextColor(itemView.getContext().getColor(R.color.warning));
                    break;
                case "extracting":
                    textStatus.setText("⏳ Extracting...");
                    textStatus.setTextColor(itemView.getContext().getColor(R.color.warning));
                    break;
                case "failed":
                    textStatus.setText("✗ Failed");
                    textStatus.setTextColor(itemView.getContext().getColor(R.color.error));
                    break;
                default:
                    textStatus.setText("⏳ Pending");
                    textStatus.setTextColor(itemView.getContext().getColor(R.color.on_surface_medium));
                    break;
            }

            // File icon based on type
            String mime = file.getMimeType().toLowerCase();
            String name = file.getFileName().toLowerCase();
            if (name.endsWith(".pdf") || mime.contains("pdf")) {
                iconFile.setImageResource(android.R.drawable.ic_menu_agenda);
            } else if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || mime.contains("image")) {
                iconFile.setImageResource(android.R.drawable.ic_menu_gallery);
            } else if (name.endsWith(".pptx") || mime.contains("presentation") || mime.contains("powerpoint")) {
                iconFile.setImageResource(android.R.drawable.ic_menu_slideshow);
            } else if (name.endsWith(".xlsx") || name.endsWith(".csv") || mime.contains("spreadsheet") || mime.contains("csv")) {
                iconFile.setImageResource(android.R.drawable.ic_menu_sort_by_size);
            } else {
                iconFile.setImageResource(android.R.drawable.ic_menu_save);
            }
        }
    }
}
