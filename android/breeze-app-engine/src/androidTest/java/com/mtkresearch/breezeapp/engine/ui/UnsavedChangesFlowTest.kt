package com.mtkresearch.breezeapp.engine.ui

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.mtkresearch.breezeapp.engine.R
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.endsWith as matcherEndsWith
import org.hamcrest.Matchers.containsString
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for complete unsaved changes flow (T013)
 *
 * Tests the full user experience:
 * 1. Modify parameter value
 * 2. Attempt to exit via back button
 * 3. Verify dialog appears with 3 buttons
 * 4. Test Save/Discard/Cancel actions work correctly
 *
 * Note: These tests require the Engine service to be available.
 * Run with: ./gradlew :breeze-app-engine:connectedAndroidTest
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class UnsavedChangesFlowTest {

    private lateinit var scenario: ActivityScenario<EngineSettingsActivity>

    @Before
    fun setup() {
        // Launch activity
        val intent = Intent(ApplicationProvider.getApplicationContext(), EngineSettingsActivity::class.java)
        scenario = ActivityScenario.launch(intent)

        // Wait for activity to fully load
        Thread.sleep(3000) // Allow time for RunnerManager initialization
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) {
            scenario.close()
        }
    }

    @Test
    fun `modifying parameter then pressing back shows dialog`() {
        // Wait for UI to be ready
        onView(withId(R.id.spinnerRunners))
            .check(matches(isDisplayed()))

        // Find and modify a parameter (API key input as example)
        // Note: This test may need adjustment based on actual UI structure
        onView(allOf(
            isDescendantOfA(withId(R.id.containerParameters)),
            withClassName(matcherEndsWith("EditText"))
        ))
            .perform(scrollTo(), replaceText("modified_value"))

        // Press back button
        pressBack()

        // Verify unsaved changes dialog appears
        onView(withText(containsString("Unsaved Changes")))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        // Verify Save button exists
        onView(withText("Save"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        // Verify Discard button exists
        onView(withText("Discard"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        // Verify Cancel button exists
        onView(withText("Cancel"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
    }

    @Test
    fun `cancel button dismisses dialog and stays in activity`() {
        // Modify parameter
        onView(allOf(
            isDescendantOfA(withId(R.id.containerParameters)),
            withClassName(matcherEndsWith("EditText"))
        ))
            .perform(scrollTo(), replaceText("modified_value"))

        // Press back
        pressBack()

        // Click Cancel button
        onView(withText("Cancel"))
            .inRoot(isDialog())
            .perform(click())

        // Verify dialog is dismissed and we're still in the activity
        onView(withId(R.id.spinnerRunners))
            .check(matches(isDisplayed()))

        // Verify the modified value is still there
        onView(allOf(
            isDescendantOfA(withId(R.id.containerParameters)),
            withClassName(matcherEndsWith("EditText"))
        ))
            .check(matches(withText("modified_value")))
    }

    @Test
    fun `discard button exits without saving`() {
        // Modify parameter
        onView(allOf(
            isDescendantOfA(withId(R.id.containerParameters)),
            withClassName(matcherEndsWith("EditText"))
        ))
            .perform(scrollTo(), replaceText("modified_value"))

        // Press back
        pressBack()

        // Click Discard button
        onView(withText("Discard"))
            .inRoot(isDialog())
            .perform(click())

        // Verify activity is finished (this will throw if activity still exists)
        scenario.onActivity { activity ->
            assert(activity.isFinishing || activity.isDestroyed) {
                "Activity should be finishing after discard"
            }
        }
    }

    @Test
    fun `save button persists changes and exits`() {
        // Modify parameter
        onView(allOf(
            isDescendantOfA(withId(R.id.containerParameters)),
            withClassName(matcherEndsWith("EditText"))
        ))
            .perform(scrollTo(), replaceText("modified_value"))

        // Press back
        pressBack()

        // Click Save button
        onView(withText("Save"))
            .inRoot(isDialog())
            .perform(click())

        // Wait for save operation
        Thread.sleep(2000)

        // Verify activity is finishing after save
        scenario.onActivity { activity ->
            assert(activity.isFinishing || activity.isDestroyed) {
                "Activity should be finishing after save"
            }
        }
    }

    @Test
    fun `no changes means back button exits immediately`() {
        // Don't modify anything, just press back
        pressBack()

        // Verify no dialog appears and activity finishes
        scenario.onActivity { activity ->
            assert(activity.isFinishing || activity.isDestroyed) {
                "Activity should finish immediately when no changes"
            }
        }
    }

    @Test
    fun `save button disabled initially and enabled after modification`() {
        // Initially, Save button should be disabled
        onView(withId(R.id.btnSave))
            .check(matches(isNotEnabled()))

        // Modify a parameter
        onView(allOf(
            isDescendantOfA(withId(R.id.containerParameters)),
            withClassName(matcherEndsWith("EditText"))
        ))
            .perform(scrollTo(), replaceText("modified_value"))

        // Now Save button should be enabled
        onView(withId(R.id.btnSave))
            .check(matches(isEnabled()))
    }

    @Test
    fun `clicking save button directly works without dialog`() {
        // Modify a parameter
        onView(allOf(
            isDescendantOfA(withId(R.id.containerParameters)),
            withClassName(matcherEndsWith("EditText"))
        ))
            .perform(scrollTo(), replaceText("modified_value"))

        // Click Save button directly (not via back button dialog)
        onView(withId(R.id.btnSave))
            .perform(click())

        // Wait for save operation
        Thread.sleep(2000)

        // Verify save succeeded (Save button disabled again or activity finishing)
        onView(withId(R.id.btnSave))
            .check(matches(isNotEnabled()))
    }
}
