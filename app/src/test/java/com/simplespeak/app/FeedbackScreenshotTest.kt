package com.simplespeak.app

import android.graphics.Bitmap
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import com.simplespeak.app.data.BetterWord
import com.simplespeak.app.data.Feedback
import com.simplespeak.app.ui.AppViewModel
import com.simplespeak.app.ui.FeedbackCard
import com.simplespeak.app.ui.SimpleSpeakTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class FeedbackScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun render(name: String, fb: Feedback) {
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        rule.setContent {
            SimpleSpeakTheme {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(20.dp)) {
                    FeedbackCard(vm, fb, onTryAgain = {}, onNext = {})
                }
            }
        }
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.activity.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        File("build/screens").apply { mkdirs() }.resolve("$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun incorrectFeedback() = render(
        "10_feedback_try_again",
        Feedback(
            correct = false,
            praise = "You kept the meaning: you went to the park yesterday.",
            learnerText = "yesterday I go to the park",
            improved = "Yesterday I went to the park.",
            tip = "\"Yesterday\" means the past, so \"go\" changes to \"went\".",
            mistakes = listOf("verb_tense"),
            betterWords = listOf(BetterWord("very good", "amazing")),
            organization = "Start with when it happened, then say what you did.",
            nextLevel = "Last weekend I went to the park with my brother, and we played football until it got dark.",
        ),
    )

    @Test fun correctFeedback() = render(
        "11_feedback_correct",
        Feedback(true, "You used \"is\" with \"she\".", "she is tired", "She is tired.", "", emptyList()),
    )
}
