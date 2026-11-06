package com.example.examprep.ui.settings;

import com.example.examprep.util.UserPaths;

import android.os.Bundle;
import android.text.InputType;
import android.widget.Toast;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import com.example.examprep.R;
import com.example.examprep.util.SecurePrefs;

public class SettingsFragment extends PreferenceFragmentCompat {

    private SecurePrefs securePrefs;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        getPreferenceManager().setSharedPreferencesName(UserPaths.prefsName());
        setPreferencesFromResource(R.xml.preferences, rootKey);
        securePrefs = new SecurePrefs(requireContext());

        // API Key preference
        EditTextPreference apiKeyPref = findPreference("api_key");
        if (apiKeyPref != null) {
            apiKeyPref.setOnBindEditTextListener(editText -> {
                editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                editText.setSelection(editText.getText().length());
            });
            
            // Show current status
            updateApiKeySummary(apiKeyPref);
            
            apiKeyPref.setOnPreferenceChangeListener((preference, newValue) -> {
                String key = (String) newValue;
                if (key != null && !key.trim().isEmpty()) {
                    securePrefs.setApiKey(key.trim());
                    updateApiKeySummary(apiKeyPref);
                    Toast.makeText(requireContext(), "API key saved securely", Toast.LENGTH_SHORT).show();
                }
                return false; // Don't save to default prefs
            });
        }

        // Daily cap preference
        EditTextPreference capPref = findPreference("daily_cap");
        if (capPref != null) {
            capPref.setOnBindEditTextListener(editText -> {
                editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            });
            
            capPref.setSummary(String.format("$%.2f per day", securePrefs.getDailyCap()));
            
            capPref.setOnPreferenceChangeListener((preference, newValue) -> {
                try {
                    double cap = Double.parseDouble((String) newValue);
                    if (cap <= 0) {
                        Toast.makeText(requireContext(), "Cap must be greater than 0", Toast.LENGTH_SHORT).show();
                        return false;
                    }
                    securePrefs.setDailyCap(cap);
                    capPref.setSummary(String.format("$%.2f per day", cap));
                    Toast.makeText(requireContext(), "Daily cap updated", Toast.LENGTH_SHORT).show();
                } catch (NumberFormatException e) {
                    Toast.makeText(requireContext(), "Invalid number", Toast.LENGTH_SHORT).show();
                }
                return false;
            });
        }

        // Language preference
        ListPreference langPref = findPreference("language");
        if (langPref != null) {
            langPref.setSummary(langPref.getEntry());
            langPref.setOnPreferenceChangeListener((preference, newValue) -> {
                int index = langPref.findIndexOfValue((String) newValue);
                if (index >= 0) {
                    langPref.setSummary(langPref.getEntries()[index]);
                }
                return true;
            });
        }

        // Today's spending display
        Preference spendingPref = findPreference("today_spending");
        if (spendingPref != null) {
            double spent = securePrefs.getTodaySpending();
            double cap = securePrefs.getDailyCap();
            spendingPref.setSummary(String.format("$%.4f / $%.2f", spent, cap));
        }

        // Validate API key button
        Preference validatePref = findPreference("validate_key");
        if (validatePref != null) {
            validatePref.setOnPreferenceClickListener(preference -> {
                if (!securePrefs.hasApiKey()) {
                    Toast.makeText(requireContext(), "No API key set", Toast.LENGTH_SHORT).show();
                } else {
                    String key = securePrefs.getApiKey();
                    if (key.startsWith("sk-")) {
                        Toast.makeText(requireContext(), "API key format looks valid (starts with 'sk-')", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), "Warning: OpenAI keys typically start with 'sk-'. Please verify.", Toast.LENGTH_LONG).show();
                    }
                }
                return true;
            });
        }

        // Clear API key button
        Preference clearPref = findPreference("clear_key");
        if (clearPref != null) {
            clearPref.setOnPreferenceClickListener(preference -> {
                securePrefs.clearApiKey();
                updateApiKeySummary(apiKeyPref);
                Toast.makeText(requireContext(), "API key cleared", Toast.LENGTH_SHORT).show();
                return true;
            });
        }
    }

    private void updateApiKeySummary(EditTextPreference apiKeyPref) {
        if (apiKeyPref != null) {
            if (securePrefs.hasApiKey()) {
                String key = securePrefs.getApiKey();
                // Show only last 4 chars
                String masked = "••••••••" + key.substring(Math.max(0, key.length() - 4));
                apiKeyPref.setSummary("Set: " + masked);
            } else {
                apiKeyPref.setSummary("Not set — tap to enter your ChatGPT (OpenAI) API key");
            }
        }
    }
}
