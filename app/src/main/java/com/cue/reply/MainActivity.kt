package com.cue.reply

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.cue.reply.service.OrchestratorService
import com.cue.reply.system.Permissions
import com.cue.reply.ui.CueApp
import com.cue.reply.ui.theme.CueTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // A reinstall or app update silently unbinds the listener; reconnect it.
        Permissions.rebindNotificationListener(this)

        // Resume the engine on app open when setup is done and it wasn't killed.
        lifecycleScope.launch {
            val s = (application as CueApplication).container.settingsRepository.currentSettings()
            if (s.onboardingDone && s.masterEnabled && !s.killed) {
                OrchestratorService.start(this@MainActivity)
            }
        }

        setContent {
            CueTheme {
                CueApp()
            }
        }
    }
}
