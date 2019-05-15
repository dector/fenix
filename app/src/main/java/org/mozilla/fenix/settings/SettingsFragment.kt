/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

@file:SuppressWarnings("MaxLineLength")

package org.mozilla.fenix.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.navigation.NavDirections
import androidx.navigation.Navigation
import androidx.preference.Preference
import androidx.preference.Preference.OnPreferenceChangeListener
import androidx.preference.Preference.OnPreferenceClickListener
import androidx.preference.PreferenceFragmentCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import mozilla.components.concept.sync.AccountObserver
import mozilla.components.concept.sync.OAuthAccount
import mozilla.components.concept.sync.Profile
import org.mozilla.fenix.BrowserDirection
import org.mozilla.fenix.Config
import org.mozilla.fenix.FenixApplication
import org.mozilla.fenix.HomeActivity
import org.mozilla.fenix.R
import org.mozilla.fenix.R.string.pref_key_about
import org.mozilla.fenix.R.string.pref_key_accessibility
import org.mozilla.fenix.R.string.pref_key_account
import org.mozilla.fenix.R.string.pref_key_data_choices
import org.mozilla.fenix.R.string.pref_key_feedback
import org.mozilla.fenix.R.string.pref_key_help
import org.mozilla.fenix.R.string.pref_key_language
import org.mozilla.fenix.R.string.pref_key_rate
import org.mozilla.fenix.R.string.pref_key_search_engine_settings
import org.mozilla.fenix.R.string.pref_key_site_permissions
import org.mozilla.fenix.R.string.pref_key_theme
import org.mozilla.fenix.R.string.pref_key_tracking_protection_settings
import org.mozilla.fenix.components.metrics.Event
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.ext.isDeviceVersionNOrLater
import org.mozilla.fenix.ext.requireComponents
import org.mozilla.fenix.settings.SettingsFragmentDirections.Companion.actionSettingsFragmentToAboutFragment
import org.mozilla.fenix.settings.SettingsFragmentDirections.Companion.actionSettingsFragmentToAccessibilityFragment
import org.mozilla.fenix.settings.SettingsFragmentDirections.Companion.actionSettingsFragmentToAccountSettingsFragment
import org.mozilla.fenix.settings.SettingsFragmentDirections.Companion.actionSettingsFragmentToDataChoicesFragment
import org.mozilla.fenix.settings.SettingsFragmentDirections.Companion.actionSettingsFragmentToSearchEngineFragment
import org.mozilla.fenix.settings.SettingsFragmentDirections.Companion.actionSettingsFragmentToSitePermissionsFragment
import org.mozilla.fenix.settings.SettingsFragmentDirections.Companion.actionSettingsFragmentToSyncFragment
import org.mozilla.fenix.settings.SettingsFragmentDirections.Companion.actionSettingsFragmentToThemeFragment
import org.mozilla.fenix.settings.SettingsFragmentDirections.Companion.actionSettingsFragmentToTrackingProtectionFragment
import org.mozilla.fenix.utils.ItsNotBrokenSnack
import org.mozilla.fenix.utils.Settings.Companion as SettingsWrapper

@SuppressWarnings("TooManyFunctions")
class SettingsFragment : PreferenceFragmentCompat(), CoroutineScope by MainScope(), AccountObserver {

    private val homeActivity: HomeActivity get() = activity as HomeActivity

    // TODO Do we still need it?
    private val defaultClickListener = OnPreferenceClickListener { preference ->
        Toast.makeText(context, "${preference.title} Clicked", Toast.LENGTH_SHORT).show()
        true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        updateSignInVisibility()
        registerPreferenceChangeListener()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }

    override fun onResume() {
        super.onResume()

        homeActivity.apply {
            title = getString(R.string.settings_title)
            supportActionBar?.show()
        }

        setupPreferences()
        setupAccountUI()
    }

    override fun onDestroy() {
        coroutineContext.cancel()
        super.onDestroy()
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        handlePreferenceClick(preference.key)
        return super.onPreferenceTreeClick(preference)
    }

    @SuppressWarnings("ComplexMethod")
    private fun handlePreferenceClick(key: String): Unit = when (key) {
        value(pref_key_search_engine_settings) ->
            navigateTo(actionSettingsFragmentToSearchEngineFragment())
        value(pref_key_tracking_protection_settings) ->
            navigateTo(actionSettingsFragmentToTrackingProtectionFragment())
        value(pref_key_site_permissions) ->
            navigateTo(actionSettingsFragmentToSitePermissionsFragment())
        value(pref_key_accessibility) ->
            navigateTo(actionSettingsFragmentToAccessibilityFragment())
        value(pref_key_language) ->
            showIssueSnackbar("220") // TODO #220
        value(pref_key_data_choices) ->
            navigateTo(actionSettingsFragmentToDataChoicesFragment())
        value(pref_key_help) ->
            loadInBrowser(SupportUtils.getSumoURLForTopic(context!!, SupportUtils.SumoTopic.HELP))
        value(pref_key_rate) ->
            openRateScreen()
        value(pref_key_feedback) ->
            loadInBrowser(SupportUtils.FEEDBACK_URL)
        value(pref_key_about) ->
            navigateTo(actionSettingsFragmentToAboutFragment())
        value(pref_key_account) ->
            navigateTo(actionSettingsFragmentToAccountSettingsFragment())
        value(pref_key_theme) ->
            navigateTo(actionSettingsFragmentToThemeFragment())
        else -> Unit
    }

