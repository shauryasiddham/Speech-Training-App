package com.simplespeak.app

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import com.simplespeak.app.data.Attempt
import com.simplespeak.app.data.ExerciseType
import com.simplespeak.app.data.Fluency
import com.simplespeak.app.ui.AppViewModel
import com.simplespeak.app.ui.Screen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * Opens the real app screens headlessly, taps through them, and saves screenshots
 * to app/build/screens so layouts can be checked without a phone.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun shot(name: String) {
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.activity.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        val dir = File("build/screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Adds two weeks of example practice so the streak and progress screens show real-looking data. */
    private fun seed(vm: AppViewModel) {
        val today = LocalDate.now()
        val days = (0..5).map { today.minusDays(it.toLong()) } + listOf(today.minusDays(8), today.minusDays(9), today.minusDays(11))
        rule.activity.getSharedPreferences("simplespeak", Context.MODE_PRIVATE).edit()
            .putString("practiced_days", days.sorted().joinToString(",")).putInt("best_streak", 9).putInt("total_stars", 142)
            .putInt("level_ANSWER", 4).putInt("level_FIX", 3).putInt("level_JOIN", 3).putInt("level_SEQUENCE", 2).apply()
        days.forEachIndexed { i, d ->
            val t = d.atTime(17, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val thisWeek = !d.isBefore(today.minusDays(6))
            repeat(if (d == today) 6 else 4) { k ->
                vm.store.addAttempt(
                    Attempt(t + k * 60_000L, ExerciseType.practice[(i + k) % 6], 3, "Example", "example answer", (i + k) % 3 != 0,
                        if ((i + k) % 3 == 0) listOf("verb_tense") else emptyList(), 40,
                        Fluency(if (thisWeek) 1800L else 3100L, if (thisWeek) 6000L else 4200L, if (thisWeek) 13 else 8))
                )
            }
        }
        vm.prefs.learnerName = "Alex"
        vm.prefs.interests = "trains, cooking"
        vm.refresh()
    }

    @Test fun walkThroughApp() {
        shot("01_setup")
        rule.onNodeWithText("Get started").performScrollTo().performClick()
        val vm = ViewModelProvider(rule.activity)[AppViewModel::class.java]
        rule.runOnUiThread { seed(vm); vm.navigate(Screen.Progress); vm.navigate(Screen.Home) }
        shot("02_home")
        rule.onNodeWithText("Progress").performClick()
        shot("03_progress")
        rule.onNodeWithText("Talk").performClick()
        shot("04_talk_not_ready")
        rule.onNodeWithText("Caregiver").performClick()
        shot("05_caregiver")
        rule.onNodeWithText("Home").performClick()
        rule.onNodeWithText("Join the ideas").performScrollTo().performClick()
        shot("06_join")
        rule.onNodeWithText("Finish").performClick()
        rule.onNodeWithText("Tell it in order").performScrollTo().performClick()
        shot("07_sequence")
        rule.onNodeWithText("Finish").performClick()
        rule.onNodeWithText("Fix the sentence").performScrollTo().performClick()
        shot("08_fix")
    }
}
