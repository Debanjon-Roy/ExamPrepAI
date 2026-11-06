package com.example.examprep.ui.home;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import com.example.examprep.ExamPrepApp;
import com.example.examprep.data.cloud.CloudSync;
import com.example.examprep.data.db.ProjectDao;
import com.example.examprep.data.model.Project;
import java.util.List;
import java.util.concurrent.Executors;

public class HomeViewModel extends AndroidViewModel {

    private final ProjectDao projectDao;
    private final LiveData<List<Project>> allProjects;

    public HomeViewModel(@NonNull Application application) {
        super(application);
        projectDao = ((ExamPrepApp) application).getDatabase().projectDao();
        allProjects = projectDao.getAllProjects();
    }

    public LiveData<List<Project>> getAllProjects() {
        return allProjects;
    }

    public void createProject(String name, String description) {
        createProject(name, description, "en");
    }

    public void createProject(String name, String description, String language) {
        Executors.newSingleThreadExecutor().execute(() -> {
            Project project = new Project(name);
            if (description != null) {
                project.setDescription(description);
            }
            if (language != null) {
                project.setLanguage(language);
            }
            long id = projectDao.insert(project);
            project.setId(id);
            CloudSync.pushProject(project);
        });
    }

    public void deleteProject(Project project) {
        Executors.newSingleThreadExecutor().execute(() -> {
            projectDao.delete(project);
            CloudSync.deleteProject(project.getId());
        });
    }
}
