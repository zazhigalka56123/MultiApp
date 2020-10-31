package com.organizationzaim.multiapp

import android.app.Application
import android.util.Log
import com.appsflyer.AppsFlyerConversionListener
import com.appsflyer.AppsFlyerLib
import com.appsflyer.AppsFlyerLibCore.LOG_TAG
import com.onesignal.OSNotification
import com.onesignal.OneSignal
import org.json.JSONException

class SDKClass : Application(), OneSignal.NotificationReceivedHandler{

    override fun onCreate() {
        super.onCreate()

        OneSignal.setLogLevel(OneSignal.LOG_LEVEL.VERBOSE, OneSignal.LOG_LEVEL.NONE);

        OneSignal.startInit(this)
            .setNotificationReceivedHandler(this)
            .inFocusDisplaying(OneSignal.OSInFocusDisplayOption.Notification)
            .unsubscribeWhenNotificationsAreDisabled(true)
            .init()

        val devKey = "qrdZGj123456789"; // ЗДЕСЬ ДОЛЖЕН БЫТЬ ВАШ КЛЮЧ, ПОЛУЧЕННЫЙ ИЗ APPSFLYER !!!
        val conversionDataListener  = object : AppsFlyerConversionListener {
            override fun onConversionDataSuccess(data: MutableMap<String, Any>?) {
                data?.let { cvData ->
                    cvData.map {
                        Log.i(LOG_TAG, "conversion_attribute:  ${it.key} = ${it.value}")
                    }
                }
            }

            override fun onConversionDataFail(error: String?) {
                Log.e(LOG_TAG, "error onAttributionFailure :  $error")
            }

            override fun onAppOpenAttribution(data: MutableMap<String, String>?) {
                data?.map {
                    Log.d(LOG_TAG, "onAppOpen_attribute: ${it.key} = ${it.value}")
                }
            }

            override fun onAttributionFailure(error: String?) {
                Log.e(LOG_TAG, "error onAttributionFailure :  $error")
            }
        }

        AppsFlyerLib.getInstance().init(devKey, conversionDataListener, this)
        AppsFlyerLib.getInstance().startTracking(this)

    }

    // Получение и отправка additionalData
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
}
