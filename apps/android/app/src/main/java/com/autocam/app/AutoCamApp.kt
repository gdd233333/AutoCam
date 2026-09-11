package com.autocam.app

import android.app.Application
import com.autocam.engine.CameraEngine
import com.autocam.engine.CommandBus
import com.autocam.mock.MockCameraEngine

class AutoCamApp : Application() {
    lateinit var engine: CameraEngine
        private set
    lateinit var bus: CommandBus
        private set

    val isMock: Boolean
        get() = MockCameraEngine.ENGINE_MOCK

    override fun onCreate() {
        super.onCreate()
        val mock = MockCameraEngine()
        engine = mock
        bus = CommandBus(mock)
    }
}
