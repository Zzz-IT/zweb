package com.zzz.webvideobrowser

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.roundToInt

class BrowserActivity : AppCompatActivity() {

    private lateinit var viewModel: BrowserViewModel
    private lateinit var rootContainer: FrameLayout
    private lateinit var webView: WebView
    private lateinit var homeLayer: View
    private lateinit var bottomBar: View
    private lateinit var addressTouchArea: View
    private lateinit var progressBar: ProgressBar

    private lateinit var homeSearchInput: EditText
    private lateinit var btnHomeGo: ImageButton
    private lateinit var urlInput: EditText
    private lateinit var btnGo: ImageButton
    private lateinit var btnHome: ImageButton
    private lateinit var btnTabs: ImageButton
    private lateinit var btnTool: ImageButton

    private lateinit var floatingControls: View
    private lateinit var btnFloatPlayPause: ImageButton
    private lateinit var btnFloatFull: ImageButton
    private lateinit var txtFloatTime: TextView
    private lateinit var seekFloat: SeekBar

    private lateinit var fullscreenContainer: FrameLayout
    private lateinit var fullscreenControls: View
    private lateinit var btnExitFull: ImageButton
    private lateinit var btnFullPlayPause: ImageButton
    private lateinit var btnOrientation: ImageButton
    private lateinit var txtFullTime: TextView
    private lateinit var seekFull: SeekBar

    private var currentDuration = 0.0
    private var currentPosition = 0.0
    private var isPaused = true
    private var isUserSeeking = false

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    private enum class OrientationMode {
        PORTRAIT, LANDSCAPE, SENSOR
    }
    private var orientationMode = OrientationMode.SENSOR

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[BrowserViewModel::class.java]
        WebView.setWebContentsDebuggingEnabled(true)
        setContentView(R.layout.activity_browser)

