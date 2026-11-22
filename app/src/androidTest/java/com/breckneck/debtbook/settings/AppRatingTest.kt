package com.breckneck.debtbook.settings

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.core.net.MailTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.breckneck.debtbook.BuildConfig
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.MainActivity
import com.breckneck.debtbook.settings.rating.appReviewIntent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AppRatingTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val prefs = context.getSharedPreferences("shared_prefs_settings", Context.MODE_PRIVATE)
    private val promptKey = "debts_quantity_for_app_rate_dialog_show"
    private val pinKey = "pin_code_is_enabled"
    private lateinit var originalPrefs: Map<String, *>

    @Before fun prepareSettings() {
        originalPrefs = prefs.all
        prefs.edit().putBoolean(pinKey, false).putInt(promptKey, Int.MAX_VALUE).commit()
    }

    @After fun restoreSettings() {
        val edit = prefs.edit().remove(pinKey).remove(promptKey)
        (originalPrefs[pinKey] as? Boolean)?.let { edit.putBoolean(pinKey, it) }
        (originalPrefs[promptKey] as? Int)?.let { edit.putInt(promptKey, it) }
        edit.commit()
    }

    @Test fun settingsRatingKeepsTheDraftWhenActivityIsRecreated() {
        val mail = instrumentation.addMonitor(
            IntentFilter(Intent.ACTION_SENDTO).apply { addDataScheme("mailto") }, null, true,
        )
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                openRatingFromSettings()
                action(R.string.continue_b).assertIsNotEnabled()
                star(2).performClick().assertIsSelected()
                action(R.string.continue_b).performClick()
                val review = "Отзыв & + 100%\nDeuxième ligne"
                awaitNode(hasSetTextAction()).performTextInput(review)

                scenario.recreate()

                compose.onNodeWithText(context.getString(R.string.rating_score, 2)).assertExists()
                compose.onNode(hasSetTextAction()).assertTextContains(review)
                action(R.string.rating_open_email).performScrollTo().performClick().assertDoesNotExist()
                assertEquals(1, mail.hits)
                assertEquals(Int.MAX_VALUE, prefs.getInt(promptKey, -1))
            }
        } finally {
            instrumentation.removeMonitor(mail)
        }
    }

    @Test fun fourAndFiveStarsOpenTheStore() {
        val market = instrumentation.addMonitor(
            IntentFilter(Intent.ACTION_VIEW).apply { addDataScheme("market") }, null, true,
        )
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                for (rating in 4..5) {
                    openRatingFromSettings()
                    star(rating).performClick().assertIsSelected()
                    action(R.string.rating_open_store).performClick().assertDoesNotExist()
                }
                assertEquals(2, market.hits)
            }
        } finally {
            instrumentation.removeMonitor(market)
        }
    }

    @Test fun automaticPromptAdvancesOnceAfterTheReviewStep() {
        prefs.edit().putInt(promptKey, 0).commit()
        ActivityScenario.launch(MainActivity::class.java).use {
            star(1).performClick()
            action(R.string.continue_b).performClick()
            compose.onNodeWithContentDescription(context.getString(R.string.cancel)).performClick()
            action(R.string.rating_open_email).assertDoesNotExist()
            assertEquals(10, prefs.getInt(promptKey, -1))
        }
    }

    @Test fun mailtoContainsTheVersionRatingAndUnchangedReviewText() {
        val review = "Сбой при 100%: вопрос? a+b & x=y\nВторая строка 🙂"
        for (rating in 1..3) {
            val intent = appReviewIntent(context, rating, review)
            assertEquals(Intent.ACTION_SENDTO, intent.action)
            val mail = MailTo.parse(intent.dataString!!)
            val score = context.getString(R.string.rating_score, rating)
            assertEquals("pavlikbrichkin@yandex.ru", mail.to)
            assertEquals(context.getString(R.string.rating_email_subject, BuildConfig.VERSION_NAME), mail.subject)
            assertEquals("$score\r\n\r\n${review.replace("\n", "\r\n")}", mail.body)
            assertEquals(mail.subject, intent.getStringExtra(Intent.EXTRA_SUBJECT))
            assertEquals("$score\n\n$review", intent.getStringExtra(Intent.EXTRA_TEXT))
        }
    }

    private fun openRatingFromSettings() {
        action(R.string.settings).performClick()
        // Scroll the About group clear of the floating navigation bar.
        awaitNode(hasText(context.getString(R.string.app_version))).performScrollTo()
        action(R.string.rate_app).performClick()
        awaitNode(hasContentDescription(context.getString(R.string.settings_rating, 1)))
    }

    private fun awaitNode(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        compose.waitUntil(5_000) {
            compose.onAllNodes(matcher).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        return compose.onNode(matcher)
    }

    private fun action(id: Int) = compose.onNode(actionMatcher(id))

    private fun actionMatcher(id: Int) = hasText(context.getString(id)) and hasClickAction()

    private fun star(rating: Int) = compose.onNodeWithContentDescription(context.getString(R.string.settings_rating, rating))
}
