package com.kARThik.vote

import android.app.Application
import android.content.Context
import com.kARThik.vote.bluetooth.BtVotingCoordinator
import com.kARThik.vote.data.AppDatabase
import com.kARThik.vote.data.ExportManager
import com.kARThik.vote.data.MatamRepository
import com.kARThik.vote.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Hand-rolled dependency container (small app, no DI framework needed). */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db = AppDatabase.get(context)
    val repo = MatamRepository(context.applicationContext, db)
    val settings = SettingsStore(context.applicationContext)
    val exporter = ExportManager(context.applicationContext, repo)
    val bt = BtVotingCoordinator(context.applicationContext, repo, exporter, settings, appScope)
}

class MatamApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appScope.launch { container.settings.ensureDeviceId() }
        container.appScope.launch { container.repo.pauseOngoingOnStart() }
    }
}
