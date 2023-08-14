package com.example.cpexprebiddemo

import android.content.ContentValues.TAG
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import org.prebid.mobile.AdSize
import org.prebid.mobile.Host
import org.prebid.mobile.PrebidMobile
import org.prebid.mobile.api.data.InitializationStatus
import org.prebid.mobile.api.rendering.BannerView
import io.didomi.sdk.Didomi
import io.didomi.sdk.DidomiInitializeParameters
import org.prebid.mobile.api.exceptions.AdException
import org.prebid.mobile.api.rendering.listeners.BannerViewListener

class MainActivity : FragmentActivity() {
    // Prebid.org server config
//    companion object {
//        val PBS_HOST: Host = Host.createCustomHost("https://prebid-server-test-j.prebid.org/openrtb2/auction")
//        const val PBS_ACCOUNT_ID = "0689a263-318d-448b-a3d4-b02e8a709d9d"
//        const val PBS_STATUS_ENDPOINT = "https://prebid-server-test-j.prebid.org/status"
//    }

    // Magnite server config
    companion object {
        val PBS_HOST = Host.RUBICON
        const val PBS_ACCOUNT_ID = "10900-mobilewrapper-0"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // Set the XML layout here
        initDidomiSDK()
        initPrebidSDK()

        // Display CMP UI
        // Always call it, checks internally if it's required
        Didomi.getInstance().setupUI(this)

        // Create a banner ad unit
        // val rectangle = createBannerAdUnit("prebid-ita-banner-320-50", width = 300, height = 50)
        // val leaderboard =
        //   createBannerAdUnit("prebid-demo-banner-multisize", width = 728, height = 90)
        val smallRectangle = createBannerAdUnit("10900-imp-rectangle-300-50", width = 300, height = 50)
        val bigRectangle =
            createBannerAdUnit("10900-imp-rectangle-300-250", width = 300, height = 250)

        val smallRectangleAdContainer = findViewById<FrameLayout>(R.id.smallRectangleContainer)
        smallRectangleAdContainer.addView(smallRectangle)

        val bigRectangleAdContainer = findViewById<FrameLayout>(R.id.bigRectangleContainer)
        bigRectangleAdContainer.addView(bigRectangle)

        // Load the ads initially
        //rectangle.loadAd()
        //leaderboard.loadAd()

        // Set the "Refresh" button
        val refreshButton = findViewById<Button>(R.id.refreshButton)
        refreshButton.setOnClickListener {
            smallRectangle.loadAd()
            //smallRectangle.setAutoRefreshDelay(30)
            bigRectangle.loadAd()
            //bigRectangle.setAutoRefreshDelay(30)
        }

        // Set the "Show CMP" button
        val showCmpButton = findViewById<Button>(R.id.showCmpButton)
        showCmpButton.setOnClickListener {
            Log.d(TAG, "Didomi: Forcing CMP UI display")
            //
            Didomi.getInstance().forceShowNotice(this)
        }
    }

    private fun initDidomiSDK() {
        try {
            Log.d(TAG, "Didomi: Initializing SDK")
            Didomi.getInstance().initialize(
                this.application,
                DidomiInitializeParameters(apiKey = "9a8e2159-3781-4da1-9590-fbf86806f86e")
            )

            // Do not use the Didomi.getInstance() object here for anything else than registering your ready listener
            // The SDK might not be ready yet

            Didomi.getInstance().onReady {
                // The SDK is ready, you can now interact with it
                Log.d(TAG, "Didomi: SDK initialized successfully!")
                Log.d(TAG, "ConsentString=" + Didomi.getInstance().userStatus.consentString)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error while initializing the Didomi SDK", e)
        }
    }

    private fun initPrebidSDK() {
        PrebidMobile.setPrebidServerAccountId(PBS_ACCOUNT_ID)
        PrebidMobile.setPrebidServerHost(PBS_HOST)
        //PrebidMobile.setCustomStatusEndpoint(PBS_STATUS_ENDPOINT)
        PrebidMobile.initializeSdk(applicationContext) { status ->
            if (status == InitializationStatus.SUCCEEDED) {
                Log.d(TAG, "Prebid: SDK initialized successfully!")
            } else {
                Log.e(TAG, "Prebid: SDK initialization error: $status\n${status.description}")
            }
        }
        PrebidMobile.setShareGeoLocation(false)
    }

    private fun createBannerAdUnit(configId: String, width: Int, height: Int): BannerView {
        return BannerView(this, configId, AdSize(width, height))
    }
}
