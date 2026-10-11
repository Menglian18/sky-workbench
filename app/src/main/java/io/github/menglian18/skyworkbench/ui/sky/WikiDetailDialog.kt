package io.github.menglian18.skyworkbench.ui.sky

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.menglian18.skyworkbench.sky.WikiPage
import io.github.menglian18.skyworkbench.sky.WikiStyle

/**
 * 详细介绍（1.4.0 新增）：点季节、点进行中的活动，弹出这个。
 *
 * 内容是 WIKI 的**正文**（`action=render`），不是整站页面 —— 没有导航栏、广告、页脚，
 * 再注入一小段跟随主题的 CSS，看着就像 App 自己的页面。
 *
 * 打不开时（没网、WIKI 限流）会给一句人话和一个「用浏览器打开」的出口，**不会白屏**。
 */
@Composable
fun WikiDetailDialog(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val readerUrl = remember(title) { WikiPage.readerUrl(title) }
    val pageUrl = remember(title) { WikiPage.pageUrl(title) }
    val css = rememberReaderCss()

    var webView by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }

    Dialog(
        // 返回键：网页里点进过别的内容就先回退，退无可退才关掉
        onDismissRequest = {
            val view = webView
            if (view != null && view.canGoBack()) view.goBack() else onDismiss()
        },
        // 默认对话框四周留白，这里要全屏才够看长页面
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    TextButton(onClick = { openInBrowser(context, pageUrl) }) { Text("浏览器") }
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surface)

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { viewContext ->
                            createWikiWebView(
                                context = viewContext,
                                url = readerUrl,
                                css = css,
                                onCreated = { webView = it },
                                onStarted = { loading = true },
                                onFinished = { loading = false },
                                onFailed = {
                                    loading = false
                                    failed = true
                                },
                            )
                        },
                    )

                    if (loading && !failed) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }

                    if (failed) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = "这个页面打不开。\n可能是没网，也可能是 WIKI 太忙（这个站经常限流）。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    failed = false
                                    loading = true
                                    webView?.reload()
                                }) { Text("重试") }
                                TextButton(onClick = { openInBrowser(context, pageUrl) }) {
                                    Text("用浏览器打开")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // WebView 很占内存，关掉就得销毁，不然反复开关会越吃越多
    DisposableEffect(Unit) {
        onDispose {
            webView?.let { view ->
                runCatching {
                    view.stopLoading()
                    view.loadUrl("about:blank")
                    view.destroy()
                }
            }
            webView = null
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createWikiWebView(
    context: Context,
    url: String,
    css: String,
    onCreated: (WebView) -> Unit,
    onStarted: () -> Unit,
    onFinished: () -> Unit,
    onFailed: () -> Unit,
): WebView = WebView(context).apply {
    layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
    )
    // 只为"把样式注入页面"开脚本；正文本身是 action=render 出来的，不带任何脚本
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = false
    settings.setSupportZoom(false)
    settings.builtInZoomControls = false
    settings.loadWithOverviewMode = false
    settings.useWideViewPort = false
    settings.cacheMode = WebSettings.LOAD_DEFAULT

    webViewClient = object : WebViewClient() {
        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            onStarted()
        }

        override fun onPageFinished(view: WebView, url: String?) {
            onFinished()
            // 页面加载完再注入，这时候 <head> 一定在
            runCatching { view.evaluateJavascript(WikiStyle.injectScript(css), null) }
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            // 只有"整页打不开"才算失败；某张图挂了不影响阅读
            if (request.isForMainFrame) onFailed()
        }
    }

    loadUrl(url)
    onCreated(this)
}

private fun openInBrowser(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** 按当前主题现算一段阅读样式（主题换了要重算，所以把颜色都记进 remember 的键里） */
@Composable
private fun rememberReaderCss(): String {
    val scheme = MaterialTheme.colorScheme
    return remember(
        scheme.background,
        scheme.surface,
        scheme.onBackground,
        scheme.onSurfaceVariant,
        scheme.secondary,
    ) {
        WikiStyle.readerCss(
            background = hexOf(scheme.background),
            surface = hexOf(scheme.surface),
            text = hexOf(scheme.onBackground),
            muted = hexOf(scheme.onSurfaceVariant),
            accent = hexOf(scheme.secondary),
        )
    }
}

private fun hexOf(color: Color): String = WikiStyle.hex(color.red, color.green, color.blue)
