package tech.cocok.app.data.internet

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import tech.cocok.app.domain.model.InternetImage

internal class GoogleKeywordSearchException(
    val reason: Reason,
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause) {
    enum class Reason { BLOCKED, NETWORK, TIMEOUT, FORMAT }
}

/** Uses a disposable, unattached WebView only to obtain Google's normal keyword result map. */
internal class GoogleKeywordSearch(context: Context) {
    private val applicationContext = context.applicationContext

    suspend fun search(keyword: String, page: Int = 0): List<InternetImage> {
        require(keyword.isNotBlank()) { "Masukkan kata kunci pencarian gambar." }
        require(page in 0..Int.MAX_VALUE / 10) { "Nomor halaman pencarian tidak valid." }
        val url = Uri.Builder()
            .scheme("https")
            .authority("www.google.com")
            .path("/search")
            .appendQueryParameter("q", keyword.trim())
            .appendQueryParameter("udm", "2")
            .appendQueryParameter("hl", "id")
            .appendQueryParameter("safe", "active")
            .appendQueryParameter("start", (page * 10).toString())
            .build()
            .toString()
        return parsePayload(payloadFor(url))
    }

    /**
     * Reads an already-obtained Google result page, e.g. the 303 target of a public
     * photo upload. Photo search lands on the Lens tab, whose image records are not
     * on that tab; try the same page with Google's own visual/products tab values.
     * Every attempt is a plain GET of a Google URL - no page is scripted or bypassed.
     */
    suspend fun fromResultUrl(url: String): List<InternetImage> {
        val start = validated(url)
        val candidates = buildList {
            add(start)
            TAB_UDM.forEach { udm ->
                add(Uri.parse(start).buildUpon().appendQueryParameter("udm", udm).build().toString())
            }
        }.distinct()
        for (candidate in candidates) {
            val images = parsePayload(payloadFor(candidate))
            if (images.isNotEmpty()) return images
        }
        return emptyList()
    }

    private fun validated(url: String): String {
        val uri = Uri.parse(url)
        navigationFailure(uri)?.let { throw it }
        require(uri.path.orEmpty() == "/search") { "Bukan halaman hasil Google." }
        return uri.buildUpon().build().toString()
    }

    private suspend fun payloadFor(url: String): String =
        withContext(Dispatchers.Main.immediate) {
            val request = RenderRequest(applicationContext, url)
            try {
                withTimeoutOrNull(LOAD_TIMEOUT_MS) { request.awaitPayload() }
                    ?: throw request.timeoutFailure()
            } finally {
                request.close()
            }
        }

    private suspend fun parsePayload(payload: String): List<InternetImage> =
        withContext(Dispatchers.Default) {
            val script = try {
                Json.parseToJsonElement(payload).jsonPrimitive.content.removePrefix("ready\n")
            } catch (error: IllegalArgumentException) {
                throw GoogleKeywordSearchException(
                    GoogleKeywordSearchException.Reason.FORMAT,
                    "Data pencarian gambar Google tidak dapat dibaca.",
                    error,
                )
            }
            GoogleImageParser.parse(script)
        }


    private class RenderRequest(private val context: Context, private val url: String) {
        private val handler = Handler(Looper.getMainLooper())
        private var webView: WebView? = null
        private var continuation: CancellableContinuation<String>? = null
        private var closed = false
        private var completed = false
        private var pageFinished = false
        private var generation = 0
        private var evaluating = false
        private val probe = Runnable { extract() }

