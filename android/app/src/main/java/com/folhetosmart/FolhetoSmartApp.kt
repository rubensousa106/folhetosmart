package com.folhetosmart

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.google.android.gms.ads.MobileAds

class FolhetoSmartApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        createNotificationChannels()
        // Inicializa o AdMob. Os anúncios só são pedidos após o consentimento (UMP,
        // tratado na MainActivity) e nunca são mostrados ao ADMIN.
        MobileAds.initialize(this)
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