    private fun setupPreferences() {
        preferenceDefaultBrowser()?.apply {
            updateSwitch()
            onPreferenceClickListener = createDefaultBrowserPreferenceListener()
                ?: defaultClickListener
        }
        preferenceSearchEngine()?.summary = context?.let {
            requireComponents.search.searchEngineManager.getDefaultSearchEngine(it).name
        }
        preferenceTrackingProtection()?.summary = context?.let {
            if (SettingsWrapper.getInstance(it).shouldUseTrackingProtection)
                getString(R.string.tracking_protection_on)
            else getString(R.string.tracking_protection_off)
        }
        preferenceTheme()?.summary = context?.let {
            SettingsWrapper.getInstance(it).themeSettingString
        }
        preferenceAbout()?.title = getString(R.string.preferences_about, getString(R.string.app_name))
        preferenceLeakCanary()?.takeIf { !Config.channel.isReleased }?.onPreferenceChangeListener =
            OnPreferenceChangeListener { _, newValue ->
                (context?.applicationContext as FenixApplication).toggleLeakCanary(newValue as Boolean)
                true
            }
        preferenceRemoteDebugging()?.onPreferenceChangeListener = OnPreferenceChangeListener { _, newValue ->
            requireComponents.core.engine.settings.remoteDebuggingEnabled = newValue as Boolean
            true
        }
    }

    private fun setupAccountUI() {
        val accountManager = requireComponents.backgroundServices.accountManager
        // Observe account changes to keep the UI up-to-date.
        accountManager.register(this, owner = this)

        updateAuthState(accountManager.authenticatedAccount())
        accountManager.accountProfile()?.let { updateAccountProfile(it) }
    }

    override fun onAuthenticated(account: OAuthAccount) {
        updateAuthState(account)
    }

    // TODO we could display some error states in this UI.
    override fun onError(error: Exception) {
        /*when (error) {
            is FxaUnauthorizedException -> {
            }
        }*/
    }

    override fun onLoggedOut() {
        updateAuthState()
        updateSignInVisibility()
    }

    override fun onProfileUpdated(profile: Profile) {
        updateAccountProfile(profile)
    }

    private fun createDefaultBrowserPreferenceListener(): OnPreferenceClickListener? =
        if (isDeviceVersionNOrLater())
            OnPreferenceClickListener {
                startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                true
            }
        else null

    private fun navigateTo(direction: NavDirections) {
        Navigation.findNavController(view!!).navigate(direction)
    }

    private fun openRateScreen() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SupportUtils.RATE_APP_URL)))
    }

    private fun loadInBrowser(url: String) {
        homeActivity.openToBrowserAndLoad(
            searchTermOrURL = url,
            newTab = true,
            from = BrowserDirection.FromSettings
        )
    }

    private fun showIssueSnackbar(issueNumber: String) {
        ItsNotBrokenSnack(context!!).showSnackbar(issueNumber)
    }

    private fun registerPreferenceChangeListener() {
        preferenceManager.sharedPreferences.registerOnSharedPreferenceChangeListener { sharedPreferences, key ->
            try {
                context?.let {
                    it.components.analytics.metrics.track(
                        Event.PreferenceToggled(key, sharedPreferences.getBoolean(key, false), it)
                    )
                }
            } catch (e: IllegalArgumentException) {
                // The event is not tracked
            } catch (e: ClassCastException) {
                // The setting is not a boolean, not tracked
            }
        }
    }

    // --- Account UI helpers ---

    private fun updateAuthState(account: OAuthAccount? = null) {
        // Cache the user's auth state to improve performance of sign in visibility
        SettingsWrapper.getInstance(context!!).setHasCachedAccount(account != null)
    }

    private fun updateSignInVisibility() {
        val hasCachedAccount = SettingsWrapper.getInstance(context!!).hasCachedAccount

        preferenceFirefoxAccount()?.isVisible = hasCachedAccount
        accountPreferenceCategory()?.isVisible = hasCachedAccount
        preferenceSignIn()?.isVisible = !hasCachedAccount
        if (!hasCachedAccount) {
            preferenceSignIn()?.onPreferenceClickListener = getClickListenerForSignIn()
        }
    }

    private fun getClickListenerForSignIn() = OnPreferenceClickListener {
        navigateTo(actionSettingsFragmentToSyncFragment())
        true
    }

    private fun updateAccountProfile(profile: Profile) {
        launch {
            preferenceFirefoxAccount()?.apply {
                title = profile.displayName.orEmpty()
                summary = profile.email.orEmpty()
            }
        }
    }
}
