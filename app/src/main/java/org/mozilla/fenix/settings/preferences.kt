@file:SuppressWarnings("TooManyFunctions")

package org.mozilla.fenix.settings

import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import org.mozilla.fenix.R

// Utils

internal inline fun <reified T : Preference> PreferenceFragmentCompat.preference(
    key: String
): T? = findPreference(key)

internal inline fun <reified T : Preference> PreferenceFragmentCompat.preference(
    @StringRes stringResource: Int
): T? = preference(value(stringResource))

internal inline fun Fragment.value(@StringRes resId: Int): String =
    getString(resId)

// Preferences

internal inline fun PreferenceFragmentCompat.preferenceDefaultBrowser(): DefaultBrowserPreference? =
    preference(R.string.pref_key_make_default_browser)

internal inline fun PreferenceFragmentCompat.preferenceSearchEngine(): Preference? =
    preference(R.string.pref_key_search_engine_settings)

internal inline fun PreferenceFragmentCompat.preferenceTrackingProtection(): Preference? =
    preference(R.string.pref_key_tracking_protection_settings)

internal inline fun PreferenceFragmentCompat.preferenceTheme(): Preference? =
    preference(R.string.pref_key_theme)

internal inline fun PreferenceFragmentCompat.preferenceAbout(): Preference? =
    preference(R.string.pref_key_about)

internal inline fun PreferenceFragmentCompat.preferenceLeakCanary(): Preference? =
    preference(R.string.pref_key_leakcanary)

internal inline fun PreferenceFragmentCompat.preferenceRemoteDebugging(): Preference? =
    preference(R.string.pref_key_remote_debugging)

internal inline fun PreferenceFragmentCompat.preferenceSignIn(): Preference? =
    preference(R.string.pref_key_sign_in)

internal inline fun PreferenceFragmentCompat.preferenceFirefoxAccount(): Preference? =
    preference(R.string.pref_key_account)

internal inline fun PreferenceFragmentCompat.accountPreferenceCategory(): Preference? =
    preference(R.string.pref_key_account_category)
