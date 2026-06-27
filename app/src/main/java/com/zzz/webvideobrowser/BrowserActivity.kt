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
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.roundToInt

class BrowserActivity : AppCompatActivity() {

    private lateinit var viewModel: BrowserViewModel
    private lateinit var rootContainer: FrameLayout
    
    private lateinit var webViewContainer: FrameLayout
    private val tabs = mutableListOf<TabInfo>()
    private var activeTabIndex = -1
    
    data class TabInfo(
        val webView: WebView, 
        var title: String, 
        var url: String, 
        var themeColor: Int? = null,
        var hasVideo: Boolean = false,
        var isVideoPaused: Boolean = true,
        var activeVideoId: String? = null,
        var videoBindReason: String? = null,
        var sniffedMediaList: MutableList<String> = mutableListOf()
    )
    
    private fun getActiveWebView(): WebView? = if (activeTabIndex in tabs.indices) tabs[activeTabIndex].webView else null

    private lateinit var urlRouter: BrowserUrlRouter

    private lateinit var homeLayer: View
    private lateinit var bottomBar: View
    private lateinit var bottomBarContent: View
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

    private lateinit var floatingGestureLayer: View
    private lateinit var fullscreenGestureLayer: View

    private var currentDuration = 0.0
    private var currentPosition = 0.0
    private var isPaused = true
    private var currentRate = 1.0
    private var isUserSeeking = false

    // V2 记录
    private var activeVideoId: String? = null
    private var videoBindReason: String = ""
    private var lastVideoActivatedAt = 0L
    private var lastVideoProgressAt = 0L

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    private enum class OrientationMode {
        PORTRAIT, LANDSCAPE, SENSOR
    }
    private var orientationMode = OrientationMode.SENSOR

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[BrowserViewModel::class.java]
        val isDebuggable = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        WebView.setWebContentsDebuggingEnabled(isDebuggable)
        setContentView(R.layout.activity_browser)

        urlRouter = BrowserUrlRouter(this)

        bindViews()
        setupViewModel()
        