        bindViews()
        setupViewModel()
        setupWebView()
        setupHome()
        setupBottomBar()
        setupPlayerControls()
    }

    private fun setupViewModel() {
        viewModel.uiMode.observe(this) { mode ->
            applyUiMode(mode)
        }
        viewModel.progress.observe(this) { p ->
            progressBar.progress = p
        }
        viewModel.isLoading.observe(this) { loading ->
            val isFull = viewModel.uiMode.value == BrowserViewModel.UiMode.FULLSCREEN
            progressBar.visibility = if (loading && !isFull) View.VISIBLE else View.GONE
        }
    }

    private fun bindViews() {
        rootContainer = findViewById(R.id.rootContainer)
        webView = findViewById(R.id.webView)
        homeLayer = findViewById(R.id.homeLayer)
        bottomBar = findViewById(R.id.bottomBar)
        addressTouchArea = findViewById(R.id.addressTouchArea)
        progressBar = findViewById(R.id.progressBar)

        homeSearchInput = findViewById(R.id.homeSearchInput)
        btnHomeGo = findViewById(R.id.btnHomeGo)
        urlInput = findViewById(R.id.urlInput)
        btnGo = findViewById(R.id.btnGo)
        btnHome = findViewById(R.id.btnHome)
        btnTabs = findViewById(R.id.btnTabs)
        btnTool = findViewById(R.id.btnTool)

        floatingControls = findViewById(R.id.floatingControls)
        btnFloatPlayPause = findViewById(R.id.btnFloatPlayPause)
        btnFloatFull = findViewById(R.id.btnFloatFull)
        txtFloatTime = findViewById(R.id.txtFloatTime)
        seekFloat = findViewById(R.id.seekFloat)

        fullscreenContainer = findViewById(R.id.fullscreenContainer)
        fullscreenControls = findViewById(R.id.fullscreenControls)
        btnExitFull = findViewById(R.id.btnExitFull)
        btnFullPlayPause = findViewById(R.id.btnFullPlayPause)
        btnOrientation = findViewById(R.id.btnOrientation)
        txtFullTime = findViewById(R.id.txtFullTime)
        seekFull = findViewById(R.id.seekFull)
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun setupWebView() {
        webView.setBackgroundColor(Color.WHITE)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = false
            displayZoomControls = false
            userAgentString = userAgentString + " WebVideoBrowser/1.0"
        }

        webView.addJavascriptInterface(VideoBridge(), "AndroidVideo")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                urlInput.setText(url)
                injectVideoScript()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                viewModel.updateProgress(newProgress)
                super.onProgressChanged(view, newProgress)
            }

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                enterFullscreen(view, callback)
            }

            override fun onHideCustomView() {
                leaveFullscreen()
            }
        }
    }

    private fun setupHome() {
        homeSearchInput.setOnEditorActionListener { _, _, _ ->
            openInput(homeSearchInput.text.toString())
            true
        }
        btnHomeGo.setOnClickListener {
            openInput(homeSearchInput.text.toString())
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupBottomBar() {
        btnGo.setOnClickListener { openInput(urlInput.text.toString()) }
        urlInput.setOnEditorActionListener { _, _, _ ->
            openInput(urlInput.text.toString())
            true
        }

        btnHome.setOnClickListener { viewModel.setUiMode(BrowserViewModel.UiMode.HOME) }
        btnTabs.setOnClickListener { showTabsMenu() }
        btnTool.setOnClickListener { showToolMenu() }

        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                if (kotlin.math.abs(dx) > 100 && kotlin.math.abs(dx) > kotlin.math.abs(e2.y - e1.y)) {
                    if (dx > 0 && webView.canGoBack()) webView.goBack()
                    else if (dx < 0 && webView.canGoForward()) webView.goForward()
                    return true
                }
                return false
            }
        })
        addressTouchArea.setOnTouchListener { _, event -> detector.onTouchEvent(event); false }
    }

    private fun setupPlayerControls() {
        btnFloatPlayPause.setOnClickListener { eval("NativeVideo.toggle()") }
        btnFullPlayPause.setOnClickListener { eval("NativeVideo.toggle()") }
        btnFloatFull.setOnClickListener { eval("NativeVideo.requestFullscreen()") }
        btnExitFull.setOnClickListener { requestExitFullscreen() }
        btnOrientation.setOnClickListener { cycleOrientationMode() }

        val seekListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser && currentDuration > 0) {
                    val target = currentDuration * p / s.max
                    val text = "${formatTime(target)} / ${formatTime(currentDuration)}"
                    txtFloatTime.text = text
                    txtFullTime.text = text
                }
            }
            override fun onStartTrackingTouch(s: SeekBar) { isUserSeeking = true }
            override fun onStopTrackingTouch(s: SeekBar) {
                eval("NativeVideo.seekTo(${currentDuration * s.progress / s.max})")
                isUserSeeking = false
            }
        }
        seekFloat.setOnSeekBarChangeListener(seekListener)
        seekFull.setOnSeekBarChangeListener(seekListener)
    }

    private fun openInput(input: String) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return
        val url = if (trimmed.startsWith("http")) trimmed
        else if (trimmed.contains(".") && !trimmed.contains(" ")) "https://$trimmed"
        else "https://www.google.com/search?q=${URLEncoder.encode(trimmed, "UTF-8")}"
        openUrl(url)
    }

    private fun openUrl(url: String) {
        viewModel.setUiMode(BrowserViewModel.UiMode.WEB)
        urlInput.setText(url)
        webView.loadUrl(url)
    }

    private fun showToolMenu() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.preview_toolbox, null)
        dialog.setContentView(view)

        val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.setBackgroundResource(android.R.color.transparent)
        
        view.findViewById<View>(R.id.tool_orientation).setOnClickListener {
            cycleOrientationMode()
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.tool_clear_cache).setOnClickListener {
            webView.clearCache(true)
            Toast.makeText(this, "缓存已清", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.tool_clear_cookie).setOnClickListener {
            CookieManager.getInstance().removeAllCookies(null)
            Toast.makeText(this, "Cookie已清", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showTabsMenu() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.preview_tabs, null)
        dialog.setContentView(view)

        val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.setBackgroundResource(android.R.color.transparent)

        view.findViewById<View>(R.id.btnAddTab).setOnClickListener {
            Toast.makeText(this, "新建窗口 (模拟)", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.btnCloseTab).setOnClickListener {
            Toast.makeText(this, "关闭窗口 (模拟)", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun applyUiMode(mode: BrowserViewModel.UiMode) {
        homeLayer.visibility = if (mode == BrowserViewModel.UiMode.HOME) View.VISIBLE else View.GONE
        webView.visibility = if (mode == BrowserViewModel.UiMode.HOME || mode == BrowserViewModel.UiMode.FULLSCREEN) View.GONE else View.VISIBLE
        bottomBar.visibility = if (mode == BrowserViewModel.UiMode.HOME || mode == BrowserViewModel.UiMode.WEB || mode == BrowserViewModel.UiMode.FLOATING) View.VISIBLE else View.GONE
        floatingControls.visibility = if (mode == BrowserViewModel.UiMode.FLOATING) View.VISIBLE else View.GONE
        fullscreenContainer.visibility = if (mode == BrowserViewModel.UiMode.FULLSCREEN) View.VISIBLE else View.GONE
        fullscreenControls.visibility = if (mode == BrowserViewModel.UiMode.FULLSCREEN) View.VISIBLE else View.GONE
        
        if (mode == BrowserViewModel.UiMode.FULLSCREEN) hideSystemBars() else showSystemBars()
    }

    private fun enterFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
        customView = view
        customViewCallback = callback
        fullscreenContainer.addView(view, FrameLayout.LayoutParams(-1, -1))
        applyOrientationMode()
        viewModel.setUiMode(BrowserViewModel.UiMode.FULLSCREEN)
    }

    private fun leaveFullscreen() {
        fullscreenContainer.removeAllViews()
        customView = null
        customViewCallback = null
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        viewModel.setUiMode(BrowserViewModel.UiMode.FLOATING)
    }

    private fun requestExitFullscreen() {
        customViewCallback?.onCustomViewHidden()
        leaveFullscreen()
    }

    private fun cycleOrientationMode() {
        orientationMode = when (orientationMode) {
            OrientationMode.SENSOR -> OrientationMode.PORTRAIT
            OrientationMode.PORTRAIT -> OrientationMode.LANDSCAPE
            else -> OrientationMode.SENSOR
        }
        applyOrientationMode()
    }

    private fun applyOrientationMode() {
        requestedOrientation = when (orientationMode) {
            OrientationMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            OrientationMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }
    }

    private fun updateProgressUi() {
        val progress = if (currentDuration > 0) (currentPosition / currentDuration * 1000).toInt() else 0
        if (!isUserSeeking) {
            seekFloat.progress = progress
            seekFull.progress = progress
        }
        val text = "${formatTime(currentPosition)} / ${formatTime(currentDuration)}"
        txtFloatTime.text = text
        txtFullTime.text = text
        val playIcon = if (isPaused) R.drawable.ic_play else R.drawable.ic_pause
        btnFloatPlayPause.setImageResource(playIcon)
        btnFullPlayPause.setImageResource(playIcon)
    }

    private fun formatTime(s: Double): String {
        val t = s.toInt()
        val sec = if (t < 0) 0 else t
        return String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60)
    }

    private fun injectVideoScript() { webView.evaluateJavascript(VideoJs.SCRIPT, null) }
    private fun eval(js: String) { webView.evaluateJavascript(js, null) }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.apply {
                hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    private fun showSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
        }
    }

    override fun onBackPressed() {
        if (viewModel.uiMode.value == BrowserViewModel.UiMode.FULLSCREEN) requestExitFullscreen()
        else if (webView.canGoBack()) webView.goBack()
        else if (viewModel.uiMode.value != BrowserViewModel.UiMode.HOME) viewModel.setUiMode(BrowserViewModel.UiMode.HOME)
        else super.onBackPressed()
    }

    inner class VideoBridge {
        @JavascriptInterface fun onVideoActivated(title: String) {
            runOnUiThread { if (viewModel.uiMode.value != BrowserViewModel.UiMode.FULLSCREEN) viewModel.setUiMode(BrowserViewModel.UiMode.FLOATING) }
        }
        @JavascriptInterface fun onProgress(curr: Double, dur: Double, paused: Boolean, rate: Double) {
            runOnUiThread {
                currentPosition = curr; currentDuration = dur; isPaused = paused
                viewModel.updateVideoState(curr, dur, paused)
                updateProgressUi()
            }
        }
        @JavascriptInterface fun onHint(text: String) {
            runOnUiThread { Toast.makeText(this@BrowserActivity, text, Toast.LENGTH_SHORT).show() }
        }
    }
}
