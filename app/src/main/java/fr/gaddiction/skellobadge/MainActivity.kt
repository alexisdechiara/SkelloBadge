package fr.gaddiction.skellobadge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.gaddiction.skellobadge.ui.AppViewModel
import fr.gaddiction.skellobadge.ui.home.HomeScreen
import fr.gaddiction.skellobadge.ui.onboarding.OnboardingScreen
import fr.gaddiction.skellobadge.ui.settings.SettingsScreen
import fr.gaddiction.skellobadge.ui.theme.SkelloBadgeTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by lazy {
        androidx.lifecycle.ViewModelProvider(this)[AppViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SkelloBadgeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppRoot()
                }
            }
        }
    }

    /** Chaque retour dans l'application est une occasion de resynchroniser le planning. */
    override fun onStart() {
        super.onStart()
        viewModel.refresh()
    }
}

@Composable
private fun AppRoot() {
    val viewModel: AppViewModel = viewModel()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }

    // La reconfiguration est un état d'écran, pas un état enregistré : la configuration
    // précédente reste valide tant que la nouvelle n'a pas été validée, et en sortir ne
    // laisse jamais l'application à moitié configurée.
    var reconfiguring by remember { mutableStateOf(false) }

    val current = settings
    when {
        current == null -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }

        !current.configured || reconfiguring -> OnboardingScreen(
            viewModel = viewModel,
            settings = current,
            onCancel = if (current.configured) {
                { reconfiguring = false }
            } else {
                null
            },
            onDone = { reconfiguring = false },
        )

        showSettings -> SettingsScreen(
            viewModel = viewModel,
            settings = current,
            onReconfigure = {
                showSettings = false
                reconfiguring = true
            },
            onBack = { showSettings = false },
        )

        else -> HomeScreen(viewModel, current) { showSettings = true }
    }
}