        createNewTab("about:blank")
        
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
            val mode = viewModel.uiMode.value
            val isFull = mode == BrowserViewModel.UiMode.FULLSCREEN_CUSTOM || mode == BrowserViewModel.UiMode.FULLSCREEN_PSEUDO
            progressBar.visibility = if (loading && !isFull) View.VISIBLE else View.GONE
        }
    }

    private fun bindViews() {
        rootContainer = findViewById(R.id.rootContainer)
        webViewContainer = findViewById(R.id.webViewContainer)
        homeLayer = findViewById(R.id.homeLayer)
        bottomBar = findViewById(R.id.bottomBar)
        bottomBarContent = findViewById(R.id.bottomBarContent)
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

        floatingGestureLayer = findViewById(R.id.floatingGestureLayer)
        fullscreenGestureLayer = findViewById(R.id.fullscreenGestureLayer)
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun createWebView(): WebView {
        val wv = WebView(this)
        wv.setBackgroundColor(Color.WHITE)

        wv.settings.apply {
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
            userAgentString = userAgentString + " WebVideoBrowser/2.0"
        }

        wv.addJavascriptInterface(VideoBridge(wv), "AndroidVideo")

        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return urlRouter.shouldOverride(view, request)
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return urlRouter.shouldOverride(view, url)
            }
            
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                sniffMediaRequest(view, request.url.toString(), request.requestHeaders)
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                if (getActiveWebView() == view) {
                    urlInput.setText(url)
                }
                val tab = tabs.find { it.webView == view }
                if (tab != null) {
                    tab.url = url
                    tab.title = view.title ?: url
                    tab.sniffedMediaList.clear()
                }
                view.evaluateJavascript(VideoJs.SCRIPT, null)
            }
        }

        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (getActiveWebView() == view) {
                    viewModel.updateProgress(newProgress)
                }
                super.onProgressChanged(view, newProgress)
            }

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (getActiveWebView() == wv) {
                    enterFullscreen(view, callback)
                }
            }

            override fun onHideCustomView() {
                if (getActiveWebView() == wv) {
                    requestExitFullscreen()
                }
            }
        }
        return wv
    }

    private fun sniffMediaRequest(wv: WebView, rawUrl: String, headers: Map<String, String>) {
        val lower = rawUrl.lowercase(Locale.US)
        val type = when {
            ".m3u8" in lower || "m3u8" in lower -> "hls"
            ".mpd" in lower -> "dash"
            ".mp4" in lower -> "mp4"
            ".webm" in lower -> "webm"
            ".m4s" in lower -> "fragment"
            ".ts" in lower -> "ts"
            else -> null
        } ?: return

        runOnUiThread {
            val tab = tabs.find { it.webView == wv } ?: return@runOnUiThread
            if (!tab.sniffedMediaList.contains(rawUrl)) {
                tab.sniffedMediaList.add(rawUrl)
            }
        }
    }

    private fun createNewTab(url: String) {
        val wv = createWebView()
        val tab = TabInfo(wv, "新窗口", url)
        tabs.add(tab)
        webViewContainer.addView(wv, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, 
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        switchTab(tabs.size - 1)
        if (url != "about:blank") openUrl(url)
    }

    private fun switchTab(index: Int) {
        if (index !in tabs.indices) return
        activeTabIndex = index
        tabs.forEachIndexed { i, tab ->
            tab.webView.visibility = if (i == index) View.VISIBLE else View.GONE
        }
        
        val tab = tabs[index]
        urlInput.setText(tab.webView.url ?: tab.url)
        
        activeVideoId = tab.activeVideoId
        videoBindReason = tab.videoBindReason ?: ""
        
        renderThemeColor(tab.themeColor ?: Color.parseColor("#F2F2F7"))

        when {
            tab.webView.url == null || tab.webView.url == "about:blank" -> {
                viewModel.setUiMode(BrowserViewModel.UiMode.HOME)
            }
            tab.hasVideo && !tab.isVideoPaused && tab.activeVideoId != null -> {
                viewModel.setUiMode(BrowserViewModel.UiMode.FLOATING)
            }
            else -> {
                viewModel.setUiMode(BrowserViewModel.UiMode.WEB)
            }
        }
    }

    private fun closeTab(index: Int) {
        if (index !in tabs.indices) return
        val tab = tabs.removeAt(index)
        webViewContainer.removeView(tab.webView)
        tab.webView.stopLoading()
        tab.webView.webChromeClient = null
        tab.webView.webViewClient = WebViewClient()
        tab.webView.removeAllViews()
        tab.webView.destroy()
        
        if (tabs.isEmpty()) {
            createNewTab("about:blank")
        } else {
            if (activeTabIndex >= tabs.size) {
                switchTab(tabs.size - 1)
            } else if (activeTabIndex == index) {
                switchTab(activeTabIndex)
            } else if (activeTabIndex > index) {
                activeTabIndex--
            }
        }
    }

    override fun onDestroy() {
        tabs.forEach { tab ->
            webViewContainer.removeView(tab.webView)
            tab.webView.stopLoading()
            tab.webView.webChromeClient = null
            tab.webView.webViewClient = WebViewClient()
            tab.webView.removeAllViews()
            tab.webView.destroy()
        }
        tabs.clear()
        super.onDestroy()
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
                val activeWv = getActiveWebView()
                if (kotlin.math.abs(dx) > 100 && kotlin.math.abs(dx) > kotlin.math.abs(e2.y - e1.y)) {
                    if (dx > 0 && activeWv?.canGoBack() == true) activeWv.goBack()
                    else if (dx < 0 && activeWv?.canGoForward() == true) activeWv.goForward()
                    return true
                }
                return false
            }
        })
        addressTouchArea.setOnTouchListener { _, event -> detector.onTouchEvent(event); false }
    }

    private fun requestVideoFullscreenWithFallback() {
        val wv = getActiveWebView() ?: return
        wv.evaluateJavascript("NativeVideo.requestFullscreen()") { _ ->
            rootContainer.postDelayed({
                val mode = viewModel.uiMode.value
                if (mode != BrowserViewModel.UiMode.FULLSCREEN_CUSTOM) {
                    wv.evaluateJavascript("NativeVideo.getState()") { state ->
                        if (state != "null") {
                            enterPseudoFullscreen()
                        } else {
                            Toast.makeText(this, "未绑定可全屏视频", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }, 600)
        }
    }

    private fun setupPlayerControls() {
        btnFloatPlayPause.setOnClickListener { 
            evalBool("NativeVideo.toggle()") { ok ->
                if (!ok) Toast.makeText(this, "未绑定可控视频", Toast.LENGTH_SHORT).show()
            }
        }
        btnFullPlayPause.setOnClickListener { 
            evalBool("NativeVideo.toggle()") { ok ->
                if (!ok) Toast.makeText(this, "未绑定可控视频", Toast.LENGTH_SHORT).show()
            }
        }
        btnFloatFull.setOnClickListener { requestVideoFullscreenWithFallback() }
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
                val target = currentDuration * s.progress / s.max
                evalBool("NativeVideo.seekTo($target)") { ok ->
                    if (!ok) Toast.makeText(this@BrowserActivity, "此视频暂不支持拖动", Toast.LENGTH_SHORT).show()
                }
                isUserSeeking = false
            }
        }
        seekFloat.setOnSeekBarChangeListener(seekListener)
        seekFull.setOnSeekBarChangeListener(seekListener)

        setupVideoGestureLayer(floatingGestureLayer)
        setupVideoGestureLayer(fullscreenGestureLayer)
    }

    private var lastNativeTapTime = 0L

    private fun handleDoubleTap() {
        val now = System.currentTimeMillis()
        if (now - lastNativeTapTime < 320) {
            evalBool("NativeVideo.toggle()") { ok ->
                if (!ok) Toast.makeText(this, "未绑定可控视频", Toast.LENGTH_SHORT).show()
            }
            lastNativeTapTime = 0L
        } else {
            lastNativeTapTime = now
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupVideoGestureLayer(layer: View) {
        var downX = 0f
        var downY = 0f
        var downTime = 0L
        var longPressTriggered = false
        var moved = false
        var oldRate = 1.0

        val handler = android.os.Handler(mainLooper)
        var longPressRunnable: Runnable? = null

        layer.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    downTime = System.currentTimeMillis()
                    moved = false
                    longPressTriggered = false

                    eval("NativeVideo.getState()") 

                    longPressRunnable = Runnable {
                        longPressTriggered = true
                        oldRate = currentRate
                        evalBool("NativeVideo.setRate(2.0)") { ok ->
                            if (ok) Toast.makeText(this, "2.0x", Toast.LENGTH_SHORT).show()
                        }
                    }
                    handler.postDelayed(longPressRunnable!!, 450)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - downX
                    val dy = event.y - downY

                    if (kotlin.math.abs(dx) > 16 || kotlin.math.abs(dy) > 16) {
                        moved = true
                    }

                    if (kotlin.math.abs(dx) > 40 && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.2f) {
                        longPressRunnable?.let { handler.removeCallbacks(it) }
                        val sec = (dx / 8).toInt()
                        Toast.makeText(this, if (sec >= 0) "+${sec}s" else "${sec}s", Toast.LENGTH_SHORT).show()
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    longPressRunnable?.let { handler.removeCallbacks(it) }

                    val dx = event.x - downX
                    val dy = event.y - downY
                    val dt = System.currentTimeMillis() - downTime

                    if (longPressTriggered) {
                        evalBool("NativeVideo.setRate($oldRate)") { ok ->
                            if (ok) Toast.makeText(this, "恢复倍速", Toast.LENGTH_SHORT).show()
                        }
                        return@setOnTouchListener true
                    }

                    if (kotlin.math.abs(dx) > 60 && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.2f) {
                        val sec = (dx / 8).toInt()
                        evalBool("NativeVideo.seekBy($sec)") { ok ->
                            if (!ok) Toast.makeText(this, "此视频暂不支持拖动", Toast.LENGTH_SHORT).show()
                        }
                        return@setOnTouchListener true
                    }

                    if (!moved && dt < 260) {
                        handleDoubleTap()
                        return@setOnTouchListener true
                    }

                    true
                }
                else -> true
            }
        }
    }

    private fun normalizeBarColor(color: Int): Int {
        val lum = ColorUtils.calculateLuminance(color)
        if (lum > 0.94) return Color.parseColor("#F2F2F7")
        if (lum < 0.08) return Color.parseColor("#111111")
        return color
    }

    private fun applyThemeColor(colorStr: String, forWebView: WebView) {
        val parsedColor = try {
            Color.parseColor(colorStr)
        } catch (e: Exception) {
            Color.parseColor("#F2F2F7")
        }
        val tab = tabs.find { it.webView == forWebView }
        if (tab != null) {
            tab.themeColor = parsedColor
        }
        if (getActiveWebView() == forWebView) {
            renderThemeColor(parsedColor)
        }
    }

    private fun renderThemeColor(rawColor: Int) {
        val safeBg = normalizeBarColor(rawColor)

        window.statusBarColor = safeBg
        window.navigationBarColor = safeBg
        bottomBar.setBackgroundColor(safeBg)
        bottomBarContent.setBackgroundColor(safeBg)

        val isLight = ColorUtils.calculateLuminance(safeBg) > 0.55

        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = isLight
            isAppearanceLightNavigationBars = isLight
        }

        val fg = if (isLight) Color.parseColor("#222222") else Color.WHITE
        val hint = if (isLight) Color.parseColor("#8E8E93") else Color.parseColor("#BDBDBD")
        val inputBg = if (isLight) {
            Color.parseColor("#E9E9EF")
        } else {
            ColorUtils.blendARGB(safeBg, Color.BLACK, 0.35f)
        }

        addressTouchArea.backgroundTintList = android.content.res.ColorStateList.valueOf(inputBg)

        urlInput.setTextColor(fg)
        urlInput.setHintTextColor(hint)

        btnGo.setColorFilter(fg)
        btnHome.setColorFilter(fg)
        btnTabs.setColorFilter(fg)
        btnTool.setColorFilter(fg)

        progressBar.progressTintList = android.content.res.ColorStateList.valueOf(
            if (isLight) Color.parseColor("#007AFF") else Color.WHITE
        )
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
        getActiveWebView()?.loadUrl(url)
        
        val tab = tabs.getOrNull(activeTabIndex)
        if (tab != null) {
            tab.url = url
            tab.title = "加载中..."
            tab.sniffedMediaList.clear()
            tab.activeVideoId = null
            tab.videoBindReason = null
        }
    }

    private fun forceVideoTakeover() {
        val wv = getActiveWebView() ?: return
        wv.evaluateJavascript("NativeVideo.forceActivate()") { result ->
            if (result == "true") {
                viewModel.setUiMode(BrowserViewModel.UiMode.FLOATING)
            } else {
                Toast.makeText(this, "未发现可直接接管的视频，可能在 iframe 跨域沙盒中", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("SetTextI18n")
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
            getActiveWebView()?.clearCache(true)
            Toast.makeText(this, "缓存已清", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.tool_clear_cookie).setOnClickListener {
            CookieManager.getInstance().removeAllCookies(null)
            Toast.makeText(this, "Cookie已清", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.tool_force_video)?.setOnClickListener {
            forceVideoTakeover()
            dialog.dismiss()
        }

        // V2 调试展示
        val txtDebug = view.findViewById<TextView>(R.id.txtDebugInfo)
        if (txtDebug != null) {
            val tab = tabs.getOrNull(activeTabIndex)
            val info = StringBuilder()
            info.append("当前绑定视频:\n")
            if (activeVideoId != null) {
                info.append("  ID: $activeVideoId\n")
                info.append("  Reason: $videoBindReason\n")
                info.append("  State: ${if (isPaused) "Paused" else "Playing"} (${formatTime(currentPosition)}/${formatTime(currentDuration)})\n")
            } else {
                info.append("  无绑定 (DOM 未接管)\n")
            }
            info.append("\n嗅探流 (诊断):\n")
            if (tab != null && tab.sniffedMediaList.isNotEmpty()) {
                tab.sniffedMediaList.forEach {
                    info.append("  - $it\n")
                }
            } else {
                info.append("  未嗅探到媒体流\n")
            }
            txtDebug.text = info.toString()
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
            createNewTab("about:blank")
            dialog.dismiss()
        }
        
        val container = view.findViewById<android.widget.LinearLayout>(R.id.tabListContainer)
        tabs.forEachIndexed { index, tab ->
            val tabView = layoutInflater.inflate(R.layout.item_tab, container, false)
            val txtTitle = tabView.findViewById<TextView>(R.id.txtTabTitle)
            val btnClose = tabView.findViewById<ImageButton>(R.id.btnCloseTab)
            
            txtTitle.text = if (index == activeTabIndex) "★ ${tab.title}" else tab.title
            
            tabView.setOnClickListener {
                switchTab(index)
                dialog.dismiss()
            }
            
            btnClose.setOnClickListener {
                closeTab(index)
                dialog.dismiss()
                showTabsMenu() // 重新刷新列表
            }
            container.addView(tabView)
        }

        dialog.show()
    }

    private fun applyUiMode(mode: BrowserViewModel.UiMode) {
        val isHome = mode == BrowserViewModel.UiMode.HOME
        val isWeb = mode == BrowserViewModel.UiMode.WEB
        val isFloating = mode == BrowserViewModel.UiMode.FLOATING
        val isCustomFull = mode == BrowserViewModel.UiMode.FULLSCREEN_CUSTOM
        val isPseudoFull = mode == BrowserViewModel.UiMode.FULLSCREEN_PSEUDO
        val isAnyFull = isCustomFull || isPseudoFull

        homeLayer.visibility = if (isHome) View.VISIBLE else View.GONE

        webViewContainer.visibility = when {
            isHome -> View.GONE
            isCustomFull -> View.GONE
            else -> View.VISIBLE
        }

        bottomBar.visibility = if (isHome || isWeb || isFloating) View.VISIBLE else View.GONE

        floatingControls.visibility = if (isFloating) View.VISIBLE else View.GONE
        floatingGestureLayer.visibility = if (isFloating) View.VISIBLE else View.GONE

        fullscreenContainer.visibility = if (isCustomFull) View.VISIBLE else View.GONE

        fullscreenGestureLayer.visibility = if (isAnyFull) View.VISIBLE else View.GONE
        fullscreenControls.visibility = if (isAnyFull) View.VISIBLE else View.GONE

        if (isAnyFull) {
            hideSystemBars()
        } else {
            showSystemBars()
        }

        bringVideoLayersToFront()
    }

    private fun bringVideoLayersToFront() {
        when (viewModel.uiMode.value) {
            BrowserViewModel.UiMode.FLOATING -> {
                floatingGestureLayer.bringToFront()
                floatingControls.bringToFront()
            }
            BrowserViewModel.UiMode.FULLSCREEN_CUSTOM -> {
                fullscreenContainer.bringToFront()
                fullscreenGestureLayer.bringToFront()
                fullscreenControls.bringToFront()
            }
            BrowserViewModel.UiMode.FULLSCREEN_PSEUDO -> {
                webViewContainer.bringToFront()
                fullscreenGestureLayer.bringToFront()
                fullscreenControls.bringToFront()
            }
            else -> Unit
        }
    }

    private fun enterFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (customView != null) {
            callback.onCustomViewHidden()
            return
        }
        customView = view
        customViewCallback = callback
        fullscreenContainer.removeAllViews()
        fullscreenContainer.addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        applyOrientationMode()
        viewModel.setUiMode(BrowserViewModel.UiMode.FULLSCREEN_CUSTOM)
    }

    private fun enterPseudoFullscreen() {
        applyOrientationMode()
        viewModel.setUiMode(BrowserViewModel.UiMode.FULLSCREEN_PSEUDO)
    }

    private fun leaveCustomFullscreen() {
        fullscreenContainer.removeAllViews()
        customView = null
        customViewCallback = null
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        viewModel.setUiMode(BrowserViewModel.UiMode.FLOATING)
    }

    private fun leavePseudoFullscreen() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        viewModel.setUiMode(BrowserViewModel.UiMode.FLOATING)
    }

    private fun requestExitFullscreen() {
        when (viewModel.uiMode.value) {
            BrowserViewModel.UiMode.FULLSCREEN_CUSTOM -> {
                customViewCallback?.onCustomViewHidden()
                leaveCustomFullscreen()
            }
            BrowserViewModel.UiMode.FULLSCREEN_PSEUDO -> {
                leavePseudoFullscreen()
            }
            else -> Unit
        }
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

    private fun eval(js: String) { getActiveWebView()?.evaluateJavascript(js, null) }
    
    private fun evalBool(js: String, onResult: (Boolean) -> Unit = {}) {
        getActiveWebView()?.evaluateJavascript(js) { result ->
            onResult(result == "true")
        }
    }

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
        if (viewModel.uiMode.value == BrowserViewModel.UiMode.FULLSCREEN_CUSTOM || 
            viewModel.uiMode.value == BrowserViewModel.UiMode.FULLSCREEN_PSEUDO) {
            requestExitFullscreen()
        }
        else if (getActiveWebView()?.canGoBack() == true) getActiveWebView()?.goBack()
        else if (viewModel.uiMode.value != BrowserViewModel.UiMode.HOME) viewModel.setUiMode(BrowserViewModel.UiMode.HOME)
        else super.onBackPressed()
    }

    inner class VideoBridge(private val wv: WebView) {
        
        @JavascriptInterface
        fun onVideoDetected(title: String, videoId: String, paused: Boolean, curr: Double, dur: Double) {
            runOnUiThread {
                val tab = tabs.find { it.webView == wv } ?: return@runOnUiThread
                tab.hasVideo = true
                tab.isVideoPaused = paused

                if (getActiveWebView() != wv) return@runOnUiThread

                if (this@BrowserActivity.activeVideoId == videoId) {
                    currentPosition = curr
                    currentDuration = dur
                    isPaused = paused
                    updateProgressUi()
                }
            }
        }

        @JavascriptInterface 
        fun onVideoActivated(title: String, videoId: String, reason: String) {
            runOnUiThread { 
                val tab = tabs.find { it.webView == wv } ?: return@runOnUiThread
                tab.hasVideo = true
                tab.isVideoPaused = false
                tab.activeVideoId = videoId
                tab.videoBindReason = reason
                
                if (getActiveWebView() != wv) return@runOnUiThread
                
                this@BrowserActivity.activeVideoId = videoId
                this@BrowserActivity.videoBindReason = reason
                this@BrowserActivity.lastVideoActivatedAt = System.currentTimeMillis()

                if (viewModel.uiMode.value != BrowserViewModel.UiMode.FULLSCREEN_CUSTOM &&
                    viewModel.uiMode.value != BrowserViewModel.UiMode.FULLSCREEN_PSEUDO) {
                    viewModel.setUiMode(BrowserViewModel.UiMode.FLOATING) 
                }
            }
        }
        
        @JavascriptInterface 
        fun onProgress(videoId: String, curr: Double, dur: Double, paused: Boolean, rate: Double) {
            runOnUiThread {
                if (getActiveWebView() != wv) return@runOnUiThread
                
                // V2: 防止其他广告视频的 timeupdate 污染主进度
                if (this@BrowserActivity.activeVideoId != null && this@BrowserActivity.activeVideoId != videoId) {
                    return@runOnUiThread
                }

                this@BrowserActivity.activeVideoId = videoId
                currentPosition = curr
                currentDuration = dur
                isPaused = paused
                currentRate = rate
                this@BrowserActivity.lastVideoProgressAt = System.currentTimeMillis()
                
                val tab = tabs.find { it.webView == wv }
                if (tab != null) {
                    tab.isVideoPaused = paused
                }
                
                viewModel.updateVideoState(curr, dur, paused)
                updateProgressUi()
            }
        }
        
        @JavascriptInterface 
        fun onVideoLost(videoId: String, reason: String) {
            runOnUiThread {
                if (this@BrowserActivity.activeVideoId != videoId) return@runOnUiThread

                this@BrowserActivity.activeVideoId = null
                
                if (viewModel.uiMode.value != BrowserViewModel.UiMode.FULLSCREEN_CUSTOM &&
                    viewModel.uiMode.value != BrowserViewModel.UiMode.FULLSCREEN_PSEUDO) {
                    viewModel.setUiMode(BrowserViewModel.UiMode.WEB)
                }
                Toast.makeText(this@BrowserActivity, "视频接管已失效: $reason", Toast.LENGTH_SHORT).show()
            }
        }
        
        @JavascriptInterface fun onHint(text: String) {
            runOnUiThread { 
                if (getActiveWebView() != wv) return@runOnUiThread
                Toast.makeText(this@BrowserActivity, text, Toast.LENGTH_SHORT).show() 
            }
        }
        
        @JavascriptInterface fun onThemeColor(color: String) {
            runOnUiThread {
                applyThemeColor(color, wv)
            }
        }
    }
}
