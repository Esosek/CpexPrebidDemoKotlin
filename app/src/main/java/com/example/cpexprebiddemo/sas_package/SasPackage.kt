package com.example.cpexprebiddemo.sas_package

import android.annotation.SuppressLint
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.prebid.mobile.Host
import java.io.IOException
import kotlin.random.Random

object SasPackage {

    // Configuration
    private lateinit var instanceUrl: String
    private lateinit var site: String
    private var enablePrebid = false

    private const val logTag = "SasPackage"
    private var isInitialized = false
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private lateinit var prebid: PrebidHandler
    private lateinit var context: FragmentActivity
    private var consentString: String? = null
    private var mid: String? = null
    private val random: Int
        get() = (Random.nextDouble() * 100000000).toInt()

    /**
     * Configures SasPackage, must be called before using other methods
     * @param context Required for rendering and PrebidSdk initialization
     * @param instanceUrl Base domain of SAS ad server instance
     * @param enablePrebid (Optional) Set to true if Prebid should be used and provide additional params
     * @param pbsHost (Required if Prebid enabled) Hosted domain of the Prebid Server including /openrtb2/auction endpoint
     * @param pbsAccountId (Required if Prebid enabled) ID of the wrapper stored on Prebid Server
     * @param pbsTimeoutMs (Optional) Time in milliseconds to wait for Prebid Server response, defaults to 1000ms
     * @param bidderTable (Optional) Translation table for Prebid bidder name to SAS partner name, defaults to "headerbid-app" for bidders that are not explicitly set
     */
    fun initialize(
        context: FragmentActivity,
        instanceUrl: String,
        enablePrebid: Boolean = false,
        pbsHost: Host? = null,
        pbsAccountId: String? = null,
        pbsTimeoutMs: Int = 1000,
        bidderTable: Map<String, String> = emptyMap()
    ) {
        this.context = context
        this.instanceUrl = instanceUrl
        this.site = context.packageName
        this.enablePrebid = enablePrebid

        // Initialize Prebid if enabled
        if (enablePrebid) {
            if (pbsHost == null || pbsAccountId == null) {
                Log.e(logTag, "Missing configuration for Prebid, won't initialize")
            } else {
                prebid = PrebidHandler(context, pbsHost, pbsAccountId, pbsTimeoutMs, bidderTable)
            }
        }

        isInitialized = true
        Log.d(logTag, "SasPackage initialized successfully")
    }

    fun requestAds(adUnits: List<AdUnit>) {
        if (!isInitialized) {
            Log.e(
                logTag, "SasPackage is NOT initialized, call SasPackage.initialize() first"
            )
        }
        coroutineScope.launch {
            consentString = Didomi.getInstance().userStatus.consentString

            var adjAdUnits = adUnits
            if (enablePrebid) {
                adjAdUnits = prebid.requestAds(adUnits)
            }

            val deferredResults = adjAdUnits.map { adUnit ->
                CoroutineScope(Dispatchers.IO).async {
                    val result = withContext(Dispatchers.IO) {
                        requestSingleAd(adUnit)
                    }
                    adUnit to result
                }
            }

            val results = deferredResults.associate { deferred -> deferred.await() }
            results.forEach { (adUnit, response) ->
                renderAd(context, adUnit, response)
            }
        }
    }

    // Function to render an ad in a specified WebView container
    @SuppressLint("SetJavaScriptEnabled")
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
            @Deprecated("Deprecated in Java")
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
                if (!key.startsWith("_")) extTargeting += "$key=$value/"
            }

            /** Add random param to bust cache */
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
                val creative = fetchPrebidCreative(res, adUnit.targeting["_hb_cache_host"] ?: "")
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
        val request = Request.Builder().url(url).build()

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

        if (mid != null) Log.d(logTag, "MID $mid stored")
        else Log.d(logTag, "No MID found in response")
    }
}
