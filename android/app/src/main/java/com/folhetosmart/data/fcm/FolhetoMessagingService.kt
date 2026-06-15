package com.folhetosmart.data.fcm

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.folhetosmart.FolhetoSmartApp
import com.folhetosmart.MainActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Recebe notificações de alertas de preço via Firebase Cloud Messaging.
 *
 * NOTA: o FCM só fica ativo depois de adicionares o `google-services.json`
 * do teu projeto Firebase em android/app/ e aplicares o plugin
 * `com.google.gms.google-services` no Gradle. Sem isso a app corre na mesma;
 * simplesmente não recebe notificações push.
 */
class FolhetoMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Log.i(TAG, "Novo token FCM recebido")
        // TODO: enviar o token para o backend (users.fcm_token) quando o
        //  utilizador tiver sessão iniciada.
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title
            ?: message.data["title"]
            ?: "FolhetoSmart"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: "Um produto da tua lista baixou de preço!"

        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat
            .Builder(this, FolhetoSmartApp.CHANNEL_PRICE_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        try {
            NotificationManagerCompat.from(this)
                .notify(System.currentTimeMillis().toInt(), notification)
        } catch (e: SecurityException) {
            // Sem permissão POST_NOTIFICATIONS (Android 13+) — ignora.
            Log.w(TAG, "Sem permissão para mostrar notificações", e)
        }
    }

    private companion object {
        const val TAG = "FolhetoFCM"
    }
}
