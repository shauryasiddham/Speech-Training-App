package com.simplespeak.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.SupervisorAccount
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import com.simplespeak.app.ui.AppViewModel
import com.simplespeak.app.ui.Screen
import com.simplespeak.app.ui.SimpleSpeakTheme
import com.simplespeak.app.ui.screens.CaregiverScreen
import com.simplespeak.app.ui.screens.FinishedScreen
import com.simplespeak.app.ui.screens.HelperScreen
import com.simplespeak.app.ui.screens.HomeScreen
import com.simplespeak.app.ui.screens.PracticeScreen
import com.simplespeak.app.ui.screens.SessionScreen
import com.simplespeak.app.ui.screens.SetupScreen

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SimpleSpeakTheme {
                val screen = vm.screen
                // Bottom tabs only on Home and Caregiver, so practice screens stay calm and uncluttered.
                val showTabs = screen == Screen.Home || screen == Screen.Caregiver
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        if (showTabs) {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = screen == Screen.Home,
                                    onClick = { vm.navigate(Screen.Home) },
                                    icon = { Icon(Icons.Rounded.Home, null) },
                                    label = { Text("Practice") },
                                )
                                NavigationBarItem(
                                    selected = screen == Screen.Caregiver,
                                    onClick = { vm.navigate(Screen.Caregiver) },
                                    icon = { Icon(Icons.Rounded.SupervisorAccount, null) },
                                    label = { Text("Caregiver") },
                                )
                            }
                        }
                    },
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        when (screen) {
                            Screen.Setup -> SetupScreen(vm)
                            Screen.Home -> HomeScreen(vm)
                            Screen.Session -> SessionScreen(vm)
                            is Screen.Practice -> PracticeScreen(vm, screen.type)
                            Screen.Helper -> HelperScreen(vm)
                            Screen.Caregiver -> {
                                androidx.activity.compose.BackHandler { vm.navigate(Screen.Home) }
                                CaregiverScreen(vm)
                            }
                            is Screen.Finished -> FinishedScreen(vm, screen.done, screen.correct, screen.stars)
                        }
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        vm.speech.stop()
        vm.speaker.stop()
    }
}
