package com.example.cpexprebiddemo.sas_package

import android.content.Context
import android.util.Log
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
        const val PBS_DOMAIN = "https://prebid-server-test-j.prebid.org"
        val PBS_HOST: Host =
            Host.createCustomHost("$PBS_DOMAIN/openrtb2/auction")
        const val PBS_ACCOUNT_ID = "0689a263-318d-448b-a3d4-b02e8a709d9d"
        const val PBS_TIMEOUT_MS = 1000
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

    suspend fun requestAd(adUnit: AdUnit): Map<String, String> = suspendCoroutine { continuation ->
        val banner = BannerAdUnit(adUnit.prebidId, adUnit.size[0], adUnit.size[1])

        banner.fetchDemand { bidInfo, keywords ->
            if (bidInfo == ResultCode.SUCCESS && !keywords.isNullOrEmpty()) {
                val targeting = mapOf(
                    "hbid" to keywords["hb_pb"].toString(),
                    "hbid_v" to "headerbid-app",
                    "hb_cache" to keywords["hb_cache_id_prebid"].toString()
                )
                Log.d(LOG_TAG, "Keywords for ${adUnit.name}: $keywords")
                Log.d(LOG_TAG, "Result map ${adUnit.name}: $targeting")
                continuation.resume(targeting)
            } else {
                Log.d(LOG_TAG, "Fetching demand for ${adUnit.name} failed")
                continuation.resume(emptyMap()) // Handle the case where fetchDemand fails
            }
        }
    }



    // Get the HTML creative of the cached bid
    fun getBid(cacheId: String): String {
        val cacheUrl =  "$PBS_DOMAIN/cache?uuid=$cacheId"

        return try {
            val res = sendGetRequest(cacheUrl)
            Log.d(LOG_TAG, "PBS response: $res")
            ""

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