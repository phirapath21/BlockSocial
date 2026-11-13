package com.example.btcwidget.ui.social

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/** What the page is doing, for the browser's bars; and the controls the bars need. */
class WebPageState {
    var url by androidx.compose.runtime.mutableStateOf("")
    var title by androidx.compose.runtime.mutableStateOf("")
    var progress by androidx.compose.runtime.mutableStateOf(0)
    var canGoBack by androidx.compose.runtime.mutableStateOf(false)
    internal var view: WebView? = null
    fun reload() { view?.reload() }
    /** True if the page went back a step (else the browser should close). */
    fun goBack(): Boolean = view?.takeIf { it.canGoBack() }?.let { it.goBack(); true } ?: false
}

/**
 * The web page itself, in an Android WebView set up cautiously: JavaScript and site storage
 * on (most sites need them), but no access to the phone's files or content, no bridge into
 * the app, Safe Browsing on, no mixed http-in-https content and no pop-up windows. Links to
 * anything but http(s) (another app, a phone number, intent: links) go to Android to handle.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebPage(startUrl: String, state: WebPageState, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = false
                    allowContentAccess = false
                    setSupportMultipleWindows(false)
                    javaScriptCanOpenWindowsAutomatically = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    if (android.os.Build.VERSION.SDK_INT >= 26) safeBrowsingEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    builtInZoomControls = true
                    displayZoomControls = false
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val uri = request.url
                        if (uri.scheme == "http" || uri.scheme == "https") return false
                        // Not a web page: only let user-initiated main-frame navigation open an external app.
                        if (request.isForMainFrame && request.hasGesture()) {
                            runCatching {
                                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                    addCategory(Intent.CATEGORY_BROWSABLE)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                view.context.startActivity(intent)
                            }
                        }
                        return true
                    }

                    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                        state.url = url; state.canGoBack = view.canGoBack()
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        state.url = url; state.title = view.title.orEmpty(); state.canGoBack = view.canGoBack()
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView, newProgress: Int) { state.progress = newProgress }
                    override fun onReceivedTitle(view: WebView, title: String?) { state.title = title.orEmpty() }
                }
                state.view = this
                state.url = startUrl
                loadUrl(startUrl)
            }
        }
    )
    DisposableEffect(state) {
        onDispose { state.view?.apply { stopLoading(); destroy() }; state.view = null }
    }
}

/** Opens [url] in the phone's browser. */
fun openExternally(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
