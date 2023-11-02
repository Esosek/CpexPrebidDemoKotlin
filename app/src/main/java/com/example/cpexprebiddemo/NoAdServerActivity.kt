package com.example.cpexprebiddemo

import android.content.ContentValues.TAG
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import org.prebid.mobile.AdSize
import org.prebid.mobile.Host
import org.prebid.mobile.PrebidMobile
import org.prebid.mobile.api.data.InitializationStatus
import org.prebid.mobile.api.rendering.BannerView

class NoAdServerActivity : FragmentActivity() {
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
        const val PBS_TIMEOUT_MS = 2000
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.no_ad_server_activity) // Set the XML layout here
        initPrebidSDK()

        // Set the page title
        val titleTextView = findViewById<TextView>(R.id.integrationTitle)
        titleTextView.text = "No ad server"

        // Create a banner ad unit
        // val rectangle = createBannerAdUnit("prebid-ita-banner-320-50", width = 300, height = 50)
        // val leaderboard =
        //   createBannerAdUnit("prebid-demo-banner-multisize", width = 728, height = 90)
        val smallRectangle = createBannerAdUnit("10900-imp-rectangle-300-50", width = 300, height = 50)
        val bigRectangle =
            createBannerAdUnit("10900-imp-rectangle-300-250", width = 300, height = 250)

        val smallRectangleAdContainer = findViewById<FrameLayout>(R.id.rectangleContainer_1)
        smallRectangleAdContainer.addView(smallRectangle)

        val bigRectangleAdContainer = findViewById<FrameLayout>(R.id.rectangleContainer_2)
        bigRectangleAdContainer.addView(bigRectangle)

        // Load the ads initially
        smallRectangle.loadAd()
        bigRectangle.loadAd()

        // Set the "Refresh" button
        val refreshButton = findViewById<Button>(R.id.refreshButton)
        refreshButton.setOnClickListener {
            smallRectangle.loadAd()
            //smallRectangle.setAutoRefreshDelay(30)
            bigRectangle.loadAd()
            //bigRectangle.setAutoRefreshDelay(30)
        }
    }

    private fun initPrebidSDK() {
        PrebidMobile.setPrebidServerAccountId(PBS_ACCOUNT_ID)
        PrebidMobile.setPrebidServerHost(PBS_HOST)
        //PrebidMobile.setCustomStatusEndpoint(PBS_STATUS_ENDPOINT)
        PrebidMobile.setTimeoutMillis(PBS_TIMEOUT_MS)
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
