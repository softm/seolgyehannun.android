package net.softm.seolgyehannun

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.LinearLayout
import android.widget.Button
import androidx.activity.OnBackPressedCallback
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import softm.hannun.update.HannunInAppUpdateManager

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private lateinit var offlinePanel: View
    private var mainFrameFailed = false
    private lateinit var ads: CommonNativeAds
    private lateinit var appUpdater: HannunInAppUpdateManager
    private var pendingGeoOrigin: String? = null
    private var pendingGeoCallback: GeolocationPermissions.Callback? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        pendingGeoCallback?.invoke(pendingGeoOrigin, granted, false)
        pendingGeoOrigin = null
        pendingGeoCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appUpdater = HannunInAppUpdateManager(this, null)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        progress = findViewById(R.id.progress)
        offlinePanel = findViewById(R.id.offlinePanel)
        findViewById<Button>(R.id.retryButton).setOnClickListener { retryWebPage() }

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setGeolocationEnabled(true)
            setSupportZoom(true)
            builtInZoomControls = false
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = false
            userAgentString = "$userAgentString SeolgyeHannunAndroid/1.0 AdMobNative/1"
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                mainFrameFailed = false
                offlinePanel.visibility = View.GONE
                progress.visibility = View.VISIBLE
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                val scheme = uri.scheme?.lowercase()
                return when (scheme) {
                    "http", "https" -> false
                    "tel", "mailto", "geo", "kakaomap", "nmap", "tmap", "intent" -> openExternal(uri)
                    else -> false
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                view?.settings?.cacheMode = WebSettings.LOAD_DEFAULT
                if (!mainFrameFailed) offlinePanel.visibility = View.GONE
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) showNetworkError()
            }

            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, response: WebResourceResponse?) {
                if (request?.isForMainFrame == true && (response?.statusCode ?: 0) >= 400) showNetworkError()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                if (origin == null || callback == null) return
                val fineGranted = ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                val coarseGranted = ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                if (fineGranted || coarseGranted) {
                    callback.invoke(origin, true, false)
                } else {
                    pendingGeoOrigin = origin
                    pendingGeoCallback = callback
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.deny()
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else ads.showExitConfirmation()
            }
        })

        if (savedInstanceState == null) loadHomeFresh()
        else webView.restoreState(savedInstanceState)
        ads = CommonNativeAds(this, findViewById<LinearLayout>(R.id.adHost))
        ads.start()
    }

    private fun loadHomeFresh() {
        webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        webView.loadUrl(BuildConfig.WEB_URL, mapOf("Cache-Control" to "no-cache"))
    }

    private fun showNetworkError() {
        mainFrameFailed = true
        progress.visibility = View.GONE
        offlinePanel.visibility = View.VISIBLE
    }

    private fun retryWebPage() {
        mainFrameFailed = false
        offlinePanel.visibility = View.GONE
        progress.visibility = View.VISIBLE
        webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        webView.loadUrl(webView.url ?: BuildConfig.WEB_URL, mapOf("Cache-Control" to "no-cache"))
    }

    private fun openExternal(uri: Uri): Boolean {
        return try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        appUpdater.onResume()
        if (::ads.isInitialized) ads.onResume()
    }

    override fun onPause() {
        if (::ads.isInitialized) ads.onPause()
        appUpdater.onPause()
        super.onPause()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (appUpdater.onActivityResult(requestCode, resultCode)) return
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onDestroy() {
        appUpdater.destroy()
        if (::ads.isInitialized) ads.destroy()
        webView.destroy()
        super.onDestroy()
    }
}
