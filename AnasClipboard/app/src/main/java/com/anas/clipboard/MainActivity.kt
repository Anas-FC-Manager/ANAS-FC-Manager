package com.anas.clipboard

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray

class MainActivity : Activity() {
    private lateinit var clipboard: ClipboardManager
    private lateinit var list: LinearLayout
    private lateinit var empty: TextView
    private val prefs by lazy { getSharedPreferences("anas_clips", Context.MODE_PRIVATE) }
    private val clips = mutableListOf<String>()
    private var listenerRegistered = false

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        readCurrentClipboard()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        loadClips()
        handleIncomingIntent(intent)
        buildScreen()
        renderClips()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
        renderClips()
    }

    override fun onResume() {
        super.onResume()
        if (!listenerRegistered) {
            clipboard.addPrimaryClipChangedListener(clipListener)
            listenerRegistered = true
        }
        readCurrentClipboard()
    }

    override fun onPause() {
        if (listenerRegistered) {
            clipboard.removePrimaryClipChangedListener(clipListener)
            listenerRegistered = false
        }
        super.onPause()
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val incoming = when (intent.action) {
            Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            else -> null
        }
        if (!incoming.isNullOrBlank()) addClip(incoming)
    }

    private fun readCurrentClipboard() {
        try {
            if (clipboard.hasPrimaryClip()) {
                val item = clipboard.primaryClip?.getItemAt(0)
                val text = item?.coerceToText(this)?.toString()
                if (!text.isNullOrBlank()) addClip(text)
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) { }
    }

    private fun loadClips() {
        clips.clear()
        val raw = prefs.getString("items", "[]") ?: "[]"
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) clips.add(arr.getString(i))
        } catch (_: Exception) { }
    }

    private fun persist() {
        val arr = JSONArray()
        clips.forEach { arr.put(it) }
        prefs.edit().putString("items", arr.toString()).apply()
    }

    private fun addClip(text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || clips.firstOrNull() == clean) return
        clips.remove(clean)
        clips.add(0, clean)
        while (clips.size > 500) clips.removeAt(clips.lastIndex)
        persist()
        if (::list.isInitialized) renderClips()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(16))
            setBackgroundColor(0xFFF4F7FB.toInt())
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val header = TextView(this).apply {
            text = "تطبيق أنس"
            textSize = 27f
            setTextColor(0xFF102A43.toInt())
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        root.addView(header, LinearLayout.LayoutParams(-1, dp(48)))
        val subtitle = TextView(this).apply {
            text = "حافظتك للنصوص المنسوخة"
            textSize = 14f
            setTextColor(0xFF52606D.toInt())
            gravity = Gravity.CENTER
        }
        root.addView(subtitle, LinearLayout.LayoutParams(-1, dp(30)))

        val info = TextView(this).apply {
            text = "ملاحظة: أندرويد لا يسمح للتطبيق بقراءة الحافظة في الخلفية. للحفظ من التطبيقات الأخرى، استخدم مشاركة النص إلى «تطبيق أنس»، أو ارجع للتطبيق بعد النسخ ليحفظ آخر نص."
            textSize = 13f
            setTextColor(0xFF334E68.toInt())
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(0xFFE1ECF4.toInt())
        }
        val infoParams = LinearLayout.LayoutParams(-1, -2)
        infoParams.setMargins(0, dp(12), 0, dp(12))
        root.addView(info, infoParams)

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val pasteButton = Button(this).apply {
            text = "حفظ من الحافظة الآن"
            setOnClickListener {
                readCurrentClipboard()
                Toast.makeText(this@MainActivity, "تم تحديث الحافظة", Toast.LENGTH_SHORT).show()
            }
        }
        actions.addView(pasteButton, LinearLayout.LayoutParams(0, dp(48), 1f))
        val clearButton = Button(this).apply {
            text = "مسح الكل"
            setOnClickListener {
                clips.clear()
                persist()
                renderClips()
            }
        }
        val clearParams = LinearLayout.LayoutParams(-2, dp(48))
        clearParams.marginStart = dp(8)
        actions.addView(clearButton, clearParams)
        root.addView(actions)

        empty = TextView(this).apply {
            text = "ما في نصوص محفوظة لسه."
            textSize = 16f
            setTextColor(0xFF627D98.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(28), 0, dp(28))
        }
        root.addView(empty, LinearLayout.LayoutParams(-1, -2))
        val scroll = ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun renderClips() {
        if (!::list.isInitialized) return
        list.removeAllViews()
        empty.visibility = if (clips.isEmpty()) View.VISIBLE else View.GONE
        clips.forEachIndexed { index, value ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(8))
                setBackgroundColor(0xFFFFFFFF.toInt())
                layoutDirection = View.LAYOUT_DIRECTION_RTL
            }
            val body = TextView(this).apply {
                text = value
                textSize = 15f
                setTextColor(0xFF243B53.toInt())
                maxLines = 8
                setTextIsSelectable(true)
            }
            card.addView(body)
            val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val copy = Button(this).apply {
                text = "نسخ"
                setOnClickListener {
                    clipboard.setPrimaryClip(ClipData.newPlainText("نص محفوظ", value))
                    Toast.makeText(this@MainActivity, "تم النسخ", Toast.LENGTH_SHORT).show()
                }
            }
            buttons.addView(copy)
            val delete = Button(this).apply {
                text = "حذف"
                setOnClickListener {
                    clips.remove(value)
                    persist()
                    renderClips()
                }
            }
            buttons.addView(delete)
            card.addView(buttons)
            val params = LinearLayout.LayoutParams(-1, -2)
            params.setMargins(0, 0, 0, dp(10))
            list.addView(card, params)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
