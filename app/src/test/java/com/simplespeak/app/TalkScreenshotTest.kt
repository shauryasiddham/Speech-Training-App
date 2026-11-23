package com.simplespeak.app

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.simplespeak.app.ai.ChatTurn
import com.simplespeak.app.ai.ReportCard
import com.simplespeak.app.data.Grade
import com.simplespeak.app.data.SkillGrades
import com.simplespeak.app.data.TalkContent
import com.simplespeak.app.ui.AppViewModel
import com.simplespeak.app.ui.SimpleSpeakTheme
import com.simplespeak.app.ui.screens.ChatLine
import com.simplespeak.app.ui.screens.CoachBubble
import com.simplespeak.app.ui.screens.LearnerBubble
import com.simplespeak.app.ui.screens.ReportView
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
class TalkScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun render(name: String, content: @Composable (AppViewModel) -> Unit) {
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        rule.setContent { SimpleSpeakTheme { content(vm) } }
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.activity.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        File("build/screens").apply { mkdirs() }.resolve("$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val wrong = ChatTurn(
        said = "on saturday me and my dad go to the train museum",
        reply = "That sounds like an interesting trip. What did you enjoy most?",
        improved = "On Saturday my dad and I went to the train museum.",
        explanation = "Use “my dad and I” when you are the subject, and “went” because it happened in the past.",
        correct = false,
        grades = SkillGrades(Grade.NEEDS_WORK, Grade.GOOD, Grade.GOOD, Grade.VERY_GOOD),
        mistakes = listOf("pronoun", "verb_tense"), ended = false,
    )
    private val right = wrong.copy(
        said = "I liked the old steam engines because they are very powerful", correct = true, explanation = "",
        grades = SkillGrades(Grade.EXCELLENT, Grade.EXCELLENT, Grade.VERY_GOOD, Grade.EXCELLENT),
    )

    @Test fun conversation() = render("20_talk_graded") { vm ->
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CoachBubble(vm, "What did you do last weekend?")
            LearnerBubble(vm, ChatLine(false, wrong.said, wrong))
            CoachBubble(vm, wrong.reply)
            LearnerBubble(vm, ChatLine(false, right.said, right))
        }
    }

    @Test fun reportCard() = render("21_report_card") { vm ->
        ReportView(
            vm, TalkContent.scenarios.first(),
            ReportCard(
                SkillGrades(Grade.GOOD, Grade.VERY_GOOD, Grade.GOOD, Grade.EXCELLENT),
                "You ordered your food and asked the price. Your questions were clear and polite.",
                listOf("You asked for the price clearly.", "You were polite and said thank you."),
                listOf("Ask questions in the right order, for example “How much is it?”", "Use “I would like” when ordering."),
                "I would like a coffee and a sandwich, please.",
            ),
            listOf(wrong, right, right), onNew = {},
        )
    }
}
