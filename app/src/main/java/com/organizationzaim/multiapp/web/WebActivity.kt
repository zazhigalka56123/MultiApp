package com.organizationzaim.multiapp.web


import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.*
import com.organizationzaim.multiapp.R
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*


class WebActivity : Activity() {
    private var isConnected = true
    private var webView: WebView? = null
    private var mFilePathCallback: ValueCallback<Array<Uri>>? = null
    private var mCameraPhotoPath: String? = null
    private var URL: String? = null
    private var progressBar: ProgressDialog? = null

    @SuppressLint("SetJavaScriptEnabled", "HardwareIds")
    override fun onCreate(savedInstanceState: Bundle?) {
//        AFApplication()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toScroll(false)
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
        URL = getURL()
        val alertDialog: AlertDialog = AlertDialog.Builder(this).create()
        ProgressDialog.THEME_DEVICE_DEFAULT_DARK

        progressBar = ProgressDialog.show(this, "Loading", "Loading...")

        if (getURL() == null) {
            val intent: Intent = intent
            URL = intent.getStringExtra("banner_url")
            saveURL(URL)
        }


        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)

        webView?.settings?.javaScriptEnabled = true
        webView?.settings?.loadWithOverviewMode = true
        webView?.settings?.useWideViewPort = true
        webView?.settings?.domStorageEnabled = true
        webView?.settings?.databaseEnabled = true
        webView?.settings?.setSupportZoom(false)
        webView?.settings?.allowFileAccess = true
        webView?.settings?.allowContentAccess = true
        webView?.settings?.loadWithOverviewMode = true
        webView?.settings?.useWideViewPort = true

        webView?.settings?.javaScriptEnabled = true
        webView?.settings?.allowFileAccess = true
        webView?.settings?.domStorageEnabled = true
        webView?.settings?.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        webView?.settings?.mediaPlaybackRequiresUserGesture = true

        isConnected = isNetworkAvailable

