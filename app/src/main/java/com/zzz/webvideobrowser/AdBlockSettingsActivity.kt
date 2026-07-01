package com.zzz.webvideobrowser

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class AdBlockSettingsActivity : AppCompatActivity() {

    private lateinit var rules: MutableList<String>
    private lateinit var adapter: AdBlockRuleAdapter
    private lateinit var engine: AdBlockEngine
    private lateinit var txtAutoUpdateStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Miuix style status bar
        window.statusBarColor = android.graphics.Color.parseColor("#F5F5F7")
        window.navigationBarColor = android.graphics.Color.parseColor("#F5F5F7")
        val controller = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true

        setContentView(R.layout.activity_adblock_settings)

        engine = AdBlockEngine(this)

        findViewById<View>(R.id.btnSettingsBack).setOnClickListener {
            finish()
        }

        val prefs = getSharedPreferences("browser_prefs", Context.MODE_PRIVATE)
        val defaultRules = "https://easylist-downloads.adblockplus.org/easylistchina.txt"
        val savedRules = prefs.getString("adblock_rule_urls", defaultRules) ?: ""
        rules = savedRules.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()

        val recycler = findViewById<RecyclerView>(R.id.recyclerRules)
        recycler.layoutManager = LinearLayoutManager(this)
        
        adapter = AdBlockRuleAdapter(rules, 
            onRefresh = { url -> refreshRule(url) },
            onDelete = { url, position -> confirmDeleteRule(url, position) }
        )
        recycler.adapter = adapter

        findViewById<View>(R.id.btnAddRule).setOnClickListener {
            showAddRuleDialog()
        }

        txtAutoUpdateStatus = findViewById(R.id.txtAutoUpdateStatus)
        updateAutoUpdateStatusText()

        findViewById<View>(R.id.btnAutoUpdate).setOnClickListener {
            showAutoUpdateDialog()
        }
    }

    private fun saveRules() {
        val prefs = getSharedPreferences("browser_prefs", Context.MODE_PRIVATE)
        val joined = rules.joinToString("\n")
        prefs.edit().putString("adblock_rule_urls", joined).apply()
    }

    private fun showAddRuleDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.layout_bottom_sheet_add_rule, null)
        val editRuleUrl = view.findViewById<EditText>(R.id.editRuleUrl)
        
        view.findViewById<View>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
        view.findViewById<View>(R.id.btnConfirm).setOnClickListener {
            val url = editRuleUrl.text.toString().trim()
            if (url.isNotEmpty() && !rules.contains(url)) {
                rules.add(url)
                adapter.notifyItemInserted(rules.size - 1)
                saveRules()
            }
            dialog.dismiss()
        }
        
        dialog.setContentView(view)
        dialog.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundResource(android.R.color.transparent)
        dialog.show()
    }

    private fun confirmDeleteRule(url: String, position: Int) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.layout_bottom_sheet_delete_rule, null)
        val txtRuleUrl = view.findViewById<TextView>(R.id.txtRuleUrl)
        txtRuleUrl.text = url
        
        view.findViewById<View>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
        view.findViewById<View>(R.id.btnConfirm).setOnClickListener {
            if (position >= 0 && position < rules.size) {
                rules.removeAt(position)
                adapter.notifyItemRemoved(position)
                adapter.notifyItemRangeChanged(position, rules.size)
                saveRules()
            }
            dialog.dismiss()
        }
        
        dialog.setContentView(view)
        dialog.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundResource(android.R.color.transparent)
        dialog.show()
    }

    private fun refreshRule(url: String) {
        Toast.makeText(this, "正在更新规则...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                val success = engine.updateRules(url)
                if (success) {
                    Toast.makeText(this@AdBlockSettingsActivity, "规则更新成功", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@AdBlockSettingsActivity, "规则更新失败", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@AdBlockSettingsActivity, "更新异常: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateAutoUpdateStatusText() {
        val prefs = getSharedPreferences("browser_prefs", Context.MODE_PRIVATE)
        val interval = prefs.getInt("adblock_update_interval", 0)
        txtAutoUpdateStatus.text = when (interval) {
            1 -> "每天"
            7 -> "每周"
            else -> "不自动"
        }
    }

    private fun showAutoUpdateDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.layout_bottom_sheet_auto_update, null)
        val prefs = getSharedPreferences("browser_prefs", Context.MODE_PRIVATE)
        
        fun updateInterval(newInterval: Int) {
            prefs.edit().putInt("adblock_update_interval", newInterval).apply()
            updateAutoUpdateStatusText()
            scheduleAdBlockAutoUpdate(newInterval)
            dialog.dismiss()
        }

        view.findViewById<View>(R.id.btnOptionNone).setOnClickListener { updateInterval(0) }
        view.findViewById<View>(R.id.btnOptionDaily).setOnClickListener { updateInterval(1) }
        view.findViewById<View>(R.id.btnOptionWeekly).setOnClickListener { updateInterval(7) }
        view.findViewById<View>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }

        dialog.setContentView(view)
        dialog.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundResource(android.R.color.transparent)
        dialog.show()
    }

    private fun scheduleAdBlockAutoUpdate(intervalDays: Int) {
        WorkManager.getInstance(this).cancelUniqueWork("adblock_auto_update")

        if (intervalDays <= 0) return

        val request = PeriodicWorkRequestBuilder<AdBlockUpdateWorker>(
            intervalDays.toLong(), TimeUnit.DAYS
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "adblock_auto_update",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
