package com.lumen.launcher

import android.os.Bundle
import android.webkit.WebView
import android.app.Activity

class PrivacyActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val web = WebView(this)
        web.settings.javaScriptEnabled = false
        web.settings.allowFileAccess = false
        setContentView(web)
        web.loadUrl("file:///android_asset/privacy.html")
    }
}
