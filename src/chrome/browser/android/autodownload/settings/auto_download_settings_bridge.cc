#include "chrome/browser/android/autodownload/settings/auto_download_settings_bridge.h"

#include "base/android/jni_android.h"
#include "chrome/browser/android/autodownload/settings/auto_download_settings_prefs.h"
#include "chrome/browser/profiles/profile.h"
#include "chrome/browser/profiles/profile_manager.h"
#include "components/prefs/pref_service.h"
#include "chrome/android/chrome_jni_headers/AutoDownloadSettings_jni.h"

namespace autodownload {

static jboolean JNI_AutoDownloadSettings_IsAutoDownloadEnabled(JNIEnv* env) {
  Profile* profile = ProfileManager::GetLastUsedProfile();
  if (!profile) return false;

  PrefService* prefs = profile->GetPrefs();
  if (!prefs) {
    return false;
  }
  return prefs->GetBoolean(
      auto_download_settings::kAutoDownloadPagesEnabled);
}

static void JNI_AutoDownloadSettings_SetAutoDownloadEnabled(
    JNIEnv* env,
    jboolean enabled) {
  Profile* profile = ProfileManager::GetLastUsedProfile();
  if (!profile) return;

  PrefService* prefs = profile->GetPrefs();
  if (!prefs) {
    return;
  }
  prefs->SetBoolean(
      auto_download_settings::kAutoDownloadPagesEnabled, enabled);
}

}  // namespace autodownload