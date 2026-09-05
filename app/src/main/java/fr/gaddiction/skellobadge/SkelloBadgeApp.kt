package fr.gaddiction.skellobadge

import android.app.Application
import fr.gaddiction.skellobadge.notify.Notifications
import fr.gaddiction.skellobadge.schedule.RefreshWorker

class SkelloBadgeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        // KEEP : si le travail périodique existe déjà, on ne réinitialise pas son cycle.
        RefreshWorker.enqueuePeriodic(this)
    }
}
