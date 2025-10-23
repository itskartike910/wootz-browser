#include "chrome/browser/android/autodownload/settings/auto_download_settings_prefs.h"

#include "components/pref_registry/pref_registry_syncable.h"

namespace auto_download_settings {

const char kAutoDownloadPagesEnabled[] = "download.auto_download_pages_enabled";

void RegisterProfilePrefs(user_prefs::PrefRegistrySyncable* registry) {
  registry->RegisterBooleanPref(kAutoDownloadPagesEnabled, false);
}

}   // namespace auto_download_settings