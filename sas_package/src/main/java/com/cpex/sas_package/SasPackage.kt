package com.cpex.sas_package

import android.app.Activity
import android.util.Log
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
 * A singleton object representing the SasPackage responsible for showing ads and tracking users in SAS.
 * Requires Didomi SDK to work, it's initialized alongside SasPackage.
 * Including option to incorporate Prebid.
 */
object SasPackage {
    private const val version = "1.2.0"

    // Configuration
    private lateinit var instanceUrl: String
    private lateinit var site: String
    private var appDomain: String? = null
    private var prebidEnabled = true

    private const val logTag = "SasPackage"
    private var isInitialized = false
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private lateinit var prebid: PrebidHandler

    private val random: Int
        get() = (Random.nextDouble() * 100000000).toInt()

    var interscrollerHeight: Double = 0.0
        private set

    /**
     * Configures SasPackage, must be called before using other methods. Initializes Didomi SDK.
     * @param context Current activity context. Required primarily for Prebid and Didomi SDK initialization.
     * @param instanceUrl Base domain of SAS ad server instance
     * @param appDomain (Optional) Encoded domain of the app (eg. "https%3A%2F%2Fwww.cpex.cz")
     * @param pbsHost Hosted domain of the Prebid Server including /openrtb2/auction endpoint
     * @param pbsAccountId ID of the wrapper stored on Prebid Server
     * @param prebidEnabled (Optional) Set to false if Prebid should be disabled, use with caution
     * @param pbsTimeoutMs (Optional) Time in milliseconds to wait for Prebid Server response, defaults to 1000ms
     * @param cmpVendorId (Optional) Publisher's Didomi ID for storing MID in localStorage, MID won't be stored if consent disabled or ID is missing
     * @param bidderTable (Optional) Translation table for Prebid bidder name to SAS partner name, defaults to "headerbid-app" for bidders that are not explicitly set
     * @param interscrollerHeight (Optional) Between 0 and 1, where 1 means the interscroller will be display at 100% height of the View, defaults to 0.5
     */
    fun initialize(
        context: Activity,
        instanceUrl: String,
        pbsHost: String,
        pbsAccountId: String,
        appDomain: String? = null,
        prebidEnabled: Boolean = true,
        pbsTimeoutMs: Int = 1000,
        cmpVendorId: String? = null,
        evalConsentForId: Boolean = true,
        bidderTable: Map<String, String> = emptyMap(),
        interscrollerHeight: Double = .5
    ) {
        this.instanceUrl = instanceUrl
        site = context.packageName
        this.appDomain = appDomain
        this.prebidEnabled = prebidEnabled
        User.cmpVendorId = cmpVendorId
        User.evalConsentForId = evalConsentForId
        this.interscrollerHeight = interscrollerHeight

        // Initialize Didomi CMP
        User.initDidomiSDK(context)

        // Initialize Prebid if enabled
        if (prebidEnabled) {
            prebid = PrebidHandler(context, pbsHost, pbsAccountId, pbsTimeoutMs, bidderTable)
        }
        isInitialized = true
        Log.d(logTag, "SasPackage $version initialized successfully")
    }

    /**
     * Entry point for showing ads
     * - Gets consent string from Didomi
     * - Gets advertisingId if possible
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
            User.updateAdvertisingId(context)

            var adjAdUnits = adUnits
            if (prebidEnabled) {
                adjAdUnits = prebid.requestAds(adUnits)
            }

            val deferredResults = adjAdUnits.map { adUnit ->
                async(Dispatchers.IO) {
                    val result = withContext(Dispatchers.IO) {
                        requestSingleAd(adUnit)
                    }
                    adUnit to result
                }
            }

            val results = deferredResults.associate { deferred -> deferred.await() }
            results.forEach { (adUnit, response) ->
                Rendering.renderAd(context, adUnit, response)
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

            val consentString = User.consentString

            val parameters = listOf(
                instanceUrl,
                "hserver", // SAS server endpoint that returns HTML
                "random=$random", // Cache buster
                "site=$site",
                appDomain.let { "appUrl=$appDomain" },
                "mid=${User.mid ?: ""}",
                "ifa=${User.advertisingId ?: ""}",
                consentString.let { "gdpr=1" },
                consentString.let { "consent=$consentString" },
                "area=${adUnit.name}",
                "size=${adUnit.size[0]}x${adUnit.size[1]}",
                extTargeting
            )

            val reqUrl = parameters.joinToString("/")
            Log.d(logTag, "SAS request for ${adUnit.name}: $reqUrl")

            try {
                val res = sendGetRequest(reqUrl)
                Log.d(logTag, "SAS response for ${adUnit.name}: $res")
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
            User.getMidFromResponse(response.headers.toString())
            return response.body?.string() ?: ""
        }
    }
}
