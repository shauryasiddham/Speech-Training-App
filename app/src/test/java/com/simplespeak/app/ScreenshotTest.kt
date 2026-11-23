package com.simplespeak.app

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

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

    @Test fun walkThroughApp() {
        shot("01_setup")
        rule.onNodeWithText("Start").performClick()
        shot("02_home")
        rule.onNodeWithText("Start practice").performClick()
        shot("03_session_build")
        rule.onNodeWithContentDescription("Stop practice").performClick()
        rule.onNodeWithText("Stop").performClick()
        rule.onNodeWithText("Caregiver").performClick()
        shot("04_caregiver")
        rule.onNodeWithText("Practice").performClick()
        rule.onNodeWithText("Fix the sentence").performScrollTo().performClick()
        shot("05_fix")
        rule.onNodeWithText("Finish").performClick()
        rule.onNodeWithText("Plan my sentence").performScrollTo().performClick()
        shot("06_plan")
        rule.onNodeWithText("Finish").performClick()
        rule.onNodeWithText("Help me say it").performScrollTo().performClick()
        shot("07_helper")
    }
}
