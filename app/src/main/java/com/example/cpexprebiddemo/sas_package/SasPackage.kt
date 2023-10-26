package com.example.cpexprebiddemo.sas_package

import android.util.Log
import android.content.Context
import io.didomi.sdk.Didomi
import kotlinx.coroutines.CoroutineScope
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

// Initialize with applicationContext from the Activity.kt
// It's needed for initializing PrebidHandler
// Which is used for fetching cached won bids
class SasPackage(context: Context) {

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

    suspend fun requestAds(adUnits: List<AdUnit>): Map<AdUnit, String> {
        consentString = Didomi.getInstance().userStatus.consentString

        val deferredResults = adUnits.map { adUnit ->
            CoroutineScope(Dispatchers.IO).async {
                val result = requestSingleAd(adUnit)
                adUnit to result
            }
        }
        return deferredResults.associate { deferred -> deferred.await() }
    }

    private suspend fun requestSingleAd(adUnit: AdUnit): String {
        return withContext(Dispatchers.IO) {

            var extTargeting = ""

            // Add parameters from the targeting map
            for ((key, value) in adUnit.targeting) {
                extTargeting += "$key=$value/"
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
                val creative = fetchPrebidCreative(res)
                creative
            } catch (e: Exception) {
                Log.e(logTag, "${adUnit.name}: Fetching ad from SAS failed", e)
                ""
            }
        }
    }

    private fun fetchPrebidCreative(response: String): String {
        return if (response.startsWith("hb_cache")) {
            val hbCacheValue = response.substring(9)
            val creative = runBlocking { prebid.getBid(hbCacheValue) }
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
