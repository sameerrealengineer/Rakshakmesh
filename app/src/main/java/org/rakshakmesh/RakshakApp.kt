package org.rakshakmesh

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.rakshakmesh.alerts.AlertScheduler
import org.rakshakmesh.data.AlertRepo
import org.rakshakmesh.data.AppDatabase
import org.rakshakmesh.data.OutboxRepo
import org.rakshakmesh.data.Seeder
import org.rakshakmesh.data.SettingsStore
import org.rakshakmesh.device.LocationSource

/** Simple service locator. No DI framework, to keep the APK small. */
class RakshakApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val db: AppDatabase by lazy { AppDatabase.build(this) }
    val settings: SettingsStore by lazy { SettingsStore(this) }
    val location: LocationSource by lazy { LocationSource(this) }
    val outbox: OutboxRepo by lazy { OutboxRepo(this, db) }
    val alertRepo: AlertRepo by lazy { AlertRepo(db, settings) }

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            runCatching { Seeder(this@RakshakApp, db).seed() }
            val s = settings.get()
            if (s.onboarded && !s.cycloneMode) AlertScheduler.schedule(this@RakshakApp)
        }
    }
}
