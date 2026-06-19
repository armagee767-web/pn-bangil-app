package id.go.pnbangil.webview

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Environment
import android.os.Message
import android.view.KeyEvent
import android.view.View
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import id.go.pnbangil.webview.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val homeUrl = "https://pn-bangil.go.id/wp/"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWebView()

        binding.swipeRefresh.setOnRefreshListener {
            binding.webView.reload()
        }

        binding.btnRetry.setOnClickListener {
            binding.errorLayout.visibility = View.GONE
            binding.webView.loadUrl(homeUrl)
        }

        if (savedInstanceState != null) {
            binding.webView.restoreState(savedInstanceState)
        } else {
            binding.webView.loadUrl(homeUrl)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webView = binding.webView
        val settings = webView.settings

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.mediaPlaybackRequiresUserGesture = false
        settings.setSupportMultipleWindows(true)
        settings.javaScriptCanOpenWindowsAutomatically = true

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()
                return handleSpecialLinks(url)
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                binding.errorLayout.visibility = View.GONE
                binding.progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                super.onReceivedError(view, request, error)
                if (request.isForMainFrame) {
                    binding.errorLayout.visibility = View.VISIBLE
                    binding.progressBar.visibility = View.GONE
                    binding.swipeRefresh.isRefreshing = false
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {

            override fun onProgressChanged(view: WebView, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                binding.progressBar.progress = newProgress
                if (newProgress >= 100) {
                    binding.progressBar.visibility = View.GONE
                }
            }

            // Menangani link target="_blank" / window.open() agar tetap
            // dibuka di dalam WebView yang sama, bukan jendela baru.
            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message
            ): Boolean {
                val newWebView = WebView(this@MainActivity)
                newWebView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        v: WebView,
                        request: WebResourceRequest
                    ): Boolean {
                        view.loadUrl(request.url.toString())
                        return true
                    }
                }
                val transport = resultMsg.obj as WebView.WebViewTransport
                transport.webView = newWebView
                resultMsg.sendToTarget()
                return true
            }
        }

        // Unduh file (PDF, DOCX, dll) melalui DownloadManager bawaan sistem
        webView.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
            try {
                val request = DownloadManager.Request(url.toUri())
                request.setMimeType(mimeType)
                request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    fileName
                )
                val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)
                Toast.makeText(this, "Mengunduh: $fileName", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Gagal mengunduh file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Tautan khusus (telepon, email, WhatsApp, deep-link aplikasi lain)
     * dibuka di aplikasi eksternal. Tautan lainnya tetap dimuat di dalam WebView.
     */
    private fun handleSpecialLinks(url: String): Boolean {
        return when {
            url.startsWith("tel:") || url.startsWith("mailto:") || url.startsWith("sms:") -> {
                openExternal(url)
                true
            }
            url.contains("wa.me") || url.contains("api.whatsapp.com") -> {
                openExternal(url)
                true
            }
            url.startsWith("intent://") -> {
                openIntentUri(url)
                true
            }
            else -> false
        }
    }

    private fun openExternal(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Tidak ada aplikasi untuk membuka tautan ini", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openIntentUri(url: String) {
        try {
            val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Tidak dapat membuka tautan ini", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && binding.webView.canGoBack()) {
            binding.webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.webView.saveState(outState)
    }
}