        @SuppressLint("SetJavaScriptEnabled")
        suspend fun awaitPayload(): String = suspendCancellableCoroutine { pending ->
            continuation = pending
            try {
                val view = WebView(context)
                webView = view
                view.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = false
                    allowContentAccess = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    safeBrowsingEnabled = true
                    javaScriptCanOpenWindowsAutomatically = false
                    setSupportMultipleWindows(false)
                    mediaPlaybackRequiresUserGesture = true
                    // Standard desktop-compatible UA, not a challenge-solving or identity-spoofing service.
                    userAgentString = WebSettings.getDefaultUserAgent(context)
                        .replaceFirst(Regex("\\([^)]*\\)"), "(X11; Linux x86_64)")
                        .replace(Regex("Version/\\S+\\s*"), "")
                        .replace(" Mobile", "")
                }
                CookieManager.getInstance().setAcceptThirdPartyCookies(view, false)
                view.webChromeClient = object : WebChromeClient() {
                    override fun onPermissionRequest(request: PermissionRequest) = request.deny()

                    override fun onGeolocationPermissionsShowPrompt(
                        origin: String,
                        callback: GeolocationPermissions.Callback,
                    ) = callback.invoke(origin, false, false)

                    override fun onShowFileChooser(
                        webView: WebView,
                        filePathCallback: ValueCallback<Array<Uri>>,
                        fileChooserParams: FileChooserParams,
                    ): Boolean {
                        filePathCallback.onReceiveValue(null)
                        return true
                    }
                }
                view.webViewClient = client()
                // No Activity, Compose AndroidView, JavaScript bridge, or user photograph is involved.
                view.measure(
                    View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                )
                view.layout(0, 0, 1280, 1920)
                view.loadUrl(url)
            } catch (error: RuntimeException) {
                fail(GoogleKeywordSearchException(
                    GoogleKeywordSearchException.Reason.NETWORK,
                    "Mesin pencarian gambar tidak dapat dijalankan. Periksa Android System WebView.",
                    error,
                ))
            }
        }

