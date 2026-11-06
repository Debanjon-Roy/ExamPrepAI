package com.example.examprep.ui.project;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

public class ProjectViewModelFactory implements ViewModelProvider.Factory {
    private final Application application;
    private final long projectId;

    public ProjectViewModelFactory(Application application, long projectId) {
        this.application = application;
        this.projectId = projectId;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(ProjectViewModel.class)) {
            return (T) new ProjectViewModel(application, projectId);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
