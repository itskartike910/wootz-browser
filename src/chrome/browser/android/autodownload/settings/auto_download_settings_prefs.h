#ifndef CHROME_BROWSER_ANDROID_AUTODOWNLOAD_SETTINGS_AUTO_DOWNLOAD_SETTINGS_PREFS_H_
#define CHROME_BROWSER_ANDROID_AUTODOWNLOAD_SETTINGS_AUTO_DOWNLOAD_SETTINGS_PREFS_H_

namespace user_prefs {
class PrefRegistrySyncable;
}

namespace auto_download_settings {

// Registers the preferences used by the auto download pages component.
void RegisterProfilePrefs(user_prefs::PrefRegistrySyncable* registry);

// Preference name constant
extern const char kAutoDownloadPagesEnabled[];

}  // namespace auto_download_settings

#endif  // CHROME_BROWSER_ANDROID_AUTODOWNLOAD_SETTINGS_AUTO_DOWNLOAD_SETTINGS_PREFS_H_