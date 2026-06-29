package com.zzz.webvideobrowser

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView

class BrowserUrlRouter(
    private val context: Context
) {
    fun shouldOverride(view: WebView, request: WebResourceRequest): Boolean {
        return handleUrl(view, request.url.toString())
    }

    fun shouldOverride(view: WebView, rawUrl: String): Boolean {
        return handleUrl(view, rawUrl)
    }

    private fun handleUrl(view: WebView, rawUrl: String): Boolean {
        val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return true
        val scheme = uri.scheme?.lowercase()

        when (scheme) {
            "http", "https", "about", "data", "blob", "javascript" -> {
                return false
            }

            "intent" -> {
                return handleIntentUrl(view, rawUrl)
            }

            "market" -> {
                openExternal(rawUrl)
                return true
            }

            null -> {
                return true
            }

            else -> {
                // 例如 baiduhaokan://、snssdk://、bilibili://、weixin://
                // 不再提示拦截，直接尝试向系统发出请求。
                // 如果系统有对应的 App 且设置了询问，系统会自然询问；
                // 如果没有 App 响应，则静默失败，不影响用户。
                openExternal(rawUrl)
                return true
            }
        }
    }

    private fun handleIntentUrl(view: WebView, rawUrl: String): Boolean {
        val intent = runCatching {
            Intent.parseUri(rawUrl, Intent.URI_INTENT_SCHEME)
        }.getOrNull() ?: return true

        val fallback = intent.getStringExtra("browser_fallback_url")

        if (!fallback.isNullOrBlank()) {
            view.loadUrl(fallback)
            return true
        }

        return openExternalIntent(intent)
    }

    private fun openExternal(rawUrl: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(rawUrl))
        return openExternalIntent(intent)
    }

    private fun openExternalIntent(intent: Intent): Boolean {
        return try {
            intent.addCategory(Intent.CATEGORY_BROWSABLE)
            intent.component = null
            // 移除额外标志，让系统默认行为接管（如有多个 App 系统会弹出选择器）
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            // 静默失败，不干扰用户
            true
        }
    }
}