        webView?.loadUrl(URL!!)
        webView?.setNetworkAvailable(isConnected)

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
                if (progressBar?.isShowing!!) {
                    progressBar?.dismiss()
                }
            }

            override fun onReceivedError(
                view: WebView, errorCode: Int,
                description: String, failingUrl: String
            ) {
                alertDialog.setTitle("Error")
                alertDialog.setMessage(description)
                alertDialog.show()
                if (errorCode == ERROR_TIMEOUT) {
                    view.stopLoading() // may not be needed
                    // view.loadData(timeoutMessageHtml, "text/html", "utf-8");
                }
            }

            @SuppressLint("HardwareIds")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                view.loadUrl(url)
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
                            TAG,
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
                    INPUT_FILE_REQUEST_CODE
                )
                return true
            }
        }

    }

    public override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView!!.canGoBack()) {
            webView!!.goBack()
            return true
        }
        return false
    }


    private fun showInfoMessageDialog() {
        val intent = Intent(this, NetworkFalseActivity::class.java)
        startActivity(intent)
        finish()
    }

    private val isNetworkAvailable2: Boolean
        get() {
            println("isNetworkAvailable2 called")
            val info = (applicationContext
                .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
                .activeNetworkInfo
            return !(info == null || !info.isAvailable || !info.isConnected)
        }

    private val isNetworkAvailable: Boolean
        get() {
            val context = applicationContext
            val connectivity =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val info = connectivity.allNetworkInfo
            for (i in info.indices) {
                if (info[i].state == NetworkInfo.State.CONNECTED) {
                    return true
                }
            }
            return false
        }

    companion object {
        private const val TAG = "MainActivity"
        private const val INPUT_FILE_REQUEST_CODE = 1
    }


    private fun saveURL(url: String?) {
        val sp = getSharedPreferences("SP_WEBVIEW_PREFS", Context.MODE_PRIVATE)
        val editor = sp.edit()
        editor.putString("SAVED_URL", url)
        editor.apply()
    }

    private fun getURL(): String? {
        val sp = getSharedPreferences("SP_WEBVIEW_PREFS", Context.MODE_PRIVATE)
        return sp.getString("SAVED_URL", null)
    }

    private fun toScroll(flag: Boolean){
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
}


//@/%//**********//***(#***/(/****(#/***************,,,****(/*/*//((/*,(&&&&&&&&@&&&&&&&&@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@%((((((((((##(####(#######((((((((#(#(((######(
///*,*************####**(((##/********************//*(#((#*////(#/(/(%&&&&&&&&&&&&@&&&&@@&%%&@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@####(((((((((##########((((#((######((######((
//****/((#%#(##%##/***********(#****************,*//#(((#(/*//(((#%&&&&&&&&&&@@&@%%&&&%%%&&&&&@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@###(##((#((((#######################(########
//********#********/#******/#//*,**,**(((*/(((///**/#(#(##(#//(##&&@&&&&&&&@@&&%&&&%%%&&&&&&&@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@&######((((((############################(###
//&%%&&#####(%%%%%%&&%%%##%#/%****,*%/(*######(**(#(#((#####((##(&&@&@@@&&@%#%&%#%%&&&@&&%&&@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@&&@@@@@@@@@@@@@@@@&#####(((((#################################
//******/***#/***//(#%%%&&&&%(*****/(*/(#(###((#/###########(####@&@@@@&%%%%#%%%%%%%&&&&&&&&@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@&&&&&&&@@@@@@@@@@@@@@@&##(######(############(###################
///(#%#(%%#(/**/##******,#,**(********/#/#(#######(%#########(###@@&%#####%%%%&&&&&&&@@&&&@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@&&@@&&@@&&&&&&&&&&@@@@@@@@@@@@@%%##########################################
///#/**********##/**,****#*/%((/*/(/(#/((######(#################%#%#%%%&&&&@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@&@&@@@&&&&&&&&&&&%&&@@@@@@@@@@&%#(#((#######################################
//****(&%((/******/**,*/#*/#(#/(%#(*//#%###############((#####%%%%%%&%%%%%%&&&%&@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@&&@@@@@@@@@@@&&&&&&&&&&&&&%%%%&&@@@@@@@&######(########################%%#############
//*****//((/***(/*(/////(#%#%%&%(((((####(###############%%%%#%%%%%%&&&&&&&&&&&&@@@@@@@@@@@@@@@@@@@@@@@@@@@@@&&@@@@&&&@@@@@@@@@&&&&&&&&&&&&&&%%%%%%&@@@@@@%####(##########################################
//******(//((((/*/*/**///*/(%######%%############%%%%%%##%%%%&&&&&&&&&&&&&&&&@@@@@@@@@@@@@@@@@@@&&&&&&&&&@@@@@@@@@@@@@@@@@@@@@@&&&&&&&&&&&&&&%%%%%%%&@@@@%&&%%############################################
//******/(#(//(#(**/(///((*(/(#%%######(##%%%%%%%%%%%%%%&&&&&&&&&@@@@@&&@@@@@@@@@@@@@@@&##%%%%%&%&&&&&&&@@@@@@@@@@@@@@@@@@@@@@@&&&&&&&&&&&&&&&&&&&&&&@@@&&&&&&%#####################(######(##############
//*//*//(/(//(((*//((/((/((#(##(#%%###%%%%%%%%%%%&&&&&&&&&&&&&@@@@@@@@&&&@@@@@@@@@@#(###%%&&&&&&&&&@@@@@@@@@@@@@@@@@@@@@@@@@@@@&&&&@@@@@@@@&&&&&&&&%&@@&&&@&&&%%%%##############((##(#####################
//*/(/((//(/(#(/((((/#((((#((((######%%%%%%%&&&&&&&&&&&&&&@@@@@@@&&&&&&&&&%%%%%%&&%#(##%%&@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@&&&&&&&@&&&&&&&%####%#########((#########((################
//(*(#((/(((((((((((((((##(#######%%%%&&&&&&&&&&&&&@@@@@@@@@&&&&&@@@@@@&@%%%&&#%&&%((###%@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@&&&&%%&@&@@@&&&%##############(((##(###((##################
//((((((((/((/#*((((###(#####%%%%%%&&&&&&&&&&&&@@@@@@@@@@&&&&&&&@@@@@@@&%%%&%%%%&&#((((#%@@@@@@@@@@@@@@@@@@@@@@@&%%%&&&&&&&&@@@@@@@@@@@@@@&&&&&&&%%%%&&&@&&&&&####%###########((##########################
//((*/(((((#(((((((#######%%%%%&&&&&&&&&&&&&@@@@@@@@@@@&&&&&@@@@@@@@@&%####%&%&%&&(((((#%@@@@@@@@@@@@@@@@@@@@@@&%#%%&&&&&&&&&&&@@@@@@@@@@&&&&&&&%%%&&&@@&&&&&%###%#################(######################
//#((((((/(#(#((#####%%%%%%&&&&&&&&&&&@@@@@@@@@@@@@@@&&&@@@@@@@@@@%#######%#%%%&%&#(((###%@@@@@@@@@@@@@@@@@@@@&%###%&&&&&&&&&&&&&&@@@&&&&&&&&&&&&&&&&@@@&&&&%%#%%#########################################
//##(((((((###%%%%%%%&&&&&&&&&&&&@@@@@@@@@@@@@@@@@@@@@@@@@@@&%###########%%##%%&%######%&@@@@@@@@@@@@@@@@&&%#####%&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&@&&&&&&##############################################
//&#(#########%%%%%%&&&&&&&&&&&@@@@@@@@@@@@@@@@@@&@@@@@@@&@%#########(#(#(%%%%###&&#####&@&&@@@@@@@@@@&&&&&&%%###(#%&&&&%%&&&&&&&&&&&&&&&&&&&&&&&&&&&@&&&&&%##############################################
//%#######%%%%%&&&&&&&&&&&&@@@@@@@@@@@@@@@@@@@@@@&@@@@@&#################%&%#####&&%#%&&%%%%%&&&&&&&&&&&&&%%%%##((#%&&&&%%%&&&&&&&&&&&&&&&&&&&&&&&&&&@&&&&&%############################################((
//###%%%%%%&&&&&&&&&&&&@@@@@@@@@@@@@@@@@@@@@@@@@&&&@@&################%&&%&&&&%%%&&&&%%##%%%%&&&%%&&&&&&&&&&%#(((##%&&&&%%%%&&&&&&&&&&&&&&&&&&&&&&&&@@@@@&&&&%##########################((((((############
//%%%%&&&&&&&&&&&@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@&@@&(############%#%%%%&&&&%&&&&&@@&%#####%%%%%%%%&&&&&&&&&&&#####%%&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&@@@@@@@&&&&%%##(((((##########################(((#####
//&&&&&&&&&&&@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@&&@@&#(##############%&&&&&&&&&&&&&&&&&%%%###%%%%%%%%&&&&&&&&&&&&@@&%&&@@@@&&@@@@@&@@&&&&&&&&&&&&&&&&&&@@@@@@@@@&%%%%%%######################################
//&&&&&&&&@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@%#####(##############%&&&&&&&&&&&&&&&%%%%##%%##%%%%&&&&&&&&&&&&&@@@@@@@@@@@@@&&&&&&&@&&&&&&&&&&&&&&@@@@@@@@@@&&&%%############################((((((#######
//@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@&##%#################%%&&&&&%&&&&&&&&&&&&&&&%%%%%#%%%&&&&&&&&&&&&&&@@@@@@@@@@@@@@@@&&&&&@@&&&&&&&&&&@@@@@@@@&@&&&@&&%#############################(((((#######
//@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@&##(#####################%%%#%%&&&&&&&&&&@&&&&&%%%%%%%%%&&&@@&&&&&&&@@@@@@@@@@@@@@@@@@@@@@@@@@&&&&&&&@@@@@&&&@@@@&&&&%%%%%############################((((((###(#
//@@@@@@@@@@@@@@@@@@@@@@@@@@@@@&&@@@@@&%###%###########################%&&&&&&&@&&&@@&&&&&&%%%%%&&@@@&&&&&&&&&@@@@@@@@@@@@@@@@@@@@@@@@@@&&&&&@@@@@@@@@&@&@@&&&&%###############################((((#######
//@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@&#(##((##############################%&&&&&&&&&&@&%%&&&&&%%%%&&&@&&&&&&@&&&&&&&&&&@&&&&&&&@@@@@@@@@@@@&&&@@@@@@@@@@@@@&&@@&#%%%###############################((((#####
//@@@@@@@@@@@@@@@@@@@@@@@@&@@@&%######################################%&%&&&@&@&@@&&&&##&&&&&&&&&&&&&&&&&&&&&&&&@@@@@@@@@@@@@&&&&&&&@@@@@&&@@@@@@@@@&&@@@@@&%&&&#################################(((#(####
//@@@@@@@@@@@@@@@@@@@@@@@@&#####(####################################%%%&&&&%@&&&&&@@&##%%&&&&&&&&&&&&&&&&&&&@@@@@@@@@@@@@@@@@@&&&&@@@@@@@@@@@&@@@@&@@&&@@@@@&%%%%#################################((((###
//@@@@@@@@@@@@@@@@@@@@@########(#####################################%&&&&%&&&&&@@@@@%%##%%&&&&&&&&&&&&&&&&&@@@@@@@@@@@@@@@@@@@&&&&@@@@@@@@@@&&&@@&&@&&@%%&@&&@&%%################################((((((##
//@@@@@@@@@@@@@@@@@@%((############################################%%#%%%%&%%&@@@@@@&%%%&%#%%&&&&&&&&&&&&&&&&&&&@@@@@@@@@@&&&&&&&&@@@@@@@@@&&&&&@@@@@%%%&&%%&&%%#%%##############################((#((((((
//@@@@@@@@@@@@@@@@#(####################################################%%%&@@@@@@@@%%%%%&&%%%%@&&@&&&&&&&&&&&&@@@@@@@@@@@@@@&&&&&@@@@@@@&&&&&&&&@@@@@@%%%&%%%#%###%%%%%%####%%####%%%%%%####(##%%%%####((
//@@@@@@@@@@@@@#(########################%%%%%%##%%%###%#%%#####%%#(###%&@@@@@@@@@@@%%%%%%%&&&%%&&&@@@&&&&&&&@@@@@@@@@@@@@@@@@@@@@@@@@@&&&&&&&&&&@@@@@@@@@%%%%%%%%%%#%%%%%%%%%%%%%%%%%%%#%%#((#%%%%%%%%#%#
//@@@@@@@@&##############################%%%%%%###%%###%#%%##(#%%%&&&&&&@@@@@@@@@@@&%%%%%%%%&&&&&%%&@@@&&@@@@@@@@@@@@@@@@@@@@@@@@@@@@&&&&&&&&&&&&@@@@@@@@@@@&%%%%%%%#%%%%%%%%%%%%%%%%%%%%#%####%%%%###%#%%
//&&&&&&&&&&&&&%&&&&%%%%%%%%%%%%%%%&%%%%&&&&&&&&&&&&&%%%%%&&%&&&&&&&&&&&&&@@@@@&&&&&&&%&&&&%%%%&%&&&%%%%%%%%%&&&&&&&&&&&&@@@@&@&@@%%&%%%&&&&&&&&&@@@@@@@@@@@@@&&&&@&&&&&&@@@@&&&&@@&&&&&@@&@&&&&&&&&&&&&&&
//%%%%/.,*,,*(%&&&&&&%%%&&&&&&&&&&&&&&&&&&&&&&&&&&&&&%%%%(//////#%&%&&&&&&@#&@@&&&&@&%%&@&&&%%#,,***,*#&%%&&&&&&&&&&&&&&@@@@@&(,,,*,,/#%%&&&&&@@@@&&@@@@@@&@@&@@@@@@@&@@@@@@@@@@@@@@@@@&@@&#&@@&&@@@@&@&@@
//%%&%/*%&&&#,#&(//*(&%(****/%&%(****/#&&(*/**/#&&&&&%%%%/(%%%%%&&&&&&&&&(*.,/(&&#/****/%@&%%%#,(%%%%,/&#(**/*(%%//&&&&#/%@@@&/,&@&&#,(&%(*****(%%#/****(%&#//****/%&%#/****/#&%#/,***/%%/* ,*/&%/****/#&%
//&%&&/*#%%#/*%&*,&&&%,*%%#%**%(,#&&%#/%(,#&&&(/&&&&&%%%%/*(///#%&&&&&&&&@&,(@&&#,(&&%%,/@&%%%#,(#%#(,(&(/%&%#,/&%,*%&(,(@@@@@/,%%&%*,%@%,#&&%#,/&/,%@@&(#@#,#@@@%,/@%,/%%&%*,&&*/@@@&(/&@%.#@&&/*%@@&((&&
//&%&&/*%&&%&&&&**%&%%,*&&%%%%&&&%#(/,/%&%%#(*,/&&&%&%%&%/(%&&&&&&&&&&&&&@@,(@&&#,(@&%%*/@&%%&#,(%%%%&&%(,*#%#,/%&%,/(,(@&@@@&/,&&#,/&@@#,#&&&&%&@&&%#(,,#@#,#@@@&**@%*/&&@&&%@&*/@&&@&&@@%,(@@&@&%#(/,#&&
//&&&&/*%&%&%&&&**%&&&/,////,/&#,**//,*&#,*//*,*&&&%%%%%&((%&%&&&&&&&&&&@@&*,/(%#**///*.(@&%%&#*#&%%&%&&/,*//*.(&%&#,.(@&&@@@@/,%&&&(,#@%,,(((*,#@/,//(/,(@#.*//(*.(@&/,/(//,/@&/,////./&&#/,*%*(//##*/%&@
//&&&&&%&&&&&&&&&&&&&&%&&&&&&@&%&&&&&&&&&&&&&&&@&&&%%%%&&&&&&&&&&&&&&&&&@@@@&@&&&&&@&&&@@&&%%%&&&%%&&%&%%&&&&&&&%&&#*(@&&@@@@@&@@@@&@@@&@&@@@@@@@&@@@@&@@&@#,%@@@@@@@@@@@@@@@@&@@@@@@@@@@&@@@@@&@@@@@@&@@@
//