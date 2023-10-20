package com.example.cpexprebiddemo

import android.util.Log
import android.content.ContentValues.TAG
import io.didomi.sdk.Didomi
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

data class AdUnit(
    val name: String,
    val size: List<Int>,
    val layoutContainerId: Int // ID of <FrameLayout> example: R.id.smallRectangleContainer
)

class SasPackage private constructor() {

    private val instanceUrl = "https://optimics-ads.aimatch.com/optimics"
    private val site = "com.example.cpex_prebid_sas"
    private var consentString: String? = null

    private val random: Int
        get() = (Random.nextDouble() * 100000000).toInt()

    companion object {
        private var instance: SasPackage? = null

        fun initialize(): SasPackage {
            return instance ?: SasPackage().also {
                instance = it
            }
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
            val consentString = instance?.consentString

            val parameters = listOf(
                instance?.instanceUrl,
                "hserver",
                "random=${instance?.random}",
                "site=${instance?.site}",
                consentString?.let { "gdpr=1" },
                consentString?.let { "consent=$consentString" },
                "area=${adUnit.name}",
                "size=${adUnit.size[0]}x${adUnit.size[1]}"
            )

            val reqUrl = parameters.joinToString("/")
            Log.d(TAG, "SAS request: $reqUrl")

            try {
                val res = sendGetRequest(reqUrl)
                Log.d(TAG, "SAS response: $res")
                res
            } catch (e: Exception) {
                Log.e(TAG, "${adUnit.name}: Fetching ad from SAS failed", e)
                ""
            }
        }
    }
}
