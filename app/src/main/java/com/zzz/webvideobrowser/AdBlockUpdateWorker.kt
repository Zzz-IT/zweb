package com.zzz.webvideobrowser

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class AdBlockUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("browser_prefs", Context.MODE_PRIVATE)
        val rulesStr = prefs.getString("adblock_rule_urls", "") ?: ""
        val urls = rulesStr.lines().map { it.trim() }.filter { it.isNotEmpty() }

        if (urls.isEmpty()) return Result.success()

        val engine = AdBlockEngine(applicationContext)
        var successCount = 0

        urls.forEach { url ->
            if (engine.updateRules(url)) successCount++
        }

        prefs.edit()
            .putLong("adblock_last_update_at", System.currentTimeMillis())
            .putInt("adblock_last_update_success_count", successCount)
            .apply()

        return if (successCount > 0) Result.success() else Result.retry()
    }
}
