package org.chromium.chrome.browser.autodownload;

import android.app.Activity;
import org.chromium.base.ActivityState;
import org.chromium.base.ApplicationStatus;
import org.chromium.base.Log;
import org.chromium.chrome.browser.autodownload.settings.AutoDownloadSettings;
import org.chromium.chrome.browser.offlinepages.ClientId;
import org.chromium.chrome.browser.offlinepages.OfflinePageBridge;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.SadTab;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabCreationState;
import org.chromium.chrome.browser.tab.TabLaunchType;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorTabModelObserver;
import org.chromium.components.offlinepages.SavePageResult;
import org.chromium.content_public.browser.NavigationHandle;
import org.chromium.content_public.browser.WebContents;
import java.util.HashMap;
import java.util.Map;

public class AutoDownloadController {
    private static final String TAG = "AutoDownload";
    private static final Map<Activity, TabModelSelectorTabModelObserver> sTabModelObservers =
            new HashMap<>();

    private static class AutoDownloadTabModelObserver extends TabModelSelectorTabModelObserver {
        private final Map<Tab, AutoDownloadPageObserver> mTabObservers = new HashMap<>();

        public AutoDownloadTabModelObserver(TabModelSelector tabModelSelector) {
            super(tabModelSelector);
            Log.i(TAG, "AutoDownloadTabModelObserver created.");
        }

        public void attachObserverToTab(Tab tab) {
            if (tab == null || mTabObservers.containsKey(tab)) return;
            Log.i(TAG, "Attaching AutoDownloadPageObserver to tab: %d", tab.getId());
            AutoDownloadPageObserver pageObserver = new AutoDownloadPageObserver(tab);
            mTabObservers.put(tab, pageObserver);
            tab.addObserver(pageObserver);
        }

        @Override
        public void didAddTab(
                Tab tab,
                @TabLaunchType int type,
                @TabCreationState int creationState,
                boolean markedForSelection) {
            Log.i(TAG, "didAddTab called for tab: %d", tab.getId());
            attachObserverToTab(tab);
        }

        @Override
        public void tabRemoved(Tab tab) {
            Log.i(TAG, "tabRemoved called for tab: %d", tab.getId());
            AutoDownloadPageObserver pageObserver = mTabObservers.remove(tab);
            if (pageObserver != null) {
                tab.removeObserver(pageObserver);
                Log.i(TAG, "Removed AutoDownloadPageObserver from tab: %d", tab.getId());
            }
        }

        @Override
        public void destroy() {
            super.destroy();
            Log.i(TAG, "Destroying AutoDownloadTabModelObserver and cleaning up tab observers.");
            for (Map.Entry<Tab, AutoDownloadPageObserver> entry : mTabObservers.entrySet()) {
                entry.getKey().removeObserver(entry.getValue());
            }
            mTabObservers.clear();
        }
    }

    public static void observeTabModelSelector(
            Activity activity, TabModelSelector tabModelSelector) {
        Log.i(TAG, "observeTabModelSelector called for activity: %s", activity.getClass().getSimpleName());
        if (sTabModelObservers.containsKey(activity)) {
            Log.i(TAG, "Observer already exists for this activity. Skipping.");
            return;
        }

        Log.i(TAG, "Creating new AutoDownloadTabModelObserver.");
        AutoDownloadTabModelObserver observer = new AutoDownloadTabModelObserver(tabModelSelector);

        // Attach observers to all existing tabs.
        Log.i(TAG, "Attaching observers to existing tabs.");
        for (TabModel model : tabModelSelector.getModels()) {
            Log.i(TAG, "Processing TabModel. Tab count: %d", model.getCount());
            for (int i = 0; i < model.getCount(); i++) {
                observer.attachObserverToTab(model.getTabAt(i));
            }
        }

        sTabModelObservers.put(activity, observer);
        Log.i(TAG, "Observer registered for activity: %s", activity.getClass().getSimpleName());

        ApplicationStatus.registerStateListenerForActivity(
                new ApplicationStatus.ActivityStateListener() {
                    @Override
                    public void onActivityStateChange(Activity activity, int newState) {
                        if (newState == ActivityState.DESTROYED) {
                            Log.i(TAG, "Activity destroyed: %s. Cleaning up observer.", activity.getClass().getSimpleName());
                            TabModelSelectorTabModelObserver destroyedObserver =
                                    sTabModelObservers.remove(activity);
                            if (destroyedObserver != null) {
                                destroyedObserver.destroy();
                            }
                            ApplicationStatus.unregisterActivityStateListener(this);
                        }
                    }
                },
                activity);
    }

    private static class AutoDownloadPageObserver extends EmptyTabObserver {
        private final Tab mTab;

        public AutoDownloadPageObserver(Tab tab) {
            mTab = tab;
        }

        @Override
        public void onDidFinishNavigationInPrimaryMainFrame(Tab tab, NavigationHandle navigation) {
            if (!navigation.hasCommitted() || !navigation.isInPrimaryMainFrame()
                    || navigation.isSameDocument()) {
                return;
            }

            Log.i(TAG, "Navigation finished. Checking if page should be auto-downloaded...");

            if (!AutoDownloadSettings.isAutoDownloadPagesEnabled()) {
                Log.i(TAG, "Auto-download is disabled in settings. Skipping.");
                return;
            }

            if (mTab.isIncognito()) {
                Log.i(TAG, "Skipping download for Incognito tab.");
                return;
            }

            if (mTab.isShowingErrorPage() || SadTab.isShowing(mTab)) {
                Log.i(TAG, "Skipping download for error page.");
                return;
            }

            if (!OfflinePageBridge.canSavePage(mTab.getUrl())) {
                Log.i(TAG, "URL cannot be saved offline: %s", mTab.getUrl());
                return;
            }

            WebContents webContents = mTab.getWebContents();
            if (webContents == null || webContents.isDestroyed()) return;

            Profile profile = mTab.getProfile();
            OfflinePageBridge offlinePageBridge = OfflinePageBridge.getForProfile(profile);
            if (offlinePageBridge == null) {
                Log.e(TAG, "OfflinePageBridge is not available for the current profile.");
                return;
            }

            Log.i(TAG, "Conditions met. Attempting to save page: %s", mTab.getUrl());

            ClientId clientId =
                    ClientId.createGuidClientIdForNamespace(OfflinePageBridge.DOWNLOAD_NAMESPACE);
            offlinePageBridge.savePage(
                    webContents,
                    clientId,
                    (savePageResult, url, offlineId) -> {
                        if (savePageResult == SavePageResult.SUCCESS) {
                            Log.i(TAG, "Successfully saved page with offlineId: %d", offlineId);
                            // ThreadUtils.postOnUiThread(() -> {
                            //     if (mTab.getWebContents() != null
                            //             && mTab.getWebContents().getTopLevelNativeWindow()
                            //                     != null) {
                            //         Toast.show(mTab.getContext(),
                            //                 R.string.auto_download_page_saved_toast,
                            //                 Toast.LENGTH_SHORT);
                            //     }
                            // });
                        } else {
                            Log.e(TAG, "Failed to save page. Result code: %d", savePageResult);
                        }
                    });
        }
    }
}