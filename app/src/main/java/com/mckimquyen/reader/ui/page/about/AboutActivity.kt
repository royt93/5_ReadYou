package com.mckimquyen.reader.ui.page.about

import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.mckimquyen.reader.ui.component.base.BaseScaffold
import com.mckimquyen.reader.ui.component.base.FeedbackIconButton

class AboutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = true
        setContent {
            BaseScaffold(
                containerColor = MaterialTheme.colorScheme.surface,
                navigationIcon = {
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FeedbackIconButton(
                            imageVector = Icons.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        ) {
                            finish()
                        }
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                },
                content = {
                    AboutWebView()
                }
            )
        }
    }
}

@Composable
fun AboutWebView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp),
            factory = { context ->
                WebView(context).apply {
                    settings.cacheMode = WebSettings.LOAD_NO_CACHE
                    settings.javaScriptEnabled = false
                    settings.domStorageEnabled = true
                    loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
                }
            }
        )
    }
}

private const val htmlContent = """
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8"/>
  <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
  <style>
    body {
      font-family: sans-serif;
      line-height: 1.6;
      padding: 16px;
      color: #222;
      background: #fafafa;
    }
    h1, h2, h3 { color: #111; }
    img { max-width: 100%; height: auto; }
    .center { text-align: center; }
    .badges img { margin: 4px; }
    .round-img {
      border: 1px solid #f5f5f5;
      border-radius: 9999px;
    }
    pre {
      background: #eee;
      padding: 8px;
      overflow-x: auto;
    }
  </style>
</head>
<body>

<div class="center">
    <img width="200" height="200" class="round-img"
      src="https://raw.githubusercontent.com/ReadYouApp/ReadYou/main/fastlane/metadata/android/en-US/images/icon.png">
</div>

<br><br>

<div class="center">
    <h1>RSS Hub</h1>
    <p>A modern Android RSS reader forked from <a href="https://github.com/ReadYouApp/ReadYou">Read You</a>, with new improvements and continued maintenance.</p>
    <br/>
    <img src="https://raw.githubusercontent.com/ReadYouApp/ReadYou/main/fastlane/metadata/android/en-US/images/phoneScreenshots/startup.png" width="19.2%" alt="startup" />
    <img src="https://raw.githubusercontent.com/ReadYouApp/ReadYou/main/fastlane/metadata/android/en-US/images/phoneScreenshots/feeds.png" width="19.2%" alt="feeds" />
    <img src="https://raw.githubusercontent.com/ReadYouApp/ReadYou/main/fastlane/metadata/android/en-US/images/phoneScreenshots/flow.png" width="19.2%" alt="flow" />
    <img src="https://raw.githubusercontent.com/ReadYouApp/ReadYou/main/fastlane/metadata/android/en-US/images/phoneScreenshots/read.png" width="19.2%" alt="read" />
    <img src="https://raw.githubusercontent.com/ReadYouApp/ReadYou/main/fastlane/metadata/android/en-US/images/phoneScreenshots/settings.png" width="19.2%" alt="settings" />
</div>

<hr/>

<h2>🙌 Acknowledgement</h2>
<p>
First of all, I would like to express my deep gratitude to the original author of
<a href="https://github.com/ReadYouApp/ReadYou"><b>Read You</b></a>
and all the contributors who have put their time and effort into building such a wonderful open-source RSS reader.
</p>
<p>Without their hard work, <b>RSS Hub</b> would never have been possible.<br/>
This fork aims to continue that spirit — maintaining, improving, and adapting the app for modern Android devices, while keeping it free and open for everyone.</p>

<hr/>

<h2>🚀 About RSS Hub</h2>
<p><b>RSS Hub</b> is a fork of <a href="https://github.com/ReadYouApp/ReadYou">Read You</a>.
It inherits the clean Material You design and powerful RSS reading features, while introducing new fixes and enhancements.</p>

<h3>🔧 Key Improvements</h3>
<ul>
<li>✅ Fixed numerous <b>bugs</b> for a smoother user experience</li>
<li>🎨 Updated <b>styles</b> for improved Material You consistency</li>
<li>📱 Added <b>support for the latest Android versions</b></li>
<li>🛡️ Fixed <b>memory leaks</b> detected by LeakCanary</li>
<li>⚡ Performance and stability improvements</li>
</ul>

<hr/>

<h2>✨ Features</h2>
<ul>
<li>Subscribe to RSS links</li>
<li>Import/export OPML files</li>
<li>Notifications for new articles</li>
<li>Optimized article readability</li>
<li>Full content parsing</li>
<li>Multi-account support</li>
<li>Read aloud articles</li>
</ul>

<hr/>

<h2>🔗 Integration</h2>
<ul>
<li>[x] Fever</li>
<li>[x] Google Reader</li>
<li>[x] FreshRSS</li>
<li>[ ] Miniflux</li>
<li>[ ] Tiny Tiny RSS</li>
<li>[ ] Inoreader</li>
<li>[ ] Feedly</li>
<li>[ ] Feedbin</li>
</ul>

<hr/>

<h2>🛠️ Build from Source</h2>
<pre>
git clone https://github.com/royt93/5_ReadYou.git
</pre>
<p>Then open in Android Studio (latest). Click ▶ Run to build & run.</p>

<hr/>

<h2>Credits</h2>
<p>Includes open-source projects like MusicYou, ParseRSS, Readability4J, opml-parser, compose-html, Rome, Feeder, Seal, news-flash, besticon, Jiffy Reader…</p>

<hr/>

<h2>License</h2>
<p>GNU GPL v3.0 © <a href="https://github.com/ReadYouApp/ReadYou/blob/main/LICENSE">Read You</a></p>

</body>
</html>
"""