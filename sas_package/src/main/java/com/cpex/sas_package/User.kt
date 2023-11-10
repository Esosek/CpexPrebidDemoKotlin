package com.cpex.sas_package

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import io.didomi.sdk.Didomi
import io.didomi.sdk.DidomiInitializeParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import android.content.SharedPreferences


object User {
    private const val logTag = "User"
    private const val midPrefsKey = "mid"

    private lateinit var localStorage: SharedPreferences

    var cmpVendorId: String? = null
    var evalConsentForId: Boolean = true
    var consentString: String = ""
        get() = Didomi.getInstance().userStatus.consentString
        private set
    var mid: String? = null
        get() {
            if (evalConsentForId && !isCmpVendorEnabled) {
                clearMidFromPrefs()
                return null
            }
            return field
        }
        private set(value) {
            if (evalConsentForId && !isCmpVendorEnabled) {
                clearMidFromPrefs()
                field = null
            }
            field = value
        }
    var advertisingId: String? = null
        private set
    private val isCmpVendorEnabled: Boolean
        get() = Didomi.getInstance().userStatus.vendors.global.enabled.contains(cmpVendorId)

    fun init(context: Activity) {
        initDidomiSDK(context)
        Didomi.getInstance().onReady {
            localStorage = context.getSharedPreferences("sasPackage", Context.MODE_PRIVATE)
            mid = localStorage.getString(midPrefsKey, null)
            if (mid != null) {
                Log.d(logTag, "MID fetched from localStorage: $mid")
            }
        }
    }

    /**
     * Extracts the MID (SAS user ID) from the SAS response headers, if available and defined vendor is enabled.
     *
     * @param resHeaders The response headers from the SAS server.
     */
    fun getMidFromResponse(resHeaders: String): String? {
        if (!mid.isNullOrEmpty()) { // No need to get MID
            return mid
        }
        Log.d(logTag, "Trying to get MID from SAS response")
        val regex = Regex("""mid=(\d+);""")
        val matchResult = regex.find(resHeaders)
        mid = matchResult?.groups?.get(1)?.value

        // Stores MID in localStorage
        val editor = localStorage.edit()
        editor.putString(midPrefsKey, mid)
        editor.apply()

        if (mid != null) Log.d(logTag, "MID $mid stored")
        else Log.d(logTag, "No MID found in response")
        return mid
    }

    /** Reads and stores user's Advertising ID if consented.
     * Used for user synchronization in bid stream.
     * @param context Current app Activity context
     */
    suspend fun updateAdvertisingId(context: Activity) {
        if (evalConsentForId && !isCmpVendorEnabled) { // No consent
            advertisingId = null
            return
        }
        if (advertisingId != null) { // No need for update
            return
        }
        try {
            advertisingId = withContext(Dispatchers.IO) {
                AdvertisingIdClient.getAdvertisingIdInfo(context).id
            }
            Log.d(logTag, "Fetched Advertising ID: $advertisingId")
        } catch (e: IOException) {
            Log.d(logTag, "Fetching Advertising ID failed: $e")
        } catch (e: GooglePlayServicesNotAvailableException) {
            Log.d(logTag, "Fetching Advertising ID failed: $e")
        } catch (e: GooglePlayServicesRepairableException) {
            Log.d(logTag, "Fetching Advertising ID failed: $e")
        }
    }

    /** Initializes Didomi SDK with CPEx account during SasPackage initialization.
     * CMP UI must be requested from FragmentActivity.
     * @param context Current app Activity context */
    private fun initDidomiSDK(context: Activity) {
        Log.d(ContentValues.TAG, "Didomi: Initializing SDK")
        if (Didomi.getInstance().isInitialized) {
            Log.d(ContentValues.TAG, "Didomi: SDK was already initialized, skipping")
            return
        }
        try {
            Didomi.getInstance().initialize(
                context.application,
                DidomiInitializeParameters(apiKey = "9a8e2159-3781-4da1-9590-fbf86806f86e")
            )

            // Do not use the Didomi.getInstance() object here for anything else than registering your ready listener
            // The SDK might not be ready yet

            Didomi.getInstance().onReady {
                // The SDK is ready, you can now interact with it
                Log.d(ContentValues.TAG, "Didomi: SDK initialized successfully!")
                Log.d(
                    ContentValues.TAG,
                    "ConsentString=" + Didomi.getInstance().userStatus.consentString
                )
            }
        } catch (e: Exception) {
            Log.e(ContentValues.TAG, "Error while initializing the Didomi SDK", e)
        }
    }

    private fun clearMidFromPrefs() {
        //Log.d(logTag, "Vendor disabled, MID is NOT stored and Prefs are cleared")
        val editor = localStorage.edit()
        editor.remove(midPrefsKey)
        editor.apply()
    }
}