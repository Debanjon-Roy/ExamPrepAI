package com.example.examprep.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.examprep.R;
import com.example.examprep.data.model.Project;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class HomeFragment extends Fragment {

    private HomeViewModel viewModel;
    private ProjectAdapter adapter;
    private RecyclerView recyclerView;
    private View emptyView;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.recycler_projects);
        emptyView = view.findViewById(R.id.text_empty);
        View fab = view.findViewById(R.id.fab_new_project);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        
        adapter = new ProjectAdapter(project -> {
            // Guard against double-tap or navigating when already on ProjectFragment
            androidx.navigation.NavController navCtrl =
                    androidx.navigation.fragment.NavHostFragment.findNavController(HomeFragment.this);
            if (navCtrl.getCurrentDestination() != null
                    && navCtrl.getCurrentDestination().getId() == R.id.homeFragment) {
                Bundle args = new Bundle();
                args.putLong("projectId", project.getId());
                navCtrl.navigate(R.id.action_home_to_project, args);
            }
        }, project -> {
            // Long click - delete
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete Project")
                    .setMessage("Delete \"" + project.getName() + "\" and all its files?")
                    .setPositiveButton("Delete", (d, w) -> viewModel.deleteProject(project))
                    .setNegativeButton("Cancel", null)
                    .show();
        });
        recyclerView.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        viewModel.getAllProjects().observe(getViewLifecycleOwner(), projects -> {
            adapter.submitList(projects);
            emptyView.setVisibility(projects == null || projects.isEmpty() ? View.VISIBLE : View.GONE);
            recyclerView.setVisibility(projects == null || projects.isEmpty() ? View.GONE : View.VISIBLE);
        });

        fab.setOnClickListener(v -> showNewProjectDialog());
    }

    private void showNewProjectDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_new_project, null);
        EditText nameInput = dialogView.findViewById(R.id.edit_project_name);
        EditText descInput = dialogView.findViewById(R.id.edit_project_description);
        android.widget.RadioButton rbBn = dialogView.findViewById(R.id.rb_new_bn);

        new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setPositiveButton(R.string.dialog_create, (dialog, which) -> {
                    String name = nameInput.getText().toString().trim();
                    if (!name.isEmpty()) {
                        String desc = descInput.getText().toString().trim();
                        String lang = (rbBn != null && rbBn.isChecked()) ? "bn" : "en";
                        viewModel.createProject(name, desc.isEmpty() ? null : desc, lang);
                    }
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }
}
