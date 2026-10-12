package io.github.taverndroid

import android.app.Application

class TavernApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServerService.createChannels(this)
        AppLock.install(this)
    }
}
