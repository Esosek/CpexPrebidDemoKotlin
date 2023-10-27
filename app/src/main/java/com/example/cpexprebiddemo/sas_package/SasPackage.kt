package com.example.cpexprebiddemo.sas_package

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import io.didomi.sdk.Didomi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import kotlin.random.Random

// Initialize with applicationContext from the Activity.kt
// It's needed for initializing PrebidHandler
// Which is used for fetching cached won bids
class SasPackage(private val context: FragmentActivity) {

    // SAS configuration
    private val instanceUrl = "https://optimics-ads.aimatch.com/optimics"
    private val site = "com.example.cpexprebiddemo"

    // Internal references
    private val logTag = "SasPackage"
    private val prebid = PrebidHandler(context)
    private var consentString: String? = null
    private var mid: String? = null

    // Added to SAS call for cache busting
    private val random: Int
        get() = (Random.nextDouble() * 100000000).toInt()

    // Exposed methods to use
    suspend fun requestAds(adUnits: List<AdUnit>) {
        consentString = Didomi.getInstance().userStatus.consentString

//        val adjAdUnits =
//            withContext(CoroutineScope(Dispatchers.IO).coroutineContext) {
//                prebid.requestAds(adUnits)
//            }

        val deferredResults = adUnits.map { adUnit ->
            CoroutineScope(Dispatchers.IO).async {
                val result = requestSingleAd(adUnit)
                adUnit to result
            }
        }
        val results = deferredResults.associate { deferred -> deferred.await() }
        results.forEach { (adUnit, response) ->
            renderAd(context, adUnit, response)
        }
    }

    // Function to render an ad in a specified WebView container
    private fun renderAd(context: Activity, adUnit: AdUnit, response: String) {
        val adContainer = context.findViewById<FrameLayout>(adUnit.layoutContainerId)

        val webView = WebView(context)
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

    // Internal helper functions
    private suspend fun requestSingleAd(adUnit: AdUnit): String {
        return withContext(Dispatchers.IO) {

            var extTargeting = ""

            // Add parameters from the targeting map
            for ((key, value) in adUnit.targeting) {
                // Keys starting with _ (underscore) are internal and won't be send to SAS
                if(!key.startsWith("_")) extTargeting += "$key=$value/"
            }

            val parameters = listOf(
                instanceUrl,
                "hserver",
                "random=${random}",
                "site=${site}",
                "mid=${mid ?: ""}",
                consentString?.let { "gdpr=1" },
                consentString?.let { "consent=$consentString" },
                "area=${adUnit.name}",
                "size=${adUnit.size[0]}x${adUnit.size[1]}",
                extTargeting
            )

            val reqUrl = parameters.joinToString("/")
            Log.d(logTag, "SAS request: $reqUrl")

            try {
                val res = sendGetRequest(reqUrl)
                Log.d(logTag, "SAS response: $res")
                val creative = fetchPrebidCreative(res, adUnit.targeting["_hb_cache_host"]?:"")
                creative
            } catch (e: Exception) {
                Log.e(logTag, "${adUnit.name}: Fetching ad from SAS failed", e)
                ""
            }
        }
    }

    private fun fetchPrebidCreative(response: String, cacheHost: String): String {
        return if (response.startsWith("hb_cache")) {
            val hbCacheValue = response.substring(9)
            val creative = runBlocking { prebid.getBid(hbCacheValue, cacheHost) }
            creative
        } else {
            response
        }
    }

    private fun sendGetRequest(url: String): String {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code ${response.code}")
            // Try to get MID value from SAS response
            if (mid.isNullOrEmpty()) getMidFromResponse(response.headers.toString())
            return response.body?.string() ?: ""
        }
    }

    private fun getMidFromResponse(resHeaders: String) {
        Log.d(logTag, "Trying to get MID from SAS response")
        val regex = Regex("""mid=(\d+);""")
        val matchResult = regex.find(resHeaders)
        mid = matchResult?.groups?.get(1)?.value

        if(mid != null) Log.d(logTag, "MID $mid stored")
        else Log.d(logTag, "No MID found in response")
    }
}
