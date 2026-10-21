package com.hermes.pccontrol

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.PrintWriter
import java.io.StringWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var errorLayout: LinearLayout
    private lateinit var errorText: TextView
    private lateinit var progressBar: ProgressBar
    private val defaultUrl = "http://100.82.252.86:8899"
    private var isErrorShowing = false
    private val autoRetryHandler = Handler(Looper.getMainLooper())
    private var autoRetryRunnable: Runnable? = null
    private val timeoutHandler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            // Dark Status Bar & Navigation Bar
            window.statusBarColor = Color.parseColor("#181818")
            window.navigationBarColor = Color.parseColor("#181818")

            val root = FrameLayout(this).apply {
                setBackgroundColor(Color.parseColor("#181818"))
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            // Window insets safe margins (camera cutout & gesture bar)
            ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }

            // High Performance WebView
            webView = WebView(this).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(Color.parseColor("#181818"))
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    allowFileAccess = true
                    mediaPlaybackRequiresUserGesture = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    try {
                        val ua = userAgentString ?: ""
                        userAgentString = "$ua TaskManagerApp/2.0"
                    } catch (_: Exception) {}
                }
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
            }

            // Top thin progress bar
            progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                isIndeterminate = true
                visibility = View.VISIBLE
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    8
                ).apply {
                    gravity = Gravity.TOP
                }
            }

            // Offline / Error Layout
            errorLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundColor(Color.parseColor("#181818"))
                visibility = View.GONE
                setPadding(48, 48, 48, 48)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            val errIcon = TextView(this).apply {
                text = "⚡"
                textSize = 54f
                gravity = Gravity.CENTER
            }

            val errTitle = TextView(this).apply {
                text = "Немає зв'язку з комп'ютером"
                textSize = 20f
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, 24, 0, 12)
            }

            errorText = TextView(this).apply {
                text = "Перевірте, чи увімкнено Tailscale VPN на телефоні.\nСервер: $defaultUrl"
                textSize = 14f
                setTextColor(Color.parseColor("#a0a0a0"))
                gravity = Gravity.CENTER
                setLineSpacing(8f, 1f)
                setPadding(0, 0, 0, 36)
            }

            val retryBtn = Button(this).apply {
                text = "Повторити спробу"
                setBackgroundColor(Color.parseColor("#0078d4"))
                setTextColor(Color.WHITE)
                setPadding(32, 16, 32, 16)
                setOnClickListener {
                    retryLoading()
                }
            }

            val changeUrlBtn = Button(this).apply {
                text = "Змінити IP"
                setBackgroundColor(Color.parseColor("#272727"))
                setTextColor(Color.parseColor("#60cdff"))
                setPadding(32, 16, 32, 16)
                setOnClickListener {
                    showChangeUrlDialog()
                }
            }

            val btnRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                addView(retryBtn)
                val spacer = View(this@MainActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(24, 1)
                }
                addView(spacer)
                addView(changeUrlBtn)
            }

            errorLayout.addView(errIcon)
            errorLayout.addView(errTitle)
            errorLayout.addView(errorText)
            errorLayout.addView(btnRow)

            root.addView(webView)
            root.addView(errorLayout)
            root.addView(progressBar)

            setContentView(root)

            setupWebView()
            setupBackHandler()

            retryLoading()

        } catch (t: Throwable) {
            showCrashScreen(t)
        }
    }

    private fun showCrashScreen(t: Throwable) {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        val stackTrace = sw.toString()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#181818"))
            setPadding(32, 64, 32, 32)
        }
        val title = TextView(this).apply {
            text = "Помилка запуску додатка"
            textSize = 20f
            setTextColor(Color.RED)
            setTypeface(null, Typeface.BOLD)
        }
        val msg = TextView(this).apply {
            text = "${t.javaClass.simpleName}: ${t.message}\n\n$stackTrace"
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding(0, 16, 0, 0)
        }
        val scroll = ScrollView(this).apply {
            addView(msg)
        }
        layout.addView(title)
        layout.addView(scroll)
        setContentView(layout)
    }

    private fun retryLoading() {
        stopAutoRetry()
        cancelLoadTimeout()
        isErrorShowing = false
        errorLayout.visibility = View.GONE
        webView.visibility = View.VISIBLE
        progressBar.visibility = View.VISIBLE
        scheduleLoadTimeout()
        webView.loadUrl(getServerUrl())
    }

    private fun scheduleLoadTimeout() {
        cancelLoadTimeout()
        timeoutRunnable = Runnable {
            if (!isErrorShowing && progressBar.visibility == View.VISIBLE) {
                showErrorUI("Час очікування відповіді вичерпано")
            }
        }
        timeoutHandler.postDelayed(timeoutRunnable!!, 7000)
    }

    private fun cancelLoadTimeout() {
        timeoutRunnable?.let { timeoutHandler.removeCallbacks(it) }
        timeoutRunnable = null
    }

    private fun showErrorUI(reason: String? = null) {
        runOnUiThread {
            cancelLoadTimeout()
            progressBar.visibility = View.GONE
            webView.visibility = View.GONE
            isErrorShowing = true
            val extra = if (!reason.isNullOrBlank()) "\n($reason)" else ""
            errorText.text = "Не вдалося з'єднатися з ПК:\n${getServerUrl()}$extra\n\nПеревірте Tailscale VPN на телефоні.\n(Автоматичне перепідключення...)"
            errorLayout.visibility = View.VISIBLE
            startAutoRetry()
        }
    }

    private fun startAutoRetry() {
        stopAutoRetry()
        autoRetryRunnable = object : Runnable {
            override fun run() {
                if (!isErrorShowing) return
                thread {
                    var reachable = false
                    try {
                        val url = URL("${getServerUrl()}/api/system")
                        val conn = url.openConnection() as HttpURLConnection
                        conn.connectTimeout = 1500
                        conn.readTimeout = 1500
                        conn.requestMethod = "GET"
                        reachable = (conn.responseCode == 200)
                        conn.disconnect()
                    } catch (_: Exception) {
                        reachable = false
                    }
                    if (reachable) {
                        runOnUiThread {
                            if (isErrorShowing) {
                                retryLoading()
                            }
                        }
                    } else {
                        autoRetryHandler.postDelayed(this, 2500)
                    }
                }
            }
        }
        autoRetryHandler.postDelayed(autoRetryRunnable!!, 2500)
    }

    private fun stopAutoRetry() {
        autoRetryRunnable?.let { autoRetryHandler.removeCallbacks(it) }
        autoRetryRunnable = null
    }

    private fun getServerUrl(): String {
        val prefs = getSharedPreferences("taskmgr_config", Context.MODE_PRIVATE)
        return prefs.getString("server_url", defaultUrl) ?: defaultUrl
    }

    private fun saveServerUrl(url: String) {
        val prefs = getSharedPreferences("taskmgr_config", Context.MODE_PRIVATE)
        prefs.edit().putString("server_url", url.trim()).apply()
    }

    private fun showChangeUrlDialog() {
        val input = EditText(this).apply {
            setText(getServerUrl())
            setTextColor(Color.BLACK)
        }
        AlertDialog.Builder(this)
            .setTitle("IP або URL сервера")
            .setView(input)
            .setPositiveButton("Зберегти") { _, _ ->
                val newUrl = input.text.toString().trim()
                if (newUrl.isNotEmpty()) {
                    saveServerUrl(newUrl)
                    retryLoading()
                }
            }
            .setNegativeButton("Скасувати", null)
            .show()
    }

    private fun setupWebView() {
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressBar.visibility = View.VISIBLE
                scheduleLoadTimeout()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                cancelLoadTimeout()
                progressBar.visibility = View.GONE
                isErrorShowing = false
                errorLayout.visibility = View.GONE
                webView.visibility = View.VISIBLE
                stopAutoRetry()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    showErrorUI(error?.description?.toString())
                }
            }

            @Suppress("DEPRECATION")
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                showErrorUI(description)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress >= 100) {
                    progressBar.visibility = View.GONE
                }
            }

            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                result?.confirm()
                return true
            }
        }

        // Native Download Listener
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            try {
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setMimeType(mimetype)
                    addRequestHeader("User-Agent", userAgent)
                    setDescription("Завантаження файлу...")
                    setTitle(URLUtil.guessFileName(url, contentDisposition, mimetype))
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, contentDisposition, mimetype))
                }
                val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)
                Toast.makeText(this, "Завантаження розпочато...", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
            }
        }
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                webView.evaluateJavascript("window.__handleAndroidBack ? window.__handleAndroidBack() : false") { value ->
                    if (value == "true") {
                        return@evaluateJavascript
                    }
                    if (webView.canGoBack()) {
                        webView.goBack()
                    } else {
                        moveTaskToBack(true)
                    }
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAutoRetry()
        cancelLoadTimeout()
    }
}
