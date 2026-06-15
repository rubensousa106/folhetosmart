package com.folhetosmart

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class FolhetoSmartApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val channel = NotificationChannel(
            CHANNEL_PRICE_ALERTS,
            getString(R.string.fcm_channel_alerts),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.fcm_channel_alerts_desc)
        }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_PRICE_ALERTS = "price_alerts"
    }
}
