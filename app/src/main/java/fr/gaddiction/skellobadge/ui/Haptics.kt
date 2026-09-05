package fr.gaddiction.skellobadge.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Retour haptique sur les commandes.
 *
 * On passe par la vue Android plutôt que par LocalHapticFeedback : les constantes de
 * plateforme couvrent des intentions précises — validation, activation, désactivation —
 * là où l'abstraction Compose n'en expose qu'une poignée, et elles respectent le réglage
 * système de retour tactile sans code supplémentaire.
 */
@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}

class Haptics(private val view: View) {

    /** Appui sur un bouton ordinaire. */
    fun click() = perform(HapticFeedbackConstants.CONTEXT_CLICK)

    /** Action qui valide ou confirme quelque chose. */
    fun confirm() = perform(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.CONTEXT_CLICK
        },
    )

    /** Bascule d'un interrupteur ou d'une case à cocher. */
    fun toggle(enabled: Boolean) = perform(
        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.R -> HapticFeedbackConstants.CONTEXT_CLICK
            enabled -> HapticFeedbackConstants.CONFIRM
            else -> HapticFeedbackConstants.REJECT
        },
    )

    /** Incrément ou décrément d'une valeur : sensation de cran. */
    fun tick() = perform(HapticFeedbackConstants.CLOCK_TICK)

    private fun perform(constant: Int) {
        view.performHapticFeedback(constant)
    }
}
