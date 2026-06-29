package com.zzz.webvideobrowser

import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import android.widget.Switch
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    private lateinit var settings: BrowserSettings
    private lateinit var adBlockEngine: AdBlockEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        settings = BrowserSettings(this)
        adBlockEngine = AdBlockEngine(this)

        findViewById<ImageButton>(R.id.btnSettingsBack).setOnClickListener {
            finish()
        }

        val swAdBlock = findViewById<Switch>(R.id.swAdBlock)
        swAdBlock.isChecked = settings.enableAdBlock
        swAdBlock.setOnCheckedChangeListener { _, isChecked -> settings.enableAdBlock = isChecked }

        val swDesktop = findViewById<Switch>(R.id.swDesktop)
        swDesktop.isChecked = settings.enableDesktopMode
        swDesktop.setOnCheckedChangeListener { _, isChecked -> settings.enableDesktopMode = isChecked }

        val swDnt = findViewById<Switch>(R.id.swDnt)
        swDnt.isChecked = settings.enableDnt
        swDnt.setOnCheckedChangeListener { _, isChecked -> settings.enableDnt = isChecked }

        val txtRuleStatus = findViewById<TextView>(R.id.txtRuleStatus)
        updateRuleStatusText(txtRuleStatus)

        findViewById<android.view.View>(R.id.btnUpdateRules).setOnClickListener {
            txtRuleStatus.text = "正在下载并解析规则..."
            lifecycleScope.launch {
                val success = adBlockEngine.updateRules("https://easylist-downloads.adblockplus.org/easylistlite.txt")
                if (success) {
                    Toast.makeText(this@SettingsActivity, "更新成功！加载了 ${adBlockEngine.ruleCount} 条规则", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@SettingsActivity, "规则更新失败，请检查网络", Toast.LENGTH_SHORT).show()
                }
                updateRuleStatusText(txtRuleStatus)
            }
        }
    }

    private fun updateRuleStatusText(textView: TextView) {
        if (adBlockEngine.ruleCount > 0) {
            textView.text = "已加载 ${adBlockEngine.ruleCount} 条核心过滤规则"
        } else {
            textView.text = "尚未下载拦截规则，点击更新"
        }
    }
}
