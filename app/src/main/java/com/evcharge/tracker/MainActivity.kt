package com.evcharge.tracker

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File

/**
 * يعرض صفحة assets/index.html (نفس واجهة نسخة الويب) داخل WebView.
 * البيانات تُحفظ في localStorage على الجهاز، ولا يحتاج التطبيق إنترنت للعمل.
 */
class MainActivity : ComponentActivity() {

    private lateinit var web: WebView
    private var chooserCallback: ValueCallback<Array<Uri>>? = null

    private val picker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val uris = if (r.resultCode == RESULT_OK && r.data != null)
            WebChromeClient.FileChooserParams.parseResult(r.resultCode, r.data) else null
        chooserCallback?.onReceiveValue(uris)
        chooserCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        web = WebView(this)
        web.setBackgroundColor(Color.parseColor("#EEF1F6"))
        setContentView(web)

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = false
            cacheMode = WebSettings.LOAD_DEFAULT
            textZoom = 100
        }
        web.addJavascriptInterface(Bridge(), "AndroidBridge")

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                // أي رابط خارجي يفتح في المتصفح وليس داخل التطبيق
                val url = request.url
                if (url.scheme == "http" || url.scheme == "https") {
                    try { startActivity(Intent(Intent.ACTION_VIEW, url)) } catch (_: Exception) {}
                    return true
                }
                return false
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: WebChromeClient.FileChooserParams
            ): Boolean {
                chooserCallback?.onReceiveValue(null)
                chooserCallback = callback
                return try {
                    picker.launch(params.createIntent())
                    true
                } catch (e: Exception) {
                    chooserCallback = null
                    false
                }
            }
        }

        if (savedInstanceState == null) {
            web.loadUrl("file:///android_asset/index.html")
        } else {
            web.restoreState(savedInstanceState)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    /** يستدعيه JavaScript لحفظ/مشاركة ملف (CSV أو نسخة احتياطية). */
    inner class Bridge {
        @JavascriptInterface
        fun saveFile(name: String, data: String) {
            runOnUiThread {
                try {
                    val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
                    val dir = File(cacheDir, "exports").apply { mkdirs() }
                    val file = File(dir, safe)
                    file.writeText(data, Charsets.UTF_8)
                    val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", file)
                    val mime = when {
                        safe.endsWith(".csv") -> "text/csv"
                        safe.endsWith(".json") -> "application/json"
                        else -> "text/plain"
                    }
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = mime
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, safe)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(send, "حفظ أو مشاركة الملف"))
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "تعذر تجهيز الملف", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
