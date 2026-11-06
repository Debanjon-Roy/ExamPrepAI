package com.example.examprep.ui.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.example.examprep.R;
import com.example.examprep.data.model.Project;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class ProjectAdapter extends ListAdapter<Project, ProjectAdapter.ProjectViewHolder> {

    public interface OnProjectClickListener {
        void onClick(Project project);
    }

    public interface OnProjectLongClickListener {
        void onLongClick(Project project);
    }

    private final OnProjectClickListener clickListener;
    private final OnProjectLongClickListener longClickListener;

    public ProjectAdapter(OnProjectClickListener clickListener, OnProjectLongClickListener longClickListener) {
        super(DIFF_CALLBACK);
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
    }

    private static final DiffUtil.ItemCallback<Project> DIFF_CALLBACK = new DiffUtil.ItemCallback<Project>() {
        @Override
        public boolean areItemsTheSame(@NonNull Project oldItem, @NonNull Project newItem) {
            return oldItem.getId() == newItem.getId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull Project oldItem, @NonNull Project newItem) {
            return oldItem.getName().equals(newItem.getName())
                    && oldItem.getUpdatedAt().equals(newItem.getUpdatedAt());
        }
    };

    @NonNull
    @Override
    public ProjectViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_project, parent, false);
        return new ProjectViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProjectViewHolder holder, int position) {
        Project project = getItem(position);
        holder.bind(project);
    }

    class ProjectViewHolder extends RecyclerView.ViewHolder {
        private final TextView textName;
        private final TextView textDescription;
        private final TextView textDate;
        private final TextView textLanguage;
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault());

        ProjectViewHolder(@NonNull View itemView) {
            super(itemView);
            textName = itemView.findViewById(R.id.text_project_name);
            textDescription = itemView.findViewById(R.id.text_project_description);
            textDate = itemView.findViewById(R.id.text_project_date);
            textLanguage = itemView.findViewById(R.id.text_project_language);

            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    clickListener.onClick(getItem(pos));
                }
            });
            itemView.setOnLongClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    longClickListener.onLongClick(getItem(pos));
                }
                return true;
            });
        }

        void bind(Project project) {
            textName.setText(project.getName());
            
            if (project.getDescription() != null && !project.getDescription().isEmpty()) {
                textDescription.setText(project.getDescription());
                textDescription.setVisibility(View.VISIBLE);
            } else {
                textDescription.setVisibility(View.GONE);
            }
            
            textDate.setText(dateFormat.format(project.getUpdatedAt()));
            textLanguage.setText(project.getLanguage().equals("bn") ? "বাংলা" : "English");
        }
    }
}
