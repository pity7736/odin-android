package dev.raiseexception.odin

import android.app.Application
import dev.raiseexception.odin.di.AppContainer
import dev.raiseexception.odin.shared.domain.FailureRecorder
import dev.raiseexception.odin.shared.infrastructure.LogcatFailureRecorder
import dev.raiseexception.odin.shared.infrastructure.RecordingUncaughtExceptionHandler

class OdinApplication : Application() {

    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val failureRecorder: FailureRecorder = LogcatFailureRecorder()
        Thread.setDefaultUncaughtExceptionHandler(
            RecordingUncaughtExceptionHandler(failureRecorder, Thread.getDefaultUncaughtExceptionHandler())
        )
        System.loadLibrary("sqlcipher")
        this.appContainer = AppContainer(this, failureRecorder)
    }
}
