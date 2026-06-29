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
    private lateinit var addressContainerFrame: View
    private lateinit var bottomBarGestureLayer: View
    private lateinit var addressTouchArea: View
    private lateinit var bottomGestureHint: TextView
    private lateinit var progressBar: ProgressBar

    private lateinit var homeSearchInput: EditText
    private lateinit var btnHomeGo: ImageButton
    private lateinit var urlInput: EditText
    private lateinit var btnGo: ImageButton
    private lateinit var btnHome: ImageButton
    private lateinit var btnTabs: ImageButton
    private lateinit var btnTool: ImageButton

    private lateinit var btnResourceHub: ImageButton
    private val resourceRegistry = ResourceRegistry()
    private lateinit var browserSettings: BrowserSettings
    private lateinit var adBlockEngine: AdBlockEngine

    private lateinit var fullscreenContainer: FrameLayout
    private lateinit var fullscreenControls: View
    private lateinit var btnExitFull: ImageButton
    private lateinit var btnFullPlayPause: ImageButton
    private lateinit var btnOrientation: ImageButton
    private lateinit var txtFullTime: TextView
    private lateinit var seekFull: MiuiVideoSeekBar
    private lateinit var txtSeekOverlay: TextView

    private lateinit var fullscreenGestureLayer: View

    private var currentDuration = 0.0
    private var currentPosition = 0.0
    private var isPaused = true
    private var currentRate = 1.0
    private var isUserSeeking = false

    // V2 记录
    private enum class PlayerSourceMode {
        NONE, DOM_THEATER, IFRAME_TOP_PAGE, STREAM_NATIVE
    }
    private var playerSourceMode = PlayerSourceMode.NONE
    private var modeBeforeFullscreen: BrowserViewModel.UiMode = BrowserViewModel.UiMode.WEB
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
        browserSettings = BrowserSettings(this)
        adBlockEngine = AdBlockEngine(this)
        viewModel = ViewModelProvider(this)[BrowserViewModel::class.java]
        val isDebuggable = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        WebView.setWebContentsDebuggingEnabled(isDebuggable)
        setContentView(R.layout.activity_browser)

        urlRouter = BrowserUrlRouter(this)

        bindViews()
        setupViewModel()
        
        createNewTab("about:blank")
        
        setupHome()
        setupBottomBarGesture()
        setupPlayerControls()
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
        addressContainerFrame = findViewById(R.id.addressContainerFrame)
        bottomBarGestureLayer = findViewById(R.id.bottomBarGestureLayer)
        addressTouchArea = findViewById(R.id.addressTouchArea)
        bottomGestureHint = findViewById(R.id.bottomGestureHint)
        progressBar = findViewById(R.id.progressBar)

        homeSearchInput = findViewById(R.id.homeSearchInput)
        btnHomeGo = findViewById(R.id.btnHomeGo)
        urlInput = findViewById(R.id.urlInput)
        btnGo = findViewById(R.id.btnGo)
        btnHome = findViewById(R.id.btnHome)
        btnTabs = findViewById(R.id.btnTabs)
        btnTool = findViewById(R.id.btnTool)

        btnResourceHub = findViewById(R.id.btnResourceHub)
        btnResourceHub.setOnClickListener { showVideoCandidateSheet() }

        fullscreenContainer = findViewById(R.id.fullscreenContainer)
        fullscreenControls = findViewById(R.id.fullscreenControls)
        btnExitFull = findViewById(R.id.btnExitFull)
        btnFullPlayPause = findViewById(R.id.btnFullPlayPause)
        btnOrientation = findViewById(R.id.btnOrientation)
        txtFullTime = findViewById(R.id.txtFullTime)
        seekFull = findViewById(R.id.seekFull)
        txtSeekOverlay = findViewById(R.id.txtSeekOverlay)

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
            
            if (browserSettings.enableDesktopMode) {
                userAgentString = userAgentString.replace("Mobile", "").replace("Android", "Windows NT 10.0")
            }
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
                if (browserSettings.enableAdBlock) {
                    val url = request.url.toString()
                    if (adBlockEngine.shouldBlock(url)) {
                        return WebResourceResponse("text/plain", "UTF-8", java.io.ByteArrayInputStream(ByteArray(0)))
                    }
                    
                    // Fallback to simple rules
                    val lowerUrl = url.lowercase(java.util.Locale.US)
                    if (lowerUrl.contains("googleads") || lowerUrl.contains("doubleclick.net") || lowerUrl.contains("adsystem") || lowerUrl.contains("/ad/") || lowerUrl.contains("ad.doubleclick")) {
                        return WebResourceResponse("text/plain", "UTF-8", java.io.ByteArrayInputStream(ByteArray(0)))
                    }
                }
                
                sniffMediaRequest(view, request.url.toString(), request.requestHeaders)
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                if (getActiveWebView() == view) {
                    if (viewModel.uiMode.value == BrowserViewModel.UiMode.HOME) {
                        urlInput.setText("")
                    } else {
                        urlInput.setText(url)
                    }
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

    private fun classifyMediaUrl(rawUrl: String): CandidateType? {
        val lower = rawUrl.lowercase(Locale.US)
        return when {
            ".m3u8" in lower || "m3u8" in lower -> {
                if ("/qc/" in lower || "/720/" in lower || "/1080/" in lower || "/480/" in lower) {
                    CandidateType.HLS_MEDIA
                } else {
                    CandidateType.HLS_MASTER
                }
            }
            ".mpd" in lower -> CandidateType.DASH
            ".mp4" in lower -> CandidateType.MP4
            ".webm" in lower -> CandidateType.WEBM
            ".m4s" in lower || ".ts" in lower -> CandidateType.FRAGMENT
            else -> null
        }
    }

    private fun titleForCandidateType(type: CandidateType, host: String?): String {
        return when (type) {
            CandidateType.HLS_MASTER -> "HLS 视频流"
            CandidateType.HLS_MEDIA -> "HLS 子清晰度"
            CandidateType.MP4 -> "MP4 视频"
            CandidateType.WEBM -> "WebM 视频"
            CandidateType.DASH -> "DASH 视频流"
            CandidateType.FRAGMENT -> "媒体分片"
            else -> "网络媒体"
        } + if (host != null) " ($host)" else ""
    }

    private fun sniffMediaRequest(wv: WebView, rawUrl: String, headers: Map<String, String>) {
        val type = classifyMediaUrl(rawUrl) ?: return

        runOnUiThread {
            val tab = tabs.find { it.webView == wv } ?: return@runOnUiThread
            val entry = "${type.name}: $rawUrl"
            if (!tab.sniffedMediaList.contains(entry)) {
                tab.sniffedMediaList.add(entry)
            }
            
            val uri = runCatching { android.net.Uri.parse(rawUrl) }.getOrNull()
            val host = uri?.host

            val score = when (type) {
                CandidateType.HLS_MASTER -> 700
                CandidateType.HLS_MEDIA -> 650
                CandidateType.MP4 -> 620
                CandidateType.WEBM -> 610
                CandidateType.DASH -> 600
                CandidateType.FRAGMENT -> 100
                else -> 0
            }

            resourceRegistry.upsert(
                VideoCandidate(
                    id = "net:$rawUrl",
                    type = type,
                    title = titleForCandidateType(type, host),
                    url = rawUrl,
                    pageUrl = wv.url,
                    frameSrc = null,
                    videoId = null,
                    host = host,
                    headers = headers,
                    score = score,
                    confidence = if (type == CandidateType.FRAGMENT) 30 else 70,
                    reason = "network-sniff"
                )
            )

            updateResourceFabVisibility()
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
        val displayUrl = tab.webView.url ?: tab.url
        if (displayUrl.isBlank() || displayUrl == "about:blank" || viewModel.uiMode.value == BrowserViewModel.UiMode.HOME) {
            urlInput.setText("")
        } else {
            urlInput.setText(displayUrl)
        }
        
        activeVideoId = tab.activeVideoId
        videoBindReason = tab.videoBindReason ?: ""
        
        renderThemeColor(tab.themeColor ?: Color.parseColor("#F2F2F7"))

        if (tab.webView.url == null || tab.webView.url == "about:blank") {
            viewModel.setUiMode(BrowserViewModel.UiMode.HOME)
        } else {
            viewModel.setUiMode(BrowserViewModel.UiMode.WEB)
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

    private var barDownX = 0f
    private var barDownY = 0f
    private var barDownTime = 0L
    private var barDragging = false
    private var barGestureType = BarGestureType.NONE

    private enum class BarGestureType {
        NONE, HORIZONTAL_NAV, PULL_REFRESH
    }

    private fun rubberBand(value: Float, limit: Float): Float {
        val sign = if (value >= 0f) 1f else -1f
        val abs = kotlin.math.abs(value)
        return sign * (limit * abs / (abs + limit))
    }

    private fun focusUrlInput() {
        urlInput.requestFocus()
        urlInput.setSelection(urlInput.text?.length ?: 0)
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(urlInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }

    private fun updateBottomBarSwipeHint(dx: Float, canBack: Boolean, canForward: Boolean) {
        bottomGestureHint.visibility = View.VISIBLE
        bottomGestureHint.text = when {
            dx > 0 && canBack -> "松手后退"
            dx > 0 && !canBack -> "没有上一页"
            dx < 0 && canForward -> "松手前进"
            dx < 0 && !canForward -> "没有下一页"
            else -> ""
        }
    }

    private fun updateBottomBarRefreshHint(pull: Float) {
        bottomGestureHint.visibility = View.VISIBLE
        bottomGestureHint.text = if (pull > 56f) "松手刷新" else "上滑刷新"
    }

    private fun hideBottomBarSwipeHint() {
        bottomGestureHint.visibility = View.GONE
    }

    private fun bounceBottomBar(fromX: Float, fromY: Float) {
        bottomBar.translationX = fromX
        bottomBar.translationY = fromY

        bottomBar.animate()
            .translationX(0f)
            .translationY(0f)
            .setDuration(240)
            .setInterpolator(android.view.animation.OvershootInterpolator(1.45f))
            .withEndAction {
                barDragging = false
                barGestureType = BarGestureType.NONE
                hideBottomBarSwipeHint()
            }
            .start()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupBottomBarGesture() {
        btnGo.setOnClickListener { openInput(urlInput.text.toString()) }
        urlInput.setOnEditorActionListener { _, _, _ ->
            openInput(urlInput.text.toString())
            true
        }

        btnHome.setOnClickListener { viewModel.setUiMode(BrowserViewModel.UiMode.HOME) }
        btnTabs.setOnClickListener { showTabsMenu() }
        btnTabs.setOnLongClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("清除窗口")
                .setMessage("确定要关闭所有窗口吗？")
                .setPositiveButton("确定") { _, _ ->
                    val size = tabs.size
                    for (i in size - 1 downTo 0) {
                        closeTab(i)
                    }
                    if (tabs.isEmpty()) {
                        createNewTab("about:blank")
                    }
                }
                .setNegativeButton("取消", null)
                .show()
            true
        }
        btnTool.setOnClickListener { showToolMenu() }

        bottomBarGestureLayer.setOnTouchListener { _, event ->
            val wv = getActiveWebView()

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    barDownX = event.rawX
                    barDownY = event.rawY
                    barDownTime = System.currentTimeMillis()
                    barDragging = false
                    barGestureType = BarGestureType.NONE

                    bottomBar.animate().cancel()
                    bottomBar.translationX = 0f
                    bottomBar.translationY = 0f

                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - barDownX
                    val dy = event.rawY - barDownY
                    val absDx = kotlin.math.abs(dx)
                    val absDy = kotlin.math.abs(dy)

                    if (!barDragging) {
                        when {
                            absDx > 18f && absDx > absDy * 1.25f -> {
                                barDragging = true
                                barGestureType = BarGestureType.HORIZONTAL_NAV
                                hideKeyboardAndClearFocus()
                            }

                            -dy > 18f && absDy > absDx * 1.25f -> {
                                barDragging = true
                                barGestureType = BarGestureType.PULL_REFRESH
                                hideKeyboardAndClearFocus()
                            }
                        }
                    }

                    if (!barDragging) {
                        return@setOnTouchListener true
                    }

                    when (barGestureType) {
                        BarGestureType.HORIZONTAL_NAV -> {
                            val drag = rubberBand(dx, 140f)
                            bottomBar.translationX = drag
                            bottomBar.translationY = 0f
                            updateBottomBarSwipeHint(drag, wv?.canGoBack() == true, wv?.canGoForward() == true)
                        }

                        BarGestureType.PULL_REFRESH -> {
                            val pull = rubberBand(-dy, 96f).coerceAtLeast(0f)
                            bottomBar.translationY = -pull
                            bottomBar.translationX = 0f
                            updateBottomBarRefreshHint(pull)
                        }

                        else -> Unit
                    }

                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val dx = event.rawX - barDownX
                    val dy = event.rawY - barDownY
                    val dt = System.currentTimeMillis() - barDownTime

                    if (!barDragging) {
                        bounceBottomBar(0f, 0f)

                        if (dt < 220 && kotlin.math.abs(dx) < 12f && kotlin.math.abs(dy) < 12f) {
                            focusUrlInput()
                        }

                        return@setOnTouchListener true
                    }

                    when (barGestureType) {
                        BarGestureType.HORIZONTAL_NAV -> {
                            when {
                                dx > 96f && wv?.canGoBack() == true -> {
                                    wv.goBack()
                                    bounceBottomBar(72f, 0f)
                                }

                                dx < -96f && wv?.canGoForward() == true -> {
                                    wv.goForward()
                                    bounceBottomBar(-72f, 0f)
                                }

                                else -> {
                                    bounceBottomBar(bottomBar.translationX, bottomBar.translationY)
                                }
                            }
                        }

                        BarGestureType.PULL_REFRESH -> {
                            if (-dy > 72f) {
                                wv?.reload()
                                bounceBottomBar(0f, -56f)
                            } else {
                                bounceBottomBar(bottomBar.translationX, bottomBar.translationY)
                            }
                        }

                        else -> {
                            bounceBottomBar(bottomBar.translationX, bottomBar.translationY)
                        }
                    }

                    true
                }

                else -> true
            }
        }
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
        btnFullPlayPause.setOnClickListener { 
            evalBool("NativeVideo.toggle()") { ok ->
                if (!ok) Toast.makeText(this, "未绑定可控视频", Toast.LENGTH_SHORT).show()
            }
        }
        btnExitFull.setOnClickListener { requestExitFullscreen() }
        btnOrientation.setOnClickListener { cycleOrientationMode() }

        val seekListener = object : MiuiVideoSeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: MiuiVideoSeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser && currentDuration > 0) {
                    val target = currentDuration * progress / seekBar.max
                    val text = "${formatTime(target)} / ${formatTime(currentDuration)}"
                    txtFullTime.text = text
                }
            }
            override fun onStartTrackingTouch(seekBar: MiuiVideoSeekBar) { 
                isUserSeeking = true
            }
            override fun onStopTrackingTouch(seekBar: MiuiVideoSeekBar) {
                val target = currentDuration * seekBar.progress / seekBar.max
                eval("NativeVideo.seekTo($target)")
                isUserSeeking = false
            }
        }
        seekFull.onSeekBarChangeListener = seekListener

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

        var isHorizontalSeeking = false
        var pendingSeekSeconds = 0

        layer.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    downTime = System.currentTimeMillis()
                    moved = false
                    longPressTriggered = false
                    isHorizontalSeeking = false

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
                        isHorizontalSeeking = true
                        pendingSeekSeconds = (dx / 8).toInt()
                        
                        txtSeekOverlay.visibility = View.VISIBLE
                        txtSeekOverlay.text = if (pendingSeekSeconds >= 0) "+${pendingSeekSeconds}s" else "${pendingSeekSeconds}s"
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

                    if (isHorizontalSeeking) {
                        txtSeekOverlay.visibility = View.GONE
                        eval("NativeVideo.seekBy($pendingSeekSeconds)")
                        isHorizontalSeeking = false
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

    private fun hideKeyboardAndClearFocus() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        currentFocus?.let {
            imm.hideSoftInputFromWindow(it.windowToken, 0)
            it.clearFocus()
        }
        urlInput.clearFocus()
        homeSearchInput.clearFocus()
    }

    private fun openInput(input: String) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return
        hideKeyboardAndClearFocus()
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
                enterPseudoFullscreen()
            } else {
                Toast.makeText(this, "未发现可直接接管的视频，可能在 iframe 跨域沙盒中", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun diagnoseVideoTakeover() {
        val wv = getActiveWebView() ?: return
        wv.evaluateJavascript("JSON.stringify(NativeVideo.diagnose())") { raw ->
            val text = raw
                ?.removePrefix("\"")
                ?.removeSuffix("\"")
                ?.replace("\\\"", "\"")
                ?.replace("\\\\", "\\")
                ?: "无诊断结果"
            showDiagnosisDialog(text)
        }
    }

    private fun showDiagnosisDialog(text: String) {
        BottomSheetDialog(this).apply {
            val tv = TextView(this@BrowserActivity).apply {
                setTextIsSelectable(true)
                this.text = text
                setPadding(32, 32, 32, 32)
                textSize = 12f
            }
            setContentView(tv)
            show()
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
        view.findViewById<View>(R.id.tool_video_diagnose)?.setOnClickListener {
            diagnoseVideoTakeover()
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.tool_settings)?.setOnClickListener {
            startActivity(android.content.Intent(this, SettingsActivity::class.java))
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

        val recyclerView = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.tabRecyclerView)
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        recyclerView.itemAnimator = androidx.recyclerview.widget.DefaultItemAnimator()

        val adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder {
                val itemView = layoutInflater.inflate(R.layout.item_tab, parent, false)
                return object : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {}
            }

            override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
                val tab = tabs[position]
                val txtTitle = holder.itemView.findViewById<TextView>(R.id.txtTabTitle)
                val btnClose = holder.itemView.findViewById<ImageButton>(R.id.btnCloseTab)

                txtTitle.text = if (position == activeTabIndex) "★ ${tab.title}" else tab.title

                holder.itemView.setOnClickListener {
                    switchTab(holder.bindingAdapterPosition)
                    dialog.dismiss()
                }

                btnClose.setOnClickListener {
                    val pos = holder.bindingAdapterPosition
                    if (pos != androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
                        closeTab(pos)
                        notifyItemRemoved(pos)
                        notifyItemRangeChanged(pos, tabs.size)
                    }
                }
            }

            override fun getItemCount() = tabs.size
        }
        recyclerView.adapter = adapter

        view.findViewById<View>(R.id.btnAddTab).setOnClickListener {
            createNewTab("about:blank")
            adapter.notifyItemInserted(tabs.size - 1)
            recyclerView.scrollToPosition(tabs.size - 1)
            
            // 延迟一点时间再收起弹窗，让用户看到添加的动画
            view.postDelayed({
                dialog.dismiss()
            }, 300)
        }

        dialog.show()
    }

    private fun applyUiMode(mode: BrowserViewModel.UiMode) {
        val isHome = mode == BrowserViewModel.UiMode.HOME
        val isWeb = mode == BrowserViewModel.UiMode.WEB
        val isPanel = mode == BrowserViewModel.UiMode.RESOURCE_PANEL
        val isCustomFull = mode == BrowserViewModel.UiMode.FULLSCREEN_CUSTOM
        val isPseudoFull = mode == BrowserViewModel.UiMode.FULLSCREEN_PSEUDO
        val isAnyFull = isCustomFull || isPseudoFull

        val transition = android.transition.AutoTransition().apply {
            duration = 300
            interpolator = android.view.animation.DecelerateInterpolator()
        }
        android.transition.TransitionManager.beginDelayedTransition(rootContainer, transition)

        homeLayer.visibility = if (isHome) View.VISIBLE else View.GONE

        webViewContainer.visibility = when {
            isHome -> View.GONE
            isCustomFull -> View.GONE
            else -> View.VISIBLE
        }

        bottomBar.visibility = if (isHome || isWeb || isPanel) View.VISIBLE else View.GONE

        updateResourceFabVisibility()

        fullscreenContainer.visibility = if (isCustomFull) View.VISIBLE else View.GONE

        fullscreenGestureLayer.visibility = if (isAnyFull) View.VISIBLE else View.GONE
        fullscreenControls.visibility = if (isAnyFull) View.VISIBLE else View.GONE

        if (isAnyFull) {
            hideSystemBars()
            bringVideoLayersToFront()
        } else {
            showSystemBars()
            bringBrowserLayersToFront()
        }
    }

    private fun bringBrowserLayersToFront() {
        bottomBar.bringToFront()
        btnResourceHub.bringToFront()
        progressBar.bringToFront()
    }

    private fun bringVideoLayersToFront() {
        when (viewModel.uiMode.value) {
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
        modeBeforeFullscreen = viewModel.uiMode.value ?: BrowserViewModel.UiMode.WEB
        customView = view
        customViewCallback = callback
        fullscreenContainer.removeAllViews()
        fullscreenContainer.addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        applyOrientationMode()
        viewModel.setUiMode(BrowserViewModel.UiMode.FULLSCREEN_CUSTOM)
    }

    private fun enterPseudoFullscreen() {
        playerSourceMode = PlayerSourceMode.DOM_THEATER
        modeBeforeFullscreen = resolveReturnModeAfterPlayer()
        applyOrientationMode()
        viewModel.setUiMode(BrowserViewModel.UiMode.FULLSCREEN_PSEUDO)
    }

    private fun resolveReturnModeAfterPlayer(): BrowserViewModel.UiMode {
        val wv = getActiveWebView()
        val url = wv?.url

        return if (url.isNullOrBlank() || url == "about:blank") {
            BrowserViewModel.UiMode.HOME
        } else {
            BrowserViewModel.UiMode.WEB
        }
    }

    private fun leaveCustomFullscreen() {
        fullscreenContainer.removeAllViews()
        customView = null
        customViewCallback = null
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        viewModel.setUiMode(resolveReturnModeAfterPlayer())
        bringBrowserLayersToFront()
        updateResourceFabVisibility()
    }

    private fun leavePseudoFullscreen() {
        getActiveWebView()?.evaluateJavascript("NativeVideo.exitTheater && NativeVideo.exitTheater()", null)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activeVideoId = null
        videoBindReason = ""
        playerSourceMode = PlayerSourceMode.NONE
        viewModel.setUiMode(resolveReturnModeAfterPlayer())
        bringBrowserLayersToFront()
        updateResourceFabVisibility()
    }

    private fun updateResourceFabVisibility() {
        val mode = viewModel.uiMode.value
        val isFull = mode == BrowserViewModel.UiMode.FULLSCREEN_CUSTOM || mode == BrowserViewModel.UiMode.FULLSCREEN_PSEUDO
        val shouldShow = !isFull && resourceRegistry.hasCandidates()
        val isCurrentlyVisible = btnResourceHub.visibility == View.VISIBLE

        if (shouldShow != isCurrentlyVisible) {
            val transition = android.transition.AutoTransition().apply {
                duration = 300
                interpolator = android.view.animation.DecelerateInterpolator()
            }
            android.transition.TransitionManager.beginDelayedTransition(rootContainer, transition)
            btnResourceHub.visibility = if (shouldShow) View.VISIBLE else View.GONE
        }
    }

    @SuppressLint("SetTextI18n")
    private fun showVideoCandidateSheet() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_video_candidates, null)
        dialog.setContentView(view)

        val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.setBackgroundResource(android.R.color.transparent)

        val container = view.findViewById<android.widget.LinearLayout>(R.id.candidateListContainer)
        val candidates = resourceRegistry.listSorted()

        if (candidates.isEmpty()) {
            val emptyTxt = TextView(this).apply {
                text = "未发现任何可接管资源"
                setPadding(32, 64, 32, 64)
                gravity = android.view.Gravity.CENTER
            }
            container.addView(emptyTxt)
        } else {
            candidates.forEach { candidate ->
                val item = layoutInflater.inflate(R.layout.item_video_candidate, container, false)
                val title = item.findViewById<TextView>(R.id.txtCandidateTitle)
                val subtitle = item.findViewById<TextView>(R.id.txtCandidateSubtitle)
                val tag = item.findViewById<TextView>(R.id.txtCandidateTag)

                title.text = candidate.title
                subtitle.text = "${candidate.host ?: "unknown"} | ${candidate.reason}"
                tag.text = candidate.type.name

                item.setOnClickListener {
                    handleCandidateClick(candidate)
                    dialog.dismiss()
                }
                container.addView(item)
            }
        }

        dialog.show()
    }

    private fun handleCandidateClick(candidate: VideoCandidate) {
        when (candidate.type) {
            CandidateType.DOM_VIDEO -> {
                enterFullscreenForDomVideo(candidate)
            }
            CandidateType.IFRAME_PLAYER -> {
                openIframeCandidate(candidate)
            }
            CandidateType.HLS_MASTER,
            CandidateType.HLS_MEDIA,
            CandidateType.MP4,
            CandidateType.WEBM,
            CandidateType.DASH -> {
                openStreamFallback(candidate)
            }
            CandidateType.BLOB_HINT -> {
                Toast.makeText(this, "blob 属于页面内部播放对象，请优先进入 iframe 或 DOM 接管", Toast.LENGTH_LONG).show()
            }
            CandidateType.FRAGMENT -> {
                Toast.makeText(this, "这是媒体分片，不建议直接播放", Toast.LENGTH_SHORT).show()
            }
            CandidateType.CUSTOM_VIEW -> {
                Toast.makeText(this, "请使用网页播放器全屏按钮触发原生全屏", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun enterFullscreenForDomVideo(candidate: VideoCandidate) {
        val videoId = candidate.videoId ?: return
        tryEnterDomVideoById(videoId) { ok ->
            if (!ok) {
                refreshDomVideoCandidates {
                    val replacement = resourceRegistry.listSorted()
                        .firstOrNull { it.type == CandidateType.DOM_VIDEO }

                    if (replacement?.videoId != null) {
                        tryEnterDomVideoById(replacement.videoId!!) { retryOk ->
                            if (!retryOk) {
                                Toast.makeText(this, "视频对象已失效，请重新点击网页播放", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        Toast.makeText(this, "未找到可接管的视频，请重新点击网页播放", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun tryEnterDomVideoById(videoId: String, callback: (Boolean) -> Unit) {
        val wv = getActiveWebView() ?: return callback(false)
        val escapedId = org.json.JSONObject.quote(videoId)

        wv.evaluateJavascript("NativeVideo.enterTheaterById && NativeVideo.enterTheaterById($escapedId)") { result ->
            if (result == "true") {
                activeVideoId = videoId
                enterPseudoFullscreen()
                callback(true)
            } else {
                callback(false)
            }
        }
    }

    private fun refreshDomVideoCandidates(done: () -> Unit) {
        val wv = getActiveWebView() ?: return done()

        wv.evaluateJavascript("JSON.stringify(NativeVideo.listVideos && NativeVideo.listVideos())") { raw ->
            val text = raw?.removePrefix("\"")
                ?.removeSuffix("\"")
                ?.replace("\\\"", "\"")
                ?.replace("\\\\", "\\") ?: ""

            resourceRegistry.removeTypeForPage(CandidateType.DOM_VIDEO, wv.url)

            try {
                val arr = org.json.JSONArray(text)
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val id = item.optString("id")
                    val title = item.optString("title", "页面内视频")
                    val src = item.optString("currentSrc", "")

                    if (id.isNotBlank()) {
                        resourceRegistry.upsert(
                            VideoCandidate(
                                id = "dom:${wv.url}:$id",
                                type = CandidateType.DOM_VIDEO,
                                title = title.ifBlank { "页面内视频" },
                                url = src.ifBlank { null },
                                pageUrl = wv.url,
                                frameSrc = null,
                                videoId = id,
                                host = android.net.Uri.parse(wv.url ?: "").host,
                                score = item.optInt("score", 1000),
                                confidence = 90,
                                reason = "manual-rescan"
                            )
                        )
                    }
                }
            } catch (_: Exception) {
            }

            updateResourceFabVisibility()
            done()
        }
    }

    private fun openIframeCandidate(candidate: VideoCandidate) {
        val iframeSrc = candidate.frameSrc ?: return
        val parentUrl = candidate.pageUrl ?: getActiveWebView()?.url ?: return

        playerSourceMode = PlayerSourceMode.IFRAME_TOP_PAGE
        
        viewModel.setUiMode(BrowserViewModel.UiMode.WEB)
        bottomBar.visibility = View.GONE
        btnResourceHub.visibility = View.GONE

        urlInput.setText(iframeSrc)

        resourceRegistry.clearAll()
        updateResourceFabVisibility()

        getActiveWebView()?.loadUrl(iframeSrc)
    }

    private fun openStreamFallback(candidate: VideoCandidate) {
        val streamUrl = candidate.url ?: return
        
        playerSourceMode = PlayerSourceMode.STREAM_NATIVE
        
        val intent = android.content.Intent(this, StreamPlayerActivity::class.java).apply {
            putExtra("url", streamUrl)
            putExtra("title", candidate.title)
            putExtra("referer", candidate.pageUrl)
            putExtra("origin", candidate.host?.let { "https://$it" })
            
            val wv = getActiveWebView()
            if (wv != null) {
                putExtra("userAgent", wv.settings.userAgentString)
            }
        }
        
        startActivity(intent)
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
            seekFull.progress = progress
        }
        val text = "${formatTime(currentPosition)} / ${formatTime(currentDuration)}"
        txtFullTime.text = text
        val playIcon = if (isPaused) R.drawable.ic_play else R.drawable.ic_pause
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

                resourceRegistry.upsert(
                    VideoCandidate(
                        id = "dom:${wv.url}:$videoId",
                        type = CandidateType.DOM_VIDEO,
                        title = title.ifBlank { "页面内视频" },
                        url = null,
                        pageUrl = wv.url,
                        frameSrc = null,
                        videoId = videoId,
                        host = android.net.Uri.parse(wv.url ?: "").host,
                        score = 1000,
                        confidence = 95,
                        reason = reason
                    )
                )
                updateResourceFabVisibility()
                
                if (getActiveWebView() != wv) return@runOnUiThread
                
                this@BrowserActivity.activeVideoId = videoId
                this@BrowserActivity.videoBindReason = reason
                this@BrowserActivity.lastVideoActivatedAt = System.currentTimeMillis()
                
                // V3: Do not automatically switch UI mode to floating
                // just record the video.
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
                
                // Keep the current mode in V3. 
                // Don't auto-switch back to WEB.
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

        @JavascriptInterface fun onIframeDetected(src: String) {
            runOnUiThread {
                if (src.isBlank()) return@runOnUiThread
                resourceRegistry.upsert(
                    VideoCandidate(
                        id = "iframe:$src",
                        type = CandidateType.IFRAME_PLAYER,
                        title = "iframe 播放器",
                        url = src,
                        pageUrl = wv.url,
                        frameSrc = src,
                        videoId = null,
                        host = runCatching { android.net.Uri.parse(src).host }.getOrNull(),
                        score = 900,
                        confidence = 60,
                        reason = "iframe-scan"
                    )
                )
                updateResourceFabVisibility()
            }
        }
    }
}
