package com.cue.reply

import android.app.Application
import com.cue.reply.di.AppContainer

class CueApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
