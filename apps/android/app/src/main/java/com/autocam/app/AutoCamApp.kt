package com.autocam.app

import android.app.Application
import com.autocam.engine.CameraEngine
import com.autocam.engine.CommandBus
import com.autocam.flags.FeatureFlags
import com.autocam.flags.PrefsFlagStore
import com.autocam.hal.SwitchingCameraEngine
import com.autocam.observability.DebugBundle
import com.autocam.observability.EventLog
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AutoCamApp : Application() {
    lateinit var engine: CameraEngine
        private set
    lateinit var bus: CommandBus
        private set
    lateinit var flags: FeatureFlags
        private set
    lateinit var eventLog: EventLog
        private set
    lateinit var debugBundle: DebugBundle
        private set

    val isMock: Boolean
        get() = flags.engineMock()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        flags = FeatureFlags(
            PrefsFlagStore(getSharedPreferences(PREFS_NAME, MODE_PRIVATE)),
            debugBuild = BuildConfig.DEBUG,
        )
        eventLog = EventLog(File(filesDir, EventLog.RELATIVE_PATH))
        val switching = SwitchingCameraEngine(this, flags)
        engine = switching
        bus = CommandBus(switching)
        debugBundle = DebugBundle(filesDir, flags, eventLog)
        scope.launch {
            engine.events().collect { eventLog.append(it) }
        }
    }

    companion object {
        const val PREFS_NAME: String = "autocam"
    }
}
