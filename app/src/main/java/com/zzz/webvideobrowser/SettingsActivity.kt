package com.zzz.webvideobrowser

import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
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

        val swAdBlock = findViewById<MiuiSwitch>(R.id.swAdBlock)
        swAdBlock.checked = settings.enableAdBlock
        swAdBlock.setOnCheckedChangeListener { isChecked -> settings.enableAdBlock = isChecked }

        val swDesktop = findViewById<MiuiSwitch>(R.id.swDesktop)
        swDesktop.checked = settings.enableDesktopMode
        swDesktop.setOnCheckedChangeListener { isChecked -> settings.enableDesktopMode = isChecked }

        val swDnt = findViewById<MiuiSwitch>(R.id.swDnt)
        swDnt.checked = settings.enableDnt
        swDnt.setOnCheckedChangeListener { isChecked -> settings.enableDnt = isChecked }

        findViewById<android.view.View>(R.id.btnAdBlockSettings).setOnClickListener {
            startActivity(android.content.Intent(this, AdBlockSettingsActivity::class.java))
        }
    }
}
