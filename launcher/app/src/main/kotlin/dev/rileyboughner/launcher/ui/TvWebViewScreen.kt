package dev.rileyboughner.launcher.ui

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.view.KeyEvent
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * In-launcher TV screen for a web target (Weave, the movie wheel) — there's
 * no reliable default browser to hand off to on Google TV, so this hosts a
 * [WebView] directly. D-pad arrow keys pan/scroll and move spatial-navigation
 * focus between the page's own focusable elements (Chromium's WebView
 * supports this natively once the view itself has focus; no extra wiring
 * needed here beyond requesting that focus on load). Back always returns to
 * the TV home screen — this is a single-purpose kiosk view of one site, not
 * a general browser, so there's no in-page back-stack to unwind first.
 */
@Composable
fun TvWebViewScreen(
    title: String,
    url: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var loadFailed by remember(url) { mutableStateOf(false) }
    val currentOnBack = rememberUpdatedState(onBack)
    BackHandler(onBack = { currentOnBack.value() })

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .safeDrawingPadding(),
    ) {
        TvTextRow(
            label = "← TV Home",
            onClick = { currentOnBack.value() },
            modifier = Modifier.padding(horizontal = 56.dp),
        )
        HairlineDivider()

        Box(modifier = Modifier.fillMaxSize()) {
            TvWebView(
                url = url,
                onLoadFailed = { loadFailed = true },
                onLoadStarted = { loadFailed = false },
                modifier = Modifier.fillMaxSize(),
            )

            if (loadFailed) {
                UnreachableOverlay(title = title, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun TvWebView(
    url: String,
    onLoadFailed: () -> Unit,
    onLoadStarted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnLoadFailed = rememberUpdatedState(onLoadFailed)
    val currentOnLoadStarted = rememberUpdatedState(onLoadStarted)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(AndroidColor.BLACK)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                isFocusable = true
                isFocusableInTouchMode = true

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, pageUrl: String?, favicon: android.graphics.Bitmap?) {
                        currentOnLoadStarted.value()
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?,
                    ) {
                        // Only the main frame failing counts as "couldn't reach the
                        // service" — a failed sub-resource (an ad blocked DNS entry,
                        // a missing favicon) shouldn't blank out an otherwise-working page.
                        if (request?.isForMainFrame != false) {
                            currentOnLoadFailed.value()
                        }
                    }
                }

                // D-pad-first: load with the WebView already focused so arrow keys
                // immediately pan/navigate instead of requiring an initial OK press.
                setOnKeyListener { v, keyCode, event ->
                    if (!v.hasFocus() && event.action == KeyEvent.ACTION_DOWN) {
                        v.requestFocus()
                    }
                    false
                }

                loadUrl(url)
                requestFocus()
            }
        },
        update = { webView ->
            if (webView.url != url) {
                webView.loadUrl(url)
            }
        },
    )
}

@Composable
private fun UnreachableOverlay(title: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column {
            Text(
                text = "Couldn't reach $title",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
            )
            Text(
                text = "Check the network connection and try again later.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}
