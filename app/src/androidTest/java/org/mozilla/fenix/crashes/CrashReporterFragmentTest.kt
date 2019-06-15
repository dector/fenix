/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.crashes

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import mozilla.components.browser.session.Session
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.any
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.MockitoAnnotations.initMocks
import org.mozilla.fenix.FenixApplication
import org.mozilla.fenix.R
import org.mozilla.fenix.components.Analytics
import org.mozilla.fenix.components.Core
import org.mozilla.fenix.components.TestComponents
import org.mozilla.fenix.components.UseCases
import org.mozilla.fenix.components.WrappedCrashRecoveryUseCase
import org.mozilla.fenix.components.WrappedCrashReporter
import org.mozilla.fenix.components.WrappedRemoveTabUseCase
import org.mozilla.fenix.components.WrappedSessionManager
import org.mozilla.fenix.components.WrappedSessionUseCases
import org.mozilla.fenix.components.WrappedTabsUseCases
import org.mozilla.fenix.components.metrics.Event
import org.mozilla.fenix.components.metrics.MetricController
import org.mozilla.fenix.helpers.click

@MediumTest
@RunWith(AndroidJUnit4::class)
class CrashReporterFragmentTest {

    @Mock
    lateinit var metricController: MetricController

    @Mock
    lateinit var sessionManager: WrappedSessionManager

    @Mock
    lateinit var crashReporter: WrappedCrashReporter

    @Mock
    lateinit var crashRecoveryUseCase: WrappedCrashRecoveryUseCase

    @Mock
    lateinit var removeTabUseCase: WrappedRemoveTabUseCase

    @Before
    fun setUp() {
        initMocks(this)

        getApplicationContext<FenixApplication>().components.let { it as TestComponents }.apply {
            analytics = mock(Analytics::class.java).also { analytics ->
                doReturn(metricController).`when`(analytics).metrics
                doReturn(crashReporter).`when`(analytics).wrappedCrashReporter
            }
            core = mock(Core::class.java).also { core ->
                doReturn(sessionManager).`when`(core).wrappedSessionManager
            }
            useCases = mock(UseCases::class.java).also { useCases ->
                val sessionUseCases = mock(WrappedSessionUseCases::class.java).also { sessionUseCases ->
                    doReturn(crashRecoveryUseCase).`when`(sessionUseCases).crashRecovery
                }
                doReturn(sessionUseCases).`when`(useCases).wrappedSessionUseCases

                val tabsUseCase = mock(WrappedTabsUseCases::class.java).also { tabsUseCases ->
                    doReturn(removeTabUseCase).`when`(tabsUseCases).removeTab
                }
                doReturn(tabsUseCase).`when`(useCases).wrappedTabsUseCases
            }
        }
    }

    @Test
    fun defaultScreenState() {
        // Given
        launchFragmentScenario()

        // Then
        sendCrashCheckbox.shouldBeChecked()
        closeTabButton.shouldBeDisplayed()
        restoreTabButton.shouldBeDisplayed()

        verify(metricController).track(Event.CrashReporterOpened)
    }

    @Test
    fun noReactionOnInputIfThereIsNoActiveSession() {
        // Given
        doReturn(null).`when`(sessionManager).selectedSession

        launchFragmentScenario()

        // When
        closeTabButton.click()
        restoreTabButton.click()

        // Then
        closeTabButton.shouldBeDisplayed()
        restoreTabButton.shouldBeDisplayed()
        verifyNoMoreInteractions(crashReporter)
    }

    @Test
    fun closeButton_closesFragmentWithoutSendingReport() {
        // Given
        doReturn(Session("")).`when`(sessionManager).selectedSession

        launchFragmentScenario()

        // When
        sendCrashCheckbox.click()
        closeTabButton.click()

        // Then
        verify(metricController).track(Event.CrashReporterClosed(false))
        verify(crashReporter, never()).submitReport(any())
        verify(crashRecoveryUseCase).invoke()
        verifyNoMoreInteractions(removeTabUseCase)
        // Verify is closed
    }

    @Test
    fun closeButton_closesFragmentWithSendingReport() {
        // Given
        val currentSession = Session("")
        doReturn(currentSession).`when`(sessionManager).selectedSession

        launchFragmentScenario()

        // When
        closeTabButton.click()

        // Then
        // Closed
        verify(metricController).track(Event.CrashReporterClosed(false))
        verify(crashReporter).submitReport(any())
        verify(crashRecoveryUseCase).invoke()
        verify(removeTabUseCase).invoke(currentSession)
        // Verify is closed
    }
}

private fun launchFragmentScenario(args: CrashReporterFragmentArgs = createFragmentArguments(Error())) =
    launchFragmentInContainer<CrashReporterFragment>(args.toBundle(), R.style.NormalTheme)

private fun createFragmentArguments(throwable: Throwable) = CrashReporterFragmentArgs(
    Intent().also { intent ->
        intent.putExtra("mozilla.components.lib.crash.CRASH", Bundle().also { bundle ->
            bundle.putSerializable("exception", throwable)
        })
    }
)

private val sendCrashCheckbox get() = onView(withId(R.id.send_crash_checkbox))
private val closeTabButton get() = onView(withId(R.id.close_tab_button))
private val restoreTabButton get() = onView(withId(R.id.restore_tab_button))

private fun ViewInteraction.shouldBeChecked() = check(matches(isChecked()))
private fun ViewInteraction.shouldBeDisplayed() = check(matches(isDisplayed()))
