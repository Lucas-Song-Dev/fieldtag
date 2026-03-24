package com.fieldtag.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fieldtag.FieldTagTestUtils
import com.fieldtag.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SignInNavigationE2ETest {

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
    fun accountMenu_signIn_sharesMessagingScreen() {
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
        composeRule.onAllNodesWithText("Sign in").onFirst().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Sign in to share jobs with your team").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Sign in to share jobs with your team").assertIsDisplayed()
        composeRule.onNodeWithText("Not now").assertIsDisplayed()
    }
}
