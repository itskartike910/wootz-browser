package org.chromium.chrome.browser.autodownload.settings;

import android.os.Bundle;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;
import org.chromium.base.Log;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.profiles.ProfileManager;
import org.chromium.components.browser_ui.settings.SettingsUtils;
import org.jni_zero.JNINamespace;
import org.jni_zero.NativeMethods;

@JNINamespace("autodownload")
public class AutoDownloadSettings extends PreferenceFragmentCompat {
    private static final String TAG = "AutoDownload";
    private static final String PREF_AUTO_DOWNLOAD_ENABLED = "auto_download_pages_toggle";

    public static boolean isAutoDownloadPagesEnabled() {
        return AutoDownloadSettingsJni.get().isAutoDownloadEnabled();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        getActivity().setTitle(R.string.auto_download_pages);
        SettingsUtils.addPreferencesFromResource(this, R.xml.auto_download_pages_preferences);

        SwitchPreferenceCompat autoDownloadToggle = findPreference(PREF_AUTO_DOWNLOAD_ENABLED);
        if (autoDownloadToggle != null) {
            boolean isEnabled = isAutoDownloadPagesEnabled();
            autoDownloadToggle.setChecked(isEnabled);
            updateSummary(autoDownloadToggle, isEnabled);

            autoDownloadToggle.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean enabled = (Boolean) newValue;
                Log.i(TAG, "Auto-download preference changed by user to: %b", enabled);
                AutoDownloadSettingsJni.get().setAutoDownloadEnabled(enabled);
                updateSummary((SwitchPreferenceCompat) preference, enabled);
                return true;
            });
        }
    }

    private void updateSummary(SwitchPreferenceCompat preference, boolean enabled) {
        int summaryResId = enabled ? R.string.auto_download_pages_enabled_description
                                   : R.string.auto_download_pages_disabled_description;
        preference.setSummary(summaryResId);
    }

    @NativeMethods
    interface Natives {
        void setAutoDownloadEnabled(boolean enabled);
        boolean isAutoDownloadEnabled();
    }
}

