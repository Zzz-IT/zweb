package com.zzz.webvideobrowser

import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {
    private lateinit var settings: BrowserSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        settings = BrowserSettings(this)

        findViewById<ImageButton>(R.id.btnSettingsBack).setOnClickListener {
            finish()
        }

        val swAdBlock = findViewById<SwitchMaterial>(R.id.swAdBlock)
        swAdBlock.isChecked = settings.enableAdBlock
        swAdBlock.setOnCheckedChangeListener { _, isChecked -> settings.enableAdBlock = isChecked }

        val swDesktop = findViewById<SwitchMaterial>(R.id.swDesktop)
        swDesktop.isChecked = settings.enableDesktopMode
        swDesktop.setOnCheckedChangeListener { _, isChecked -> settings.enableDesktopMode = isChecked }

        val swDnt = findViewById<SwitchMaterial>(R.id.swDnt)
        swDnt.isChecked = settings.enableDnt
        swDnt.setOnCheckedChangeListener { _, isChecked -> settings.enableDnt = isChecked }
    }
}
