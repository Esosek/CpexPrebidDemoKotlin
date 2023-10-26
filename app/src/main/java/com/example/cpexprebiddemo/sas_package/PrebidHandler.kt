package com.example.cpexprebiddemo.sas_package

import android.content.Context
import android.util.Log
import com.google.gson.JsonParser.parseString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.prebid.mobile.BannerAdUnit
import org.prebid.mobile.Host
import org.prebid.mobile.PrebidMobile
import org.prebid.mobile.ResultCode
import org.prebid.mobile.api.data.InitializationStatus
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class PrebidHandler(context: Context) {
    companion object {

        // Prebid.org server config
//        val PBS_HOST: Host =
//            Host.createCustomHost("https://prebid-server-test-j.prebid.org/openrtb2/auction")
//        const val PBS_ACCOUNT_ID = "0689a263-318d-448b-a3d4-b02e8a709d9d"
//        const val PBS_TIMEOUT_MS = 1000

        // Magnite server config
        val PBS_HOST = Host.RUBICON
        const val PBS_ACCOUNT_ID = "10900-mobilewrapper-0"
        private const val PBS_TIMEOUT_MS = 2000

        // BidderTable translates Prebid bidder name to SAS partner name
        private val BIDDER_TABLE = mapOf(
            "rubicon" to "magnite_hb_app",
        )

        // Translates Prebid bidder name to SAS name using BIDDER_TABLE
        val translatedBidder: (String) -> String = { input ->
            BIDDER_TABLE[input] ?: "headerbid-app"
        }

        const val LOG_TAG = "PrebidHandler"
    }

    // Configure Prebid during initialization
    init {
        PrebidMobile.setPrebidServerAccountId(PBS_ACCOUNT_ID)
        PrebidMobile.setPrebidServerHost(PBS_HOST)
        //PrebidMobile.setCustomStatusEndpoint(PBS_STATUS_ENDPOINT)
        PrebidMobile.setTimeoutMillis(PBS_TIMEOUT_MS)
        PrebidMobile.initializeSdk(context) { status ->
            if (status == InitializationStatus.SUCCEEDED) {
                Log.d(LOG_TAG, "Prebid SDK initialized successfully!")
            } else {
                Log.e(
                    LOG_TAG,
                    "Prebid SDK initialization error: $status\n${status.description}"
                )
            }
        }
        PrebidMobile.setShareGeoLocation(false)
    }

    suspend fun requestAds(adUnits: List<AdUnit>): List<AdUnit> = suspendCoroutine { continuation ->
        var processedCount = 0

        // Function to check if all ad units have been processed
        fun checkCompletion() {
            processedCount++
            if (processedCount == adUnits.count()) {
                continuation.resume(adUnits)
            }
        }

        // Function to handle the response for a single ad unit
        fun handleAdUnit(adUnit: AdUnit) {
            if (adUnit.prebidId.isEmpty()) {
                // Skip fetchDemand and add the AdUnit immediately
                checkCompletion()
            } else {
                val banner = BannerAdUnit(adUnit.prebidId, adUnit.size[0], adUnit.size[1])
                banner.fetchDemand { bidInfo, keywords ->
                    if (bidInfo == ResultCode.SUCCESS && !keywords.isNullOrEmpty()) {
                        Log.d(LOG_TAG, "${adUnit.name} keywords: $keywords")
                        val targeting = mapOf(
                            // Keys starting with _ won't be send to SAS
                            //"hbid" to keywords["hb_pb"].toString(),
                            "hbid" to "0.2",
                            "hbid_v" to translatedBidder(keywords["hb_bidder"].toString()),
                            "hb_cache" to keywords["hb_cache_id"].toString(),
                            "_hb_cache_host" to keywords["hb_cache_host"].toString()
                        )
                        Log.d(LOG_TAG, "${adUnit.name}: setting targeting to $targeting")
                        adUnit.setTargeting(targeting) // Set targeting for the AdUnit
                    } else {
                        Log.d(LOG_TAG, "Prebid: No bid returned for ${adUnit.name}")
                        adUnit.setTargeting(emptyMap()) // Handle the case where fetchDemand fails
                    }
                    checkCompletion()
                }
            }
        }

        // Start fetching demand for each ad unit
        for (adUnit in adUnits) {
            handleAdUnit(adUnit)
        }
    }

    // Get the HTML creative of the cached bid
    suspend fun getBid(cacheId: String, cacheHost: String): String {
        Log.d(LOG_TAG, "Fetching creative from Prebid cache $cacheId")
        val cacheUrl = "https://$cacheHost/cache?uuid=$cacheId"

        return try {
            val res = withContext(Dispatchers.IO) {
                sendGetRequest(cacheUrl)
            }
            val jsonRes = parseString(res).asJsonObject
            val creative = jsonRes.get("adm").asString
            Log.d(LOG_TAG, "PBS creative: $creative")
            creative
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Fetching cached bid from PBS failed", e)
            ""
        }
    }

    private fun sendGetRequest(url: String): String {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code ${response.code}")
            return response.body?.string() ?: ""
        }
    }
}