        private fun client() = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return true
                val failure = navigationFailure(request.url)
                if (failure != null) fail(failure)
                return failure != null || !active()
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                // This callback runs off main. Reject unexpected main-frame requests before fetching them.
                if (!request.isForMainFrame) return null
                val failure = navigationFailure(request.url) ?: return null
                handler.post { fail(failure) }
                return WebResourceResponse(
                    "text/plain", "UTF-8", 403, "Forbidden", emptyMap(), ByteArrayInputStream(ByteArray(0)),
                )
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                if (!active()) return
                navigationFailure(Uri.parse(url))?.let {
                    fail(it)
                    return
                }
                generation++
                evaluating = false
                pageFinished = false
                handler.removeCallbacks(probe)
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (!active() || url != view.url) return
                navigationFailure(Uri.parse(url))?.let {
                    fail(it)
                    return
                }
                pageFinished = true
                handler.removeCallbacks(probe)
                extract()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (!request.isForMainFrame) return
                fail(GoogleKeywordSearchException(
                    GoogleKeywordSearchException.Reason.NETWORK,
                    "Tidak dapat terhubung ke pencarian gambar Google. Periksa koneksi internet.",
                ))
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse,
            ) {
                if (!request.isForMainFrame) return
                val status = errorResponse.statusCode
                if (status == 401 || status == 403 || status == 429) {
                    fail(blockedFailure())
                } else if (status >= 400) {
                    fail(GoogleKeywordSearchException(
                        GoogleKeywordSearchException.Reason.NETWORK,
                        "Google tidak dapat memuat pencarian gambar (HTTP $status).",
                    ))
                }
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                fail(GoogleKeywordSearchException(
                    GoogleKeywordSearchException.Reason.NETWORK,
                    "Sambungan aman ke Google gagal. Periksa koneksi dan waktu perangkat.",
                ))
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                fail(GoogleKeywordSearchException(
                    GoogleKeywordSearchException.Reason.NETWORK,
                    "Mesin pencarian gambar berhenti. Periksa Android System WebView.",
                ))
                return true
            }
        }

        private fun extract() {
            val view = webView ?: return
            if (!active() || evaluating || !pageFinished) return
            val currentUrl = view.url ?: return
            navigationFailure(Uri.parse(currentUrl))?.let {
                fail(it)
                return
            }
            val currentGeneration = generation
            evaluating = true
            try {
                view.evaluateJavascript(EXTRACT_SCRIPT) { result ->
                    if (!active() || currentGeneration != generation || currentUrl != view.url) return@evaluateJavascript
                    evaluating = false
                    when {
                        result == "\"blocked\"" -> fail(blockedFailure())
                        result == "\"oversized\"" -> fail(GoogleKeywordSearchException(
                            GoogleKeywordSearchException.Reason.FORMAT,
                            "Data pencarian gambar Google terlalu besar untuk dibaca.",
                        ))
                        result.length > MAX_PAYLOAD_CHARS * 6 + 128 -> fail(GoogleKeywordSearchException(
                            GoogleKeywordSearchException.Reason.FORMAT,
                            "Data pencarian gambar Google terlalu besar untuk dibaca.",
                        ))
                        result.startsWith("\"ready\\n") -> finish(Result.success(result))
                        result == "\"pending\"" -> handler.postDelayed(probe, PROBE_INTERVAL_MS)
                        else -> fail(GoogleKeywordSearchException(
                            GoogleKeywordSearchException.Reason.FORMAT,
                            "Format halaman pencarian gambar Google tidak dikenali.",
                        ))
                    }
                }
            } catch (error: RuntimeException) {
                fail(GoogleKeywordSearchException(
                    GoogleKeywordSearchException.Reason.NETWORK,
                    "Halaman pencarian gambar tidak dapat dibaca.",
                    error,
                ))
            }
        }

        fun timeoutFailure() = GoogleKeywordSearchException(
            if (pageFinished) GoogleKeywordSearchException.Reason.FORMAT else GoogleKeywordSearchException.Reason.TIMEOUT,
            if (pageFinished) "Google tidak menyediakan data gambar yang dapat dibaca dalam batas waktu."
            else "Pencarian gambar terlalu lama. Periksa koneksi internet.",
        )

        private fun active() = !closed && !completed && continuation?.isActive == true

        private fun fail(error: GoogleKeywordSearchException) = finish(Result.failure(error))

        private fun finish(result: Result<String>) {
            if (!active()) return
            completed = true
            handler.removeCallbacksAndMessages(null)
            val pending = continuation
            continuation = null
            pending?.resumeWith(result)
        }

        fun close() {
            if (closed) return
            closed = true
            continuation = null
            handler.removeCallbacksAndMessages(null)
            val view = webView ?: return
            webView = null
            view.stopLoading()
            view.webChromeClient = null
            view.webViewClient = WebViewClient()
            view.removeAllViews()
            view.destroy()
        }
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 30_000L
        const val PROBE_INTERVAL_MS = 400L
        const val MAX_PAYLOAD_CHARS = 4 * 1024 * 1024
        val GOOGLE_HOSTS = setOf("www.google.com", "google.com", "www.google.co.id", "google.co.id")

        // Google's own image-mode tab values: visual matches, products, then all.
        val TAB_UDM = listOf("44", "37", "2")

        fun navigationFailure(uri: Uri): GoogleKeywordSearchException? {
            val host = uri.host?.lowercase()
            if (host == "consent.google.com" || host == "consent.google.co.id" || host == "sorry.google.com" ||
                (host in GOOGLE_HOSTS && (uri.path.orEmpty().startsWith("/sorry") || uri.path.orEmpty().startsWith("/consent")))
            ) return blockedFailure()
            if (uri.scheme != "https" || host !in GOOGLE_HOSTS || uri.userInfo != null || uri.port !in setOf(-1, 443)) {
                return GoogleKeywordSearchException(
                    GoogleKeywordSearchException.Reason.BLOCKED,
                    "Google mengalihkan pencarian ke halaman yang tidak diizinkan.",
                )
            }
            return null
        }

        fun blockedFailure() = GoogleKeywordSearchException(
            GoogleKeywordSearchException.Reason.BLOCKED,
            "Google membatasi pencarian atau meminta CAPTCHA/persetujuan. Pencarian tidak dapat dilanjutkan di aplikasi.",
        )

        val EXTRACT_SCRIPT = """
            (function() {
                var text = ((document.title || '') + '\n' + (document.body ? document.body.innerText : '')).slice(0, 8192);
                if (document.querySelector('#captcha-form, form[action*="/sorry/"], form[action*="consent.google"]') ||
                    /unusual traffic|our systems have detected|automated queries|too many requests|lalu lintas tidak wajar|permintaan otomatis|before you continue to google|sebelum melanjutkan ke google/i.test(text) ||
                    /^(access denied|akses ditolak)/i.test(text.trim())) return 'blocked';
                var scripts = document.scripts;
                var selected = [];
                var size = 0;
                for (var i = 0; i < scripts.length; i++) {
                    var content = scripts[i].textContent || '';
                    if (content.indexOf('window.W_jd') < 0 || !/\bvar\s+m\s*=/.test(content)) continue;
                    size += content.length + 1;
                    if (size > $MAX_PAYLOAD_CHARS) return 'oversized';
                    selected.push(content);
                }
                return selected.length ? 'ready\n' + selected.join('\n') : 'pending';
            })();
        """.trimIndent()
    }
}
