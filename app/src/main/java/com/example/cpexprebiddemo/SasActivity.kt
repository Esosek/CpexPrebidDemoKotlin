package com.example.cpexprebiddemo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import kotlinx.coroutines.*
import android.widget.Button
import androidx.fragment.app.FragmentActivity
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

class SasActivity : FragmentActivity() {

    companion object {
        // Ad Units definition
        private val adUnits = listOf(
            AdUnit("rectangle-1", listOf(300, 50), R.id.rectangleContainer_1),
            AdUnit("rectangle-2", listOf(300, 250), R.id.rectangleContainer_2)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sas_activity) // Set XML Layout

        val sasPackage = SasPackage.initialize()

        // Load the ads initially


        // Set the "Refresh" button
        val refreshButton = findViewById<Button>(R.id.refreshButton)
        refreshButton.setOnClickListener {
            // Request ads and render them
            val results = runBlocking { sasPackage.requestAds(adUnits) }
            results.forEach { (adUnit, response) ->
                renderAd(adUnit, response)
            }
        }
    }

    // Function to render an ad in a specified WebView container
    private fun renderAd(adUnit: AdUnit, response: String) {
        val webView = WebView(this)
        val adContainer = findViewById<FrameLayout>(adUnit.layoutContainerId)
        adContainer.addView(webView)

        // Configure WebView settings
        webView.settings.javaScriptEnabled = true
        // Disable scrollbars
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false

        webView.webViewClient = object : WebViewClient() {
            // By default click url is opened inside WebView
            // Force opening browser instead
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                view?.context?.startActivity(intent)
                return true
            }
        }

        // Wrap the HTML creative to control rendered element
        val wrappedCreative =
            """<head><meta name="viewport" content="width=device-width, initial-scale=1"></head>
               <body style="margin: 0; overflow: hidden; height: ${adUnit.size[0]}px; width: ${adUnit.size[1]}px">
               $response</body>"""

        // Load the HTML content into the WebView
        webView.loadData(wrappedCreative, "text/html", "UTF-8")
    }
}