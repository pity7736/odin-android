package dev.raiseexception.odin

import android.app.Application
import dev.raiseexception.odin.di.AppContainer

class OdinApplication : Application() {

    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }
}
