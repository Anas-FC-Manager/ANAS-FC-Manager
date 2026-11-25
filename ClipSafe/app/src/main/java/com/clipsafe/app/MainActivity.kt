package com.clipsafe.app

import androidx.fragment.app.FragmentActivity
import android.os.Bundle
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.*
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class MainActivity : FragmentActivity() {
    private val bg = Color.rgb(13, 17, 25)
    private val panel = Color.rgb(25, 32, 44)
    private val accent = Color.rgb(91, 156, 255)
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var root: LinearLayout
    private lateinit var clipsBox: LinearLayout
    private lateinit var clipboard: ClipboardManager
    private var unlocked = false
    private var lastSaved = ""
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { saveClipboard(silent = true) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        val masterKey = MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        prefs = EncryptedSharedPreferences.create(
            this, "clipsafe_secure", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (prefs.getString("pin_hash", null) == null) showPinSetup() else showUnlock()
    }

    override fun onResume() {
        super.onResume()
        if (unlocked) clipboard.addPrimaryClipChangedListener(clipListener)
    }

    override fun onPause() {
        clipboard.removePrimaryClipChangedListener(clipListener)
        super.onPause()
    }

    private fun base(title: String, subtitle: String? = null) {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 28, 22, 18)
            setBackgroundColor(bg)
        }
        root.addView(TextView(this).apply {
            text = title; textSize = 27f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
        })
        if (subtitle != null) root.addView(TextView(this).apply {
            text = subtitle; textSize = 14f; setTextColor(0xFFADB8C8.toInt()); setPadding(0, 7, 0, 18)
        })
        setContentView(root)
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text; textSize = 14f; setTextColor(0xFFCBD5E1.toInt()); setPadding(0, 12, 0, 5)
    }

    private fun edit(hint: String, numeric: Boolean = false) = EditText(this).apply {
        this.hint = hint; setHintTextColor(0xFF758195.toInt()); setTextColor(Color.WHITE)
        textSize = 16f; setSingleLine(true); setPadding(16, 12, 16, 12); setBackgroundColor(panel)
        if (numeric) inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
    }

    private fun button(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text; isAllCaps = false; textSize = 15f; setTextColor(Color.WHITE)
        backgroundTintList = android.content.res.ColorStateList.valueOf(accent)
        setOnClickListener { action() }
    }

    private fun showPinSetup() {
        base("ClipSafe 🔐", "أنشئ رقم PIN لحماية سجل الحافظة")
        val pin = edit("PIN من 6 أرقام أو أكثر", true)
        val confirm = edit("أعد كتابة PIN", true)
        root.addView(label("الرقم السري")); root.addView(pin)
        root.addView(label("تأكيد الرقم")); root.addView(confirm)
        root.addView(button("إنشاء القفل") {
            val p = pin.text.toString()
            if (p.length < 6 || p != confirm.text.toString()) toast("تأكد من تطابق الرقم وأنه 6 أرقام على الأقل")
            else {
                prefs.edit().putString("pin_hash", hashPin(p)).apply()
                unlocked = true
                showHome()
            }
        })
        root.addView(TextView(this).apply {
            text = "التخزين محلي ومشفّر، ولا يحتاج التطبيق إلى الإنترنت."
            setTextColor(0xFF9AA8BA.toInt()); textSize = 13f; setPadding(0, 18, 0, 0)
        })
    }

    private fun showUnlock() {
        unlocked = false
        base("مرحباً بك في ClipSafe", "افتح خزنتك للوصول إلى النصوص المحفوظة")
        val pin = edit("أدخل PIN", true)
        root.addView(label("الرقم السري")); root.addView(pin)
        root.addView(button("فتح باستخدام PIN") {
            if (hashPin(pin.text.toString()) == prefs.getString("pin_hash", "")) {
                unlocked = true; showHome()
            } else toast("الرقم السري غير صحيح")
        })
        val canBio = BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
        if (canBio) root.addView(button("فتح بالبصمة") { biometricUnlock() })
        root.addView(TextView(this).apply {
            text = "البصمة اختيار سريع، وPIN يظل متاحاً دائماً."
            setTextColor(0xFF9AA8BA.toInt()); textSize = 13f; setPadding(0, 16, 0, 0)
        })
    }

    private fun biometricUnlock() {
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    unlocked = true; showHome()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    toast(errString.toString())
                }
            })
        val info = BiometricPrompt.PromptInfo.Builder().setTitle("فتح ClipSafe")
            .setSubtitle("أكد هويتك بالبصمة").setNegativeButtonText("إلغاء").build()
        prompt.authenticate(info)
    }

    private fun showHome() {
        unlocked = true
        base("ClipSafe", "خزنة الحافظة الخاصة بك · تخزين محلي مشفّر")
        root.addView(TextView(this).apply {
            text = "جاهز للحفظ أثناء استخدام التطبيق"; textSize = 13f
            setTextColor(0xFF9AA8BA.toInt()); setPadding(0, 8, 0, 12)
        })
        root.addView(button("حفظ النص المنسوخ الآن") { saveClipboard(silent = false) })
        val search = edit("ابحث في النصوص المحفوظة…")
        root.addView(search)
        val scroll = ScrollView(this)
        clipsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 12, 0, 0) }
        scroll.addView(clipsBox)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        search.addTextChangedListener(SimpleWatcher { renderClips(it) })
        root.addView(button("حذف كل السجل") {
            android.app.AlertDialog.Builder(this).setTitle("حذف كل النصوص؟")
                .setMessage("لن يمكن استعادة السجل بعد الحذف.")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("حذف") { _, _ ->
                    prefs.edit().remove("clips").apply()
                    renderClips(search.text.toString())
                    toast("تم حذف السجل")
                }.show()
        })
        root.addView(button("قفل التطبيق") { unlocked = false; showUnlock() })
        renderClips("")
        clipboard.removePrimaryClipChangedListener(clipListener)
        clipboard.addPrimaryClipChangedListener(clipListener)
    }

    private fun saveClipboard(silent: Boolean) {
        if (!unlocked) return
        val clip = clipboard.primaryClip
        if (clip == null || clip.itemCount == 0) { if (!silent) toast("الحافظة فارغة"); return }
        val text = clip.getItemAt(0).coerceToText(this)?.toString()?.trim().orEmpty()
        if (text.isBlank() || text == lastSaved) { if (!silent) toast("ما في نص جديد للحفظ"); return }
        lastSaved = text
        val arr = readClips()
        if ((0 until arr.length()).any { index -> arr.optJSONObject(index)?.optString("text") == text }) { if (!silent) toast("النص محفوظ مسبقاً"); return }
        arr.put(JSONObject().put("text", text).put("time", System.currentTimeMillis()))
        while (arr.length() > 300) arr.remove(0)
        prefs.edit().putString("clips", arr.toString()).apply()
        if (::clipsBox.isInitialized) renderClips("")
        if (!silent) toast("تم حفظ النص")
    }

    private fun readClips(): JSONArray = try { JSONArray(prefs.getString("clips", "[]")) } catch (_: Exception) { JSONArray() }

    private fun renderClips(query: String) {
        if (!::clipsBox.isInitialized) return
        clipsBox.removeAllViews()
        val arr = readClips()
        var count = 0
        for (i in arr.length() - 1 downTo 0) {
            val obj = arr.optJSONObject(i) ?: continue
            val text = obj.optString("text")
            if (!text.contains(query, true)) continue
            count++
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(14, 12, 14, 12); setBackgroundColor(panel)
            }
            card.addView(TextView(this).apply {
                this.text = text; setTextColor(Color.WHITE); textSize = 15f; maxLines = 5
            })
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
            actions.addView(Button(this).apply {
                this.text = "نسخ"; isAllCaps = false
                setOnClickListener { clipboard.setPrimaryClip(ClipData.newPlainText("ClipSafe", text)); toast("تم النسخ") }
            })
            actions.addView(Button(this).apply {
                this.text = "حذف"; isAllCaps = false
                setOnClickListener { arr.remove(i); prefs.edit().putString("clips", arr.toString()).apply(); renderClips(query) }
            })
            card.addView(actions)
            val lp = LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = 10; clipsBox.addView(card, lp)
        }
        if (count == 0) clipsBox.addView(TextView(this).apply {
            text = "لا توجد نصوص محفوظة هنا بعد."; setTextColor(0xFF9AA8BA.toInt()); setPadding(4, 18, 4, 18)
        })
    }

    private fun hashPin(pin: String): String {
        val salt = prefs.getString("pin_salt", null)?.let {
            android.util.Base64.decode(it, android.util.Base64.NO_WRAP)
        } ?: ByteArray(16).also {
            SecureRandom().nextBytes(it)
            prefs.edit().putString("pin_salt", android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP)).apply()
        }
        val spec = PBEKeySpec(pin.toCharArray(), salt, 120000, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}

private class SimpleWatcher(val changed: (String) -> Unit) : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { changed(s?.toString() ?: "") }
    override fun afterTextChanged(s: android.text.Editable?) {}
}
