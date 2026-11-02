package io.alexis900.neoheadsup

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.MaterialAutoCompleteTextView

class SettingsActivity : AppCompatActivity() {

    private lateinit var spinnerTheme: MaterialAutoCompleteTextView
    private lateinit var textVersion: TextView
    private lateinit var rowRepository: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        spinnerTheme = findViewById(R.id.spinner_theme)
        textVersion = findViewById(R.id.text_version)
        rowRepository = findViewById(R.id.row_repository)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener {
            finish()
        }
        setupThemeSpinner()
        setupAbout()
    }

    private fun setupThemeSpinner() {
        val labels = THEME_MODES.map { mode ->
            when (mode) {
                Prefs.THEME_LIGHT -> getString(R.string.theme_light)
                Prefs.THEME_DARK -> getString(R.string.theme_dark)
                else -> getString(R.string.theme_system)
            }
        }.toTypedArray()

        val position = THEME_MODES.indexOf(Prefs.getThemeMode(this)).coerceAtLeast(0)
        spinnerTheme.setSimpleItems(labels)
        spinnerTheme.setText(labels[position], false)
        spinnerTheme.setOnItemClickListener { _, _, selected, _ ->
            val mode = THEME_MODES[selected]
            if (mode == Prefs.getThemeMode(this@SettingsActivity)) return@setOnItemClickListener
            spinnerTheme.dismissDropDown()
            Prefs.setThemeMode(this@SettingsActivity, mode)
            ThemeManager.apply(this@SettingsActivity)
        }
    }

    private fun setupAbout() {
        val info = packageManager.getPackageInfo(packageName, 0)
        textVersion.text = getString(
            R.string.version_format,
            info.versionName,
            info.longVersionCode
        )

        rowRepository.setOnClickListener {
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse(getString(R.string.repository_url))
            )
            try {
                startActivity(intent)
            } catch (exception: ActivityNotFoundException) {
                Toast.makeText(
                    this,
                    R.string.repository_open_failed,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private companion object {
        val THEME_MODES = arrayOf(Prefs.THEME_SYSTEM, Prefs.THEME_LIGHT, Prefs.THEME_DARK)
    }
}
