package com.cpex.sas_package

import android.app.Activity
import android.util.Log
import io.didomi.sdk.Didomi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import kotlin.random.Random


/**
 * A singleton object representing the SasPackage responsible for showing ads.
 * Including option to incorporate Prebid.
 */
object SasPackage {
    private const val version = "1.0.0"
    // Configuration
    private lateinit var instanceUrl: String
    private lateinit var site: String
    private var enablePrebid = false

    private const val logTag = "SasPackage"
    private var isInitialized = false
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private lateinit var prebid: PrebidHandler
    private var consentString: String? = null
    private var mid: String? = null
    private val random: Int
        get() = (Random.nextDouble() * 100000000).toInt()

    /**
     * Configures SasPackage, must be called before using other methods
     * @param instanceUrl Base domain of SAS ad server instance
     * @param enablePrebid (Optional) Set to true if Prebid should be used and provide additional params
     * @param pbsHost (Required if Prebid enabled) Hosted domain of the Prebid Server including /openrtb2/auction endpoint
     * @param pbsAccountId (Required if Prebid enabled) ID of the wrapper stored on Prebid Server
     * @param pbsTimeoutMs (Optional) Time in milliseconds to wait for Prebid Server response, defaults to 1000ms
     * @param bidderTable (Optional) Translation table for Prebid bidder name to SAS partner name, defaults to "headerbid-app" for bidders that are not explicitly set
     */
    fun initialize(
        context: Activity,
        instanceUrl: String,
        enablePrebid: Boolean = false,
        pbsHost: String? = null,
        pbsAccountId: String? = null,
        pbsTimeoutMs: Int = 1000,
        bidderTable: Map<String, String> = emptyMap()
    ) {
        SasPackage.instanceUrl = instanceUrl
        site = context.packageName
        SasPackage.enablePrebid = enablePrebid

        // Initialize Prebid if enabled
        if (enablePrebid) {
            if (pbsHost == null || pbsAccountId == null) {
                Log.e(logTag, "Missing configuration for Prebid, initialization failed")
            } else {
                prebid = PrebidHandler(context, pbsHost, pbsAccountId, pbsTimeoutMs, bidderTable)
            }
        }
        isInitialized = true
        Log.d(logTag, "SasPackage v$version initialized successfully")
    }

    /**
     * Entry point for showing ads
     * - Gets current consent status from Didomi
     * - Fetches demand from Prebid if enabled
     * - Request ads from SAS incl. keywords for Prebid bids
     * - Renders ad creatives
     * @param adUnits List of custom AdUnit data class, Prebid is requested if AdUnit.prebidId is not empty
     */
    fun requestAds(context: Activity, adUnits: List<AdUnit>) {
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
                SasRendering.renderAd(context, adUnit, response)
            }
        }
    }

    /** Clear created WebViews from memory, should be called when refreshing ads
     * or navigating to other Activity
     * @param adUnits List of active AdUnit in current Activity */
    fun clearAdUnits(adUnits: List<AdUnit>) {
        adUnits.forEach { adUnit ->
           adUnit.clearWebView()
        }
    }

    /**
     * Requests a single ad unit from the SAS server based on the given AdUnit.
     *
     * @param adUnit The AdUnit to request from the SAS server.
     * @return The HTML creative for the requested ad.
     */
    private suspend fun requestSingleAd(adUnit: AdUnit): String {
        return withContext(Dispatchers.IO) {

            var extTargeting = ""

            // Add parameters from the targeting map
            for ((key, value) in adUnit.targeting) {
                // Keys starting with _ (underscore) are internal and won't be sent to SAS
                if (!key.startsWith("_")) extTargeting += "$key=$value/"
            }

            val parameters = listOf(
                instanceUrl,
                "hserver", // SAS server endpoint that returns HTML
                "random=$random", // Cache buster
                "site=$site",
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

    /**
     * Fetches a Prebid creative from the response if available, or returns the original response.
     *
     * @param response The response from the SAS server.
     * @param cacheHost The cache host for the Prebid creative.
     * @return The Prebid creative or the original response.
     */
    private fun fetchPrebidCreative(response: String, cacheHost: String): String {
        return if (response.startsWith("hb_cache")) {
            val hbCacheValue = response.substring(9)
            val creative = runBlocking { prebid.getBid(hbCacheValue, cacheHost) }
            creative
        } else {
            response
        }
    }

    /**
     * Sends a GET request to the specified URL and returns the response as a string.
     *
     * @param url The URL to send the GET request to.
     * @return The response from the server as a string.
     * @throws IOException if the GET request is unsuccessful.
     */
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

    /**
     * Extracts the MID (SAS user ID) from the SAS response headers, if available.
     *
     * @param resHeaders The response headers from the SAS server.
     */
    private fun getMidFromResponse(resHeaders: String) {
        Log.d(logTag, "Trying to get MID from SAS response")
        val regex = Regex("""mid=(\d+);""")
        val matchResult = regex.find(resHeaders)
        mid = matchResult?.groups?.get(1)?.value

        if (mid != null) Log.d(logTag, "MID $mid stored")
        else Log.d(logTag, "No MID found in response")
    }
}