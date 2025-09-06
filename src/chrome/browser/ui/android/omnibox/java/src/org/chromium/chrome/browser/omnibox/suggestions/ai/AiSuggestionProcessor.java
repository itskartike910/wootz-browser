package org.chromium.chrome.browser.omnibox.suggestions.ai;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import org.chromium.base.Log;
import org.chromium.chrome.browser.omnibox.R;
import org.chromium.chrome.browser.omnibox.styles.OmniboxDrawableState;
import org.chromium.chrome.browser.omnibox.styles.OmniboxImageSupplier;
import org.chromium.chrome.browser.omnibox.styles.SuggestionSpannable;
import org.chromium.chrome.browser.omnibox.suggestions.SuggestionHost;
import org.chromium.chrome.browser.omnibox.suggestions.base.BaseSuggestionViewProcessor;
import org.chromium.chrome.browser.omnibox.suggestions.base.BaseSuggestionViewProperties;
import org.chromium.components.omnibox.AutocompleteMatch;
import org.chromium.components.omnibox.suggestions.OmniboxSuggestionUiType;
import org.chromium.ui.modelutil.PropertyModel;
import org.chromium.url.GURL;

import java.util.Optional;

/** A class that handles model and view creation for AI suggestions. */
public class AiSuggestionProcessor extends BaseSuggestionViewProcessor {
    private static final int AI_SUGGESTION_COUNT = 6;
    private static final long DEBOUNCE_DELAY_MS = 2000; // 2 seconds
    
    private String mCurrentQuery = "";
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private Runnable mDebounceRunnable;
    private boolean mIsLoading = false;
    private Runnable mRefreshCallback;

    public AiSuggestionProcessor(
            @NonNull Context context,
            @NonNull SuggestionHost suggestionHost,
            @NonNull Optional<OmniboxImageSupplier> imageSupplier) {
        super(context, suggestionHost, imageSupplier);
    }

    /**
     * Set a callback to be called when the debounce completes and suggestions should be refreshed.
     */
    public void setRefreshCallback(Runnable refreshCallback) {
        mRefreshCallback = refreshCallback;
    }

    @Override
    public boolean doesProcessSuggestion(@NonNull AutocompleteMatch suggestion, int position) {
        // Only show AI suggestions when user is typing (has a query)
        // and only for the first 6 positions
        return !mCurrentQuery.isEmpty() && position < AI_SUGGESTION_COUNT;
    }

    @Override
    public int getViewTypeId() {
        return OmniboxSuggestionUiType.AI_SUGGESTION;
    }

    @Override
    public @NonNull PropertyModel createModel() {
        return new PropertyModel(AiSuggestionViewProperties.ALL_KEYS);
    }

    @Override
    public void populateModel(
            @NonNull AutocompleteMatch suggestion, @NonNull PropertyModel model, int position) {
        super.populateModel(suggestion, model, position);
        setStateForAiSuggestion(model, position, suggestion);

        // Set the icon for AI suggestions using the existing icon system
        OmniboxDrawableState icon = OmniboxDrawableState.forSmallIcon(mContext, R.drawable.ic_wootzapp, false);
        model.set(BaseSuggestionViewProperties.ICON, icon);
        
        // Override the click handler for AI suggestions
        model.set(BaseSuggestionViewProperties.ON_CLICK, 
                 () -> onAiSuggestionClicked(suggestion, position));
    }

    /**
     * Update the current query to determine when to show AI suggestions.
     * Implements debounce mechanism - shows loading state immediately, then content after 2 seconds.
     */
    public void updateQuery(String query) {
        String newQuery = query != null ? query : "";
        
        // Cancel any existing debounce runnable
        if (mDebounceRunnable != null) {
            mHandler.removeCallbacks(mDebounceRunnable);
        }
        
        mCurrentQuery = newQuery;
        
        if (!newQuery.isEmpty()) {
            // Set loading state immediately
            mIsLoading = true;
            
            // Create new debounce runnable
            mDebounceRunnable = () -> {
                mIsLoading = false;
                // Trigger a refresh of the suggestions to update the UI with AI subtext
                Log.i("AISuggestions", "Debounce completed, showing AI subtext for: " + mCurrentQuery);
                if (mRefreshCallback != null) {
                    mRefreshCallback.run();
                }
            };
            
            // Schedule the debounce
            mHandler.postDelayed(mDebounceRunnable, DEBOUNCE_DELAY_MS);
        } else {
            mIsLoading = false;
        }
    }

    /**
     * Override to ensure AI suggestions are only shown when user is typing.
     */
    @Override
    public void onSuggestionsReceived() {
        // This is called when new suggestions are received
        // We could potentially inject AI suggestions here in the future
    }

    private void setStateForAiSuggestion(PropertyModel model, int position, AutocompleteMatch suggestion) {
        // Set AI-specific properties
        model.set(AiSuggestionViewProperties.IS_AI_SUGGESTION, true);
        model.set(AiSuggestionViewProperties.IS_LOADING, mIsLoading);
        
        // Always show the header text from the Google suggestion immediately
        String headerText = suggestion.getDisplayText();

        String description = suggestion.getDescription(); // Suggestion description
        String fillIntoEdit = suggestion.getFillIntoEdit(); // What goes in omnibox
        String destinationUrl = suggestion.getUrl().getSpec(); // Target URL

        Log.i("AISuggestions", "Description: " + description);
        Log.i("AISuggestions", "Fill into edit: " + fillIntoEdit);
        Log.i("AISuggestions", "Destination URL: " + destinationUrl);
        
        SuggestionSpannable headerSpannable = new SuggestionSpannable(headerText);
        model.set(AiSuggestionViewProperties.TEXT_LINE_1_TEXT, headerSpannable);
        
        if (mIsLoading) {
            // Show shimmer effect for subtext when loading
            // Don't set subtext content - the view will show shimmer effect
            return;
        }
        
        // Set AI-generated subtext after debounce completes
        String subText = "AI-enhanced result for: " + headerText;
        SuggestionSpannable subSpannable = new SuggestionSpannable(subText);
        model.set(AiSuggestionViewProperties.TEXT_LINE_2_TEXT, subSpannable);
    }

    private void onAiSuggestionClicked(AutocompleteMatch suggestion, int position) {
        // Get the header text from the suggestion
        String headerText = suggestion.getDisplayText();
        
        // Create the wootzapp://chat URL with the header text
        String chatUrl = "wootzapp://chat/?q=" + headerText;
        
        Log.i("AISuggestions", "AI Suggestion clicked, navigating to: " + chatUrl);
        
        // Create a GURL for the custom scheme
        GURL wootzappUrl = new GURL(chatUrl);
        
        // Use the suggestion host to navigate to the wootzapp URL
        mSuggestionHost.onSuggestionClicked(suggestion, position, wootzappUrl);
    }
}
