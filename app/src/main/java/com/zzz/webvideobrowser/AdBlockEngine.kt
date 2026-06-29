package com.zzz.webvideobrowser

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class AdBlockEngine(private val context: Context) {
    private val blockedDomains = mutableSetOf<String>()
    private val exceptionDomains = mutableSetOf<String>()
    private val ruleFile = File(context.filesDir, "adblock_rules.txt")
    
    var ruleCount: Int = 0
        private set

    init {
        loadRulesFromDisk()
    }

    private fun loadRulesFromDisk() {
        if (!ruleFile.exists()) return
        
        blockedDomains.clear()
        exceptionDomains.clear()
        
        try {
            ruleFile.useLines { lines ->
                lines.forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith("!")) return@forEach
                    
                    if (trimmed.startsWith("@@||") && trimmed.endsWith("^")) {
                        exceptionDomains.add(trimmed.substring(4, trimmed.length - 1))
                    } else if (trimmed.startsWith("||") && trimmed.endsWith("^")) {
                        blockedDomains.add(trimmed.substring(2, trimmed.length - 1))
                    } else if (trimmed.startsWith("||")) {
                        // Some basic matching for domains without trailing ^
                        val domain = trimmed.substring(2).substringBefore("/")
                        if (domain.isNotBlank()) blockedDomains.add(domain)
                    }
                }
            }
            ruleCount = blockedDomains.size
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun updateRules(urlStr: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL(urlStr)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            if (connection.responseCode == 200) {
                connection.inputStream.use { input ->
                    ruleFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                loadRulesFromDisk()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun shouldBlock(url: String): Boolean {
        if (ruleCount == 0 || blockedDomains.isEmpty()) return false
        
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val host = uri.host ?: return false
        
        // Exact match exception
        if (exceptionDomains.contains(host)) return false
        
        // Subdomain matching
        var currentDomain = host
        while (currentDomain.isNotEmpty()) {
            if (exceptionDomains.contains(currentDomain)) return false
            if (blockedDomains.contains(currentDomain)) return true
            
            val dotIndex = currentDomain.indexOf('.')
            if (dotIndex == -1) break
            currentDomain = currentDomain.substring(dotIndex + 1)
        }
        
        return false
    }
}
