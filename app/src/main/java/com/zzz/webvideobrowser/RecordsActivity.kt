package com.zzz.webvideobrowser

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.zzz.webvideobrowser.db.BookmarkRecord
import com.zzz.webvideobrowser.db.BrowserDatabase
import com.zzz.webvideobrowser.db.HistoryRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RecordsActivity : AppCompatActivity() {

    private lateinit var db: BrowserDatabase
    private lateinit var adapter: RecordsAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var tagHistory: TextView
    private lateinit var tagBookmark: TextView
    
    private var isShowingHistory = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_records)

        db = BrowserDatabase.getDatabase(this)
        
        tagHistory = findViewById(R.id.tagHistory)
        tagBookmark = findViewById(R.id.tagBookmark)
        recyclerView = findViewById(R.id.recyclerView)
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = RecordsAdapter(
            onItemClick = { url ->
                val result = Intent().apply { putExtra("url", url) }
                setResult(Activity.RESULT_OK, result)
                finish()
            },
            onItemLongClick = { item ->
                showDeleteConfirmDialog(item)
            }
        )
        recyclerView.adapter = adapter
        
        tagHistory.setOnClickListener {
            isShowingHistory = true
            updateTabUI()
            loadData()
        }
        
        tagBookmark.setOnClickListener {
            isShowingHistory = false
            updateTabUI()
            loadData()
        }
        
        findViewById<View>(R.id.btnClear).setOnClickListener {
            showClearDataDialog()
        }
        
        val showBookmarks = intent.getBooleanExtra("show_bookmarks", false)
        if (showBookmarks) {
            isShowingHistory = false
            updateTabUI()
        }
        
        loadData()
    }

    private fun updateTabUI() {
        if (isShowingHistory) {
            tagHistory.setTextColor(Color.parseColor("#111111"))
            tagHistory.setTypeface(null, Typeface.BOLD)
            tagBookmark.setTextColor(Color.parseColor("#999999"))
            tagBookmark.setTypeface(null, Typeface.NORMAL)
        } else {
            tagBookmark.setTextColor(Color.parseColor("#111111"))
            tagBookmark.setTypeface(null, Typeface.BOLD)
            tagHistory.setTextColor(Color.parseColor("#999999"))
            tagHistory.setTypeface(null, Typeface.NORMAL)
        }
    }

    private fun loadData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val data = if (isShowingHistory) {
                db.browserDao().getRecentHistory(500)
            } else {
                db.browserDao().getAllBookmarks()
            }
            withContext(Dispatchers.Main) {
                adapter.submitList(data)
            }
        }
    }

    private fun showDeleteConfirmDialog(item: Any) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.layout_bottom_sheet_delete_rule, null)
        val txtRuleUrl = view.findViewById<TextView>(R.id.txtRuleUrl)
        
        val (titleStr, urlStr) = when (item) {
            is HistoryRecord -> Pair(item.title, item.url)
            is BookmarkRecord -> Pair(item.title, item.url)
            else -> Pair("", "")
        }
        txtRuleUrl.text = "$titleStr\n$urlStr"
        
        view.findViewById<View>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
        view.findViewById<View>(R.id.btnConfirm).setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                if (item is HistoryRecord) {
                    db.browserDao().deleteHistory(item)
                } else if (item is BookmarkRecord) {
                    db.browserDao().deleteBookmark(item)
                }
                loadData()
            }
            dialog.dismiss()
        }
        
        dialog.setContentView(view)
        dialog.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundResource(android.R.color.transparent)
        dialog.show()
    }

    private fun showClearDataDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.layout_bottom_sheet_clear_data, null)
        
        view.findViewById<View>(R.id.btnClearCache).setOnClickListener {
            // We set a result flag to clear cache in browser activity
            val prefs = getSharedPreferences("browser_prefs", MODE_PRIVATE)
            prefs.edit().putBoolean("pending_clear_cache", true).apply()
            Toast.makeText(this, "已加入清理队列，将在返回浏览器时执行", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        
        view.findViewById<View>(R.id.btnClearCookie).setOnClickListener {
            showSecondaryConfirmDialog("清除 Cookie") {
                val prefs = getSharedPreferences("browser_prefs", MODE_PRIVATE)
                prefs.edit().putBoolean("pending_clear_cookie", true).apply()
                Toast.makeText(this, "已加入清理队列，将在返回浏览器时执行", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        
        view.findViewById<View>(R.id.btnClearHistory).setOnClickListener {
            showSecondaryConfirmDialog("清除浏览记录") {
                lifecycleScope.launch(Dispatchers.IO) {
                    db.browserDao().clearAllHistory()
                    if (isShowingHistory) {
                        loadData()
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@RecordsActivity, "浏览记录已清除", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            dialog.dismiss()
        }
        
        view.findViewById<View>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }

        dialog.setContentView(view)
        dialog.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundResource(android.R.color.transparent)
        dialog.show()
    }

    private fun showSecondaryConfirmDialog(actionName: String, onConfirm: () -> Unit) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.layout_bottom_sheet_delete_rule, null)
        val txtTitle = view.findViewById<TextView>(R.id.txtRuleUrl)
        txtTitle.text = "您确定要 $actionName 吗？\n此操作不可恢复。"
        
        val btnConfirm = view.findViewById<TextView>(R.id.btnConfirm)
        btnConfirm.text = "确定清除"
        
        view.findViewById<View>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
        btnConfirm.setOnClickListener {
            onConfirm()
            dialog.dismiss()
        }
        
        dialog.setContentView(view)
        dialog.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundResource(android.R.color.transparent)
        dialog.show()
    }
}
