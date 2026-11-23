package com.simplespeak.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.SupervisorAccount
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.simplespeak.app.ui.AppViewModel
import com.simplespeak.app.ui.Screen
import com.simplespeak.app.ui.SimpleSpeakTheme
import com.simplespeak.app.ui.screens.CaregiverScreen
import com.simplespeak.app.ui.screens.FinishedScreen
import com.simplespeak.app.ui.screens.HelperScreen
import com.simplespeak.app.ui.screens.HomeScreen
import com.simplespeak.app.ui.screens.PracticeScreen
import com.simplespeak.app.ui.screens.ProgressScreen
import com.simplespeak.app.ui.screens.SessionScreen
import com.simplespeak.app.ui.screens.SetupScreen
import com.simplespeak.app.ui.screens.TalkScreen

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SimpleSpeakTheme {
                val screen = vm.screen
                val tabs = listOf(
                    Triple(Screen.Home, "Home", Icons.Rounded.Home),
                    Triple(Screen.Talk, "Talk", Icons.Rounded.Forum),
                    Triple(Screen.Progress, "Progress", Icons.Rounded.Insights),
                    Triple(Screen.Caregiver, "Caregiver", Icons.Rounded.SupervisorAccount),
                )
                val showTabs = tabs.any { it.first == screen }
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        if (showTabs) {
                            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp()) {
                                tabs.forEach { (s, label, icon: ImageVector) ->
                                    NavigationBarItem(
                                        selected = screen == s,
                                        onClick = { if (screen != s) vm.navigate(s) },
                                        icon = { Icon(icon, null) },
                                        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
                                    )
                                }
                            }
                        }
                    },
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        if (showTabs && screen != Screen.Home) BackHandler { vm.navigate(Screen.Home) }
                        when (screen) {
                            Screen.Setup -> SetupScreen(vm)
                            Screen.Home -> HomeScreen(vm)
                            Screen.Talk -> TalkScreen(vm)
                            Screen.Progress -> ProgressScreen(vm)
                            Screen.Caregiver -> CaregiverScreen(vm)
                            Screen.Session -> SessionScreen(vm)
                            is Screen.Practice -> PracticeScreen(vm, screen.type)
                            Screen.Helper -> HelperScreen(vm)
                            is Screen.Finished -> FinishedScreen(vm, screen.done, screen.correct, screen.stars, screen.streak, screen.streakGrew)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.checkMac() // the Mac may have been switched off or on while the app was in the background
    }

    override fun onStop() {
        super.onStop()
        vm.speech.stop()
        vm.speaker.stop()
    }
}

private fun Int.dp() = androidx.compose.ui.unit.Dp(this.toFloat())
