package com.example.examprep.ui;

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
import com.example.examprep.data.model.Project;

public class SidebarProjectAdapter extends ListAdapter<Project, SidebarProjectAdapter.SidebarViewHolder> {

    public interface OnProjectSelectedListener {
        void onProjectSelected(Project project);
    }

    private final OnProjectSelectedListener listener;
    private long activeProjectId = -1;

    public SidebarProjectAdapter(OnProjectSelectedListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    public void setActiveProjectId(long activeProjectId) {
        this.activeProjectId = activeProjectId;
        notifyDataSetChanged();
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
    public SidebarViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_sidebar_project, parent, false);
        return new SidebarViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SidebarViewHolder holder, int position) {
        Project project = getItem(position);
        holder.bind(project, project.getId() == activeProjectId);
    }

    class SidebarViewHolder extends RecyclerView.ViewHolder {
        private final TextView textName;
        private final TextView textBadge;
        private final View activeIndicator;
        private final ImageView icon;

        SidebarViewHolder(@NonNull View itemView) {
            super(itemView);
            textName = itemView.findViewById(R.id.text_project_name);
            textBadge = itemView.findViewById(R.id.text_project_badge);
            activeIndicator = itemView.findViewById(R.id.view_active_indicator);
            icon = itemView.findViewById(R.id.img_project_icon);

            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    listener.onProjectSelected(getItem(pos));
                }
            });
        }

        void bind(Project project, boolean isActive) {
            textName.setText(project.getName());
            textBadge.setText("bn".equals(project.getLanguage()) ? "বাংলা" : "EN");
            activeIndicator.setVisibility(isActive ? View.VISIBLE : View.GONE);
            if (isActive) {
                textName.setTextColor(itemView.getContext().getColor(R.color.primary));
            } else {
                textName.setTextColor(itemView.getContext().getColor(R.color.on_surface));
            }
        }
    }
}
