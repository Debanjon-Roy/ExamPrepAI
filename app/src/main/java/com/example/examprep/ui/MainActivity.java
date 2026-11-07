package com.example.examprep.ui;

import com.example.examprep.util.UserPaths;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.examprep.ExamPrepApp;
import com.example.examprep.R;
import com.example.examprep.auth.AuthManager;
import com.example.examprep.auth.WelcomeActivity;
import com.example.examprep.data.cloud.CloudSync;
import com.example.examprep.data.model.Project;
import com.example.examprep.ui.settings.SettingsActivity;
import com.example.examprep.util.SecurePrefs;
import com.example.examprep.util.UserStatsManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private NavController navController;
    private AppBarConfiguration appBarConfiguration;
    private SidebarProjectAdapter sidebarProjectAdapter;

    private TextView textStatTimeDevoted;
    private TextView textStatWorksCount;
    private TextView textSidebarProjectCount;
    private TextView textSidebarLangLabel;
    private SwitchMaterial switchThemeMode;

    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final String PREF_DARK_THEME = "pref_dark_theme";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Guard: this screen requires a signed-in user.
        if (!AuthManager.isLoggedIn()) {
            goToWelcome();
            return;
        }

        // Create/refresh this user's Firestore profile and restore their projects if needed.
        CloudSync.onSignedIn(getApplicationContext());

        setContentView(R.layout.activity_main);
        com.example.examprep.util.SystemBarInsets.apply(this);

        drawerLayout = findViewById(R.id.drawer_layout);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Setup sidebar UI and adapter first before listeners fire
        setupSidebar();

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();

            // Set up homeFragment with DrawerLayout so the hamburger icon opens the drawer
            appBarConfiguration = new AppBarConfiguration.Builder(R.id.homeFragment)
                    .setOpenableLayout(drawerLayout)
                    .build();
            NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

            // Track active project in sidebar
            navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
                if (sidebarProjectAdapter != null) {
                    if (destination.getId() == R.id.projectFragment && arguments != null) {
                        long pid = arguments.getLong("projectId", -1);
                        sidebarProjectAdapter.setActiveProjectId(pid);
                    } else {
                        sidebarProjectAdapter.setActiveProjectId(-1);
                    }
                }
            });
        }

        // Handle back button when drawer is open
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });

        requestPermissions();
    }

    private void setupSidebar() {
        updateAccountIdentity();
        textSidebarProjectCount = findViewById(R.id.text_sidebar_project_count);
        textStatTimeDevoted = findViewById(R.id.text_stat_time_devoted);
        textStatWorksCount = findViewById(R.id.text_stat_works_count);
        textSidebarLangLabel = findViewById(R.id.text_sidebar_lang_label);
        switchThemeMode = findViewById(R.id.switch_theme_mode);

        RecyclerView recyclerSidebarProjects = findViewById(R.id.recycler_sidebar_projects);
        recyclerSidebarProjects.setLayoutManager(new LinearLayoutManager(this));

        // 1. Project Switcher Adapter
        sidebarProjectAdapter = new SidebarProjectAdapter(project -> {
            drawerLayout.closeDrawer(GravityCompat.START);
            Bundle args = new Bundle();
            args.putLong("projectId", project.getId());
            navController.navigate(R.id.action_global_project, args);
        });
        recyclerSidebarProjects.setAdapter(sidebarProjectAdapter);

        // Observe all projects for sidebar
        ExamPrepApp.getInstance().getDatabase().projectDao().getAllProjects().observe(this, projects -> {
            sidebarProjectAdapter.submitList(projects);
            int count = (projects != null) ? projects.size() : 0;
            if (textSidebarProjectCount != null) {
                textSidebarProjectCount.setText(String.valueOf(count));
            }
        });

        // 2. Home Dashboard Button in Sidebar
        View btnSidebarHome = findViewById(R.id.btn_sidebar_home);
        if (btnSidebarHome != null) {
            btnSidebarHome.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                navController.popBackStack(R.id.homeFragment, false);
            });
        }

        // 3. New Project in Sidebar
        View btnSidebarNewProject = findViewById(R.id.btn_sidebar_new_project);
        if (btnSidebarNewProject != null) {
            btnSidebarNewProject.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                showNewProjectDialog();
            });
        }

        // 4. Dark Theme / Brightness Toggle
        SharedPreferences themePrefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean isDark = themePrefs.getBoolean(PREF_DARK_THEME, false);
        if (switchThemeMode != null) {
            switchThemeMode.setChecked(isDark);
            switchThemeMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked != themePrefs.getBoolean(PREF_DARK_THEME, false)) {
                    themePrefs.edit().putBoolean(PREF_DARK_THEME, isChecked).apply();
                    AppCompatDelegate.setDefaultNightMode(
                            isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
                    );
                }
            });
        }

        View btnSidebarTheme = findViewById(R.id.btn_sidebar_theme);
        if (btnSidebarTheme != null) {
            btnSidebarTheme.setOnClickListener(v -> {
                if (switchThemeMode != null) {
                    switchThemeMode.toggle();
                }
            });
        }

        // 5. Language Switcher
        updateLanguageLabel();
        View btnSidebarLang = findViewById(R.id.btn_sidebar_lang);
        if (btnSidebarLang != null) {
            btnSidebarLang.setOnClickListener(v -> showLanguageSwitchDialog());
        }

        // 6. Settings Shortcut in Sidebar
        View btnSidebarSettings = findViewById(R.id.btn_sidebar_settings);
        if (btnSidebarSettings != null) {
            btnSidebarSettings.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            });
        }

        // 7. Maker Contact Actions
        View btnMakerEmail = findViewById(R.id.btn_maker_email);
        if (btnMakerEmail != null) {
            btnMakerEmail.setOnClickListener(v -> launchEmailToMaker());
        }

        View btnMakerWhatsApp = findViewById(R.id.btn_maker_whatsapp);
        if (btnMakerWhatsApp != null) {
            btnMakerWhatsApp.setOnClickListener(v -> launchWhatsAppToMaker());
        }

        View btnMakerAutobiography = findViewById(R.id.btn_maker_autobiography);
        if (btnMakerAutobiography != null) {
            btnMakerAutobiography.setOnClickListener(v -> showMakerAutobiographyDialog());
        }

        // Observe works generated live
        ExamPrepApp.getInstance().getDatabase().generationCacheDao().getTotalWorksGeneratedLive().observe(this, count -> {
            if (textStatWorksCount != null) {
                int total = (count != null) ? count : 0;
                int actualCount = Math.max(total, UserStatsManager.getInstance(this).getWorksGeneratedCount(this));
                textStatWorksCount.setText(actualCount + (actualCount == 1 ? " Work" : " Works"));
            }
        });
    }

    private void updateAccountIdentity() {
        com.google.firebase.auth.FirebaseUser user = AuthManager.getCurrentUser();
        TextView name = findViewById(R.id.text_sidebar_account_name);
        TextView email = findViewById(R.id.text_sidebar_account_email);
        if (user == null || name == null || email == null) return;
        String displayName = user.getDisplayName();
        String address = user.getEmail();
        name.setText(displayName == null || displayName.trim().isEmpty() ? "Signed-in account" : displayName);
        email.setText(address == null || address.trim().isEmpty() ? "Account: " + user.getUid() : address);
    }

    private void updateLanguageLabel() {
        if (textSidebarLangLabel != null) {
            SharedPreferences prefs = UserPaths.prefs(this);
            String lang = prefs.getString("language", "en");
            textSidebarLangLabel.setText("bn".equals(lang) ? "বাংলা" : "English");
        }
    }

    private void showLanguageSwitchDialog() {
        SharedPreferences prefs = UserPaths.prefs(this);
        String currentLang = prefs.getString("language", "en");
        int selectedIndex = "bn".equals(currentLang) ? 1 : 0;

        String[] options = new String[]{"English", "বাংলা (Bengali)"};

        new MaterialAlertDialogBuilder(this)
                .setTitle("Select Study Language")
                .setSingleChoiceItems(options, selectedIndex, (dialog, which) -> {
                    String chosen = (which == 1) ? "bn" : "en";
                    prefs.edit().putString("language", chosen).apply();
                    updateLanguageLabel();
                    Toast.makeText(this, "Default language updated to " + options[which], Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showNewProjectDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_new_project, null);
        EditText nameInput = dialogView.findViewById(R.id.edit_project_name);
        EditText descInput = dialogView.findViewById(R.id.edit_project_description);
        RadioButton rbBn = dialogView.findViewById(R.id.rb_new_bn);

        new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setPositiveButton(R.string.dialog_create, (dialog, which) -> {
                    String name = nameInput.getText().toString().trim();
                    if (!name.isEmpty()) {
                        String desc = descInput.getText().toString().trim();
                        String lang = (rbBn != null && rbBn.isChecked()) ? "bn" : "en";

                        String ownerUid = UserPaths.currentUid();
                        com.example.examprep.data.db.ProjectDao ownerDao = ExamPrepApp.getInstance().getDatabase().projectDao();
                        Executors.newSingleThreadExecutor().execute(() -> {
                            Project project = new Project(name);
                            if (!desc.isEmpty()) project.setDescription(desc);
                            project.setLanguage(lang);
                            long id = ownerDao.insert(project);
                            project.setId(id);
                            CloudSync.pushProject(ownerUid, project);

                            runOnUiThread(() -> {
                                if (!ownerUid.equals(UserPaths.currentUid()) || isFinishing()) return;
                                Bundle args = new Bundle();
                                args.putLong("projectId", id);
                                navController.navigate(R.id.action_global_project, args);
                            });
                        });
                    }
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private void launchEmailToMaker() {
        try {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:roydebanjon2004@gmail.com"));
            intent.putExtra(Intent.EXTRA_SUBJECT, "Feedback & Greetings: ExamForge (KUET)");
            startActivity(Intent.createChooser(intent, "Send Email to Debanjon Roy"));
        } catch (Exception e) {
            Toast.makeText(this, "Cannot open email client: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void launchWhatsAppToMaker() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://wa.me/8801741816336"));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Cannot open WhatsApp: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showMakerAutobiographyDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_maker_autobiography, null);

        View emailRow = view.findViewById(R.id.dialog_maker_email_row);
        if (emailRow != null) {
            emailRow.setOnClickListener(v -> launchEmailToMaker());
        }

        View whatsappRow = view.findViewById(R.id.dialog_maker_whatsapp_row);
        if (whatsappRow != null) {
            whatsappRow.setOnClickListener(v -> launchWhatsAppToMaker());
        }

        new MaterialAlertDialogBuilder(this)
                .setView(view)
                .setPositiveButton("Close", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!AuthManager.isLoggedIn()) return;
        UserStatsManager.getInstance(this).onSessionStart();

        if (textStatTimeDevoted != null) {
            textStatTimeDevoted.setText(UserStatsManager.getInstance(this).getFormattedTimeDevoted());
        }
        if (textStatWorksCount != null) {
            int count = UserStatsManager.getInstance(this).getWorksGeneratedCount(this);
            textStatWorksCount.setText(count + (count == 1 ? " Work" : " Works"));
        }
        updateLanguageLabel();
        updateAccountIdentity();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!AuthManager.isLoggedIn()) return;
        UserStatsManager.getInstance(this).onSessionPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_open_sidebar) {
            if (drawerLayout != null) {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    drawerLayout.openDrawer(GravityCompat.START);
                }
            }
            return true;
        }
        if (item.getItemId() == R.id.action_logout) {
            confirmLogout();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void confirmLogout() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.logout_title)
                .setMessage(R.string.logout_message)
                .setPositiveButton(R.string.action_logout,
                        (dialog, which) -> AuthManager.signOut(this, this::goToWelcome))
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private void goToWelcome() {
        Intent intent = new Intent(this, WelcomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public boolean onSupportNavigateUp() {
        return (navController != null && appBarConfiguration != null
                && NavigationUI.navigateUp(navController, appBarConfiguration)) || super.onSupportNavigateUp();
    }

    private void requestPermissions() {
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.POST_NOTIFICATIONS
            };
        } else {
            permissions = new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            };
        }

        boolean needsRequest = false;
        for (String perm : permissions) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                needsRequest = true;
                break;
            }
        }

        if (needsRequest) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
        }
    }
}
