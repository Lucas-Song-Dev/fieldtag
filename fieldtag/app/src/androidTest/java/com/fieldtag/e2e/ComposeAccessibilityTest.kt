package com.fieldtag.e2e

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.espresso.accessibility.AccessibilityChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fieldtag.FieldTagTestUtils
import com.fieldtag.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith

/**
 * Accessibility-oriented checks using Compose semantics (click actions, labels, minimum sizes).
 * [AccessibilityChecks] is enabled for any hybrid View / Espresso paths in the same process.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ComposeAccessibilityTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun enableGlobalAccessibilityChecks() {
            AccessibilityChecks.enable()
        }
    }

    @get:Rule(order = 0)
    val clearDataStoreRule = object : ExternalResource() {
        override fun before() {
            FieldTagTestUtils.clearPreferencesDataStore(
                InstrumentationRegistry.getInstrumentation().targetContext,
            )
        }
    }

    @get:Rule(order = 1)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun welcome_primaryActions_areClickable_withMinimumHeight() {
        hiltRule.inject()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText("Continue without account").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Continue without account")
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText("Sign in")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun projectList_accountAndFab_areLabeledAndClickable() {
        hiltRule.inject()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText("Continue without account").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Continue without account").performClick()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithContentDescription("Account").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Account")
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onNodeWithContentDescription("New Project")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun accountMenu_signIn_entryHasClickAction() {
        hiltRule.inject()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText("Continue without account").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Continue without account").performClick()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithContentDescription("Account").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Account").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Sign in").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Sign in").assertHasClickAction()
    }
}
