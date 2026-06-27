package com.zzz.webvideobrowser

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.widget.Toast

class BrowserUrlRouter(
    private val context: Context
) {
    fun shouldOverride(view: WebView, request: WebResourceRequest): Boolean {
        return handleUrl(view, request.url.toString(), request.isForMainFrame)
    }

    fun shouldOverride(view: WebView, rawUrl: String): Boolean {
        return handleUrl(view, rawUrl, true)
    }

    private fun handleUrl(view: WebView, rawUrl: String, isMainFrame: Boolean): Boolean {
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
                // 不让 WebView 加载，否则就是 ERR_UNKNOWN_URL_SCHEME。
                if (isMainFrame) {
                    Toast.makeText(context, "已拦截 App 跳转：$scheme://", Toast.LENGTH_SHORT).show()
                }
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
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "没有可打开此链接的应用", Toast.LENGTH_SHORT).show()
            true
        } catch (_: Exception) {
            true
        }
    }
}
