package com.brruham.t9ime

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var store: SettingsStore
    private lateinit var userStore: UserWordStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store     = SettingsStore(this)
        userStore = UserWordStore(this)
        setContentView(buildUI())
    }

    private fun buildUI(): ScrollView {
        val sv = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 60)
            setBackgroundColor(0xFF0D0D1A.toInt())
        }
        sv.addView(root)

        fun header(text: String) = TextView(this).apply {
            this.text = text
            textSize = 11f
            setTextColor(0xFF00D4AA.toInt())
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
            letterSpacing = 0.2f
            setPadding(0, 32, 0, 8)
        }

        fun label(text: String) = TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(0xFFE8EAF0.toInt())
            setPadding(0, 4, 0, 4)
        }

        fun subLabel(text: String) = TextView(this).apply {
            this.text = text
            textSize = 11f
            setTextColor(0xFF6B7A99.toInt())
            setPadding(0, 0, 0, 8)
        }

        fun divider() = android.view.View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1).apply { setMargins(0, 16, 0, 8) }
            setBackgroundColor(0xFF1E2440.toInt())
        }

        fun row(label: android.view.View, control: android.view.View): LinearLayout {
            return LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                addView(label, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(control)
            }
        }

        fun switch(checked: Boolean, onChange: (Boolean) -> Unit): Switch {
            return Switch(this).apply {
                isChecked = checked
                thumbTintList = android.content.res.ColorStateList.valueOf(0xFF00D4AA.toInt())
                trackTintList = android.content.res.ColorStateList.valueOf(0xFF243060.toInt())
                setOnCheckedChangeListener { _, v -> onChange(v) }
            }
        }

        // ── Title ────────────────────────────────────────────────────────────
        root.addView(TextView(this).apply {
            text = "T9 KEYBOARD"
            textSize = 22f
            setTextColor(0xFFE8EAF0.toInt())
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 4)
        })
        root.addView(TextView(this).apply {
            text = "Pengaturan"
            textSize = 13f
            setTextColor(0xFF505878.toInt())
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 24)
        })

        // ── Setup keyboard ───────────────────────────────────────────────────
        root.addView(header("⚙  SETUP"))

        val isEnabled = isImeEnabled()
        val isDefault = isImeDefault()

        root.addView(label(if (isEnabled) "✅ Keyboard aktif" else "❌ Keyboard belum aktif"))
        if (!isEnabled) {
            root.addView(subLabel("Aktifkan T9 Keyboard di pengaturan sistem"))
            root.addView(Button(this).apply {
                text = "Buka Pengaturan Keyboard"
                setBackgroundColor(0xFF14192E.toInt())
                setTextColor(0xFF00D4AA.toInt())
                setOnClickListener {
                    startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                }
            })
        }
        if (isEnabled && !isDefault) {
            root.addView(subLabel("Set sebagai keyboard default"))
            root.addView(Button(this).apply {
                text = "Pilih Sebagai Default"
                setBackgroundColor(0xFF14192E.toInt())
                setTextColor(0xFF00D4AA.toInt())
                setOnClickListener {
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showInputMethodPicker()
                }
            })
        }
        root.addView(divider())

        // ── Input ─────────────────────────────────────────────────────────────
        root.addView(header("✏  INPUT"))

        root.addView(row(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(label("Prediksi Kata"))
                addView(subLabel("Tampilkan saran kata saat mengetik"))
            },
            switch(store.predictionOn) { store.predictionOn = it }
        ))
        root.addView(row(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(label("Auto Kapitalisasi"))
                addView(subLabel("Huruf besar otomatis awal kalimat"))
            },
            switch(store.autoCaps) { store.autoCaps = it }
        ))
        root.addView(row(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(label("Auto Spasi"))
                addView(subLabel("Spasi otomatis setelah pilih kata T9"))
            },
            switch(store.autoSpace) { store.autoSpace = it }
        ))
        root.addView(divider())

        // ── Multi-tap delay ───────────────────────────────────────────────────
        root.addView(header("⏱  MULTI-TAP DELAY"))
        root.addView(subLabel("Waktu tunggu sebelum huruf dikonfirmasi"))

        val delays = listOf(500L to "Cepat (500ms)", 750L to "Normal (750ms)", 1000L to "Lambat (1000ms)")
        val rg = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        delays.forEach { (ms, labelStr) ->
            rg.addView(RadioButton(this).apply {
                text = labelStr
                isChecked = store.mtDelay == ms
                setTextColor(0xFFCCCCCC.toInt())
                setOnClickListener { store.mtDelay = ms }
            })
        }
        root.addView(rg)
        root.addView(divider())

        // ── Tampilan ──────────────────────────────────────────────────────────
        root.addView(header("🎨  TAMPILAN"))
        root.addView(row(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(label("Tampilkan Label Huruf"))
                addView(subLabel("ABC, DEF, dll di bawah angka"))
            },
            switch(store.showSubLabel) { store.showSubLabel = it }
        ))
        root.addView(row(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(label("Getar"))
                addView(subLabel("Getaran 18ms tiap ketukan"))
            },
            switch(store.vibrate) { store.vibrate = it }
        ))

        // Theme
        root.addView(subLabel("Tema warna"))
        val themes = listOf("dark" to "🌙 Dark Navy", "amoled" to "⬛ AMOLED Hitam", "light" to "☀ Light")
        val tg = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        themes.forEach { (key, labelStr) ->
            tg.addView(RadioButton(this).apply {
                text = labelStr
                isChecked = store.theme == key
                setTextColor(0xFFCCCCCC.toInt())
                setOnClickListener { store.theme = key }
            })
        }
        root.addView(tg)
        root.addView(divider())

        // ── Kamus ─────────────────────────────────────────────────────────────
        root.addView(header("📚  KAMUS PENGGUNA"))
        val wordCount = userStore.getAllWords().size
        root.addView(label("$wordCount kata tersimpan"))
        root.addView(subLabel("Kata yang dipelajari dari kebiasaan mengetik"))

        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(Button(this@SettingsActivity).apply {
                text = "Export ke sdcard"
                setBackgroundColor(0xFF14192E.toInt())
                setTextColor(0xFF00D4AA.toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { setMargins(0, 0, 8, 0) }
                setOnClickListener { exportUserWords() }
            })
            addView(Button(this@SettingsActivity).apply {
                text = "Hapus semua"
                setBackgroundColor(0xFF1A0A0A.toInt())
                setTextColor(0xFFE94560.toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    android.app.AlertDialog.Builder(this@SettingsActivity)
                        .setTitle("Hapus kamus?")
                        .setMessage("Semua kata yang dipelajari akan dihapus.")
                        .setPositiveButton("Hapus") { _, _ ->
                            userStore.clearAll()
                            Toast.makeText(this@SettingsActivity, "Kamus dihapus", Toast.LENGTH_SHORT).show()
                            recreate()
                        }
                        .setNegativeButton("Batal", null)
                        .show()
                }
            })
        })
        root.addView(divider())

        // ── Version ───────────────────────────────────────────────────────────
        root.addView(TextView(this).apply {
            text = "T9 IME v1.0 · by brruham-arch"
            textSize = 11f
            setTextColor(0xFF2A3050.toInt())
            gravity = android.view.Gravity.CENTER
            setPadding(0, 24, 0, 0)
        })

        return sv
    }

    private fun exportUserWords() {
        try {
            val words = userStore.getAllWords()
            if (words.isEmpty()) {
                Toast.makeText(this, "Tidak ada kata untuk diekspor", Toast.LENGTH_SHORT).show()
                return
            }
            val file = java.io.File(
                android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS), "t9_user_words.txt")
            file.writeText(words.entries
                .sortedByDescending { it.value }
                .joinToString("\n") { "${it.key} ${it.value}" })
            Toast.makeText(this, "Disimpan: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal export: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun isImeEnabled(): Boolean {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val pkg = packageName
        return imm.enabledInputMethodList.any { it.packageName == pkg }
    }

    private fun isImeDefault(): Boolean {
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        return current?.startsWith(packageName) == true
    }
}
