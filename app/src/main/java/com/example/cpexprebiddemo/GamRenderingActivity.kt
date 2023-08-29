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
import com.google.android.gms.ads.MobileAds
import org.prebid.mobile.eventhandlers.GamBannerEventHandler

data class AdUnit(
    val pbsConfigId: String, // Stored Request ID in PBS example: '10900-imp-rectangle-300-50'
    val gamAdUnitId: String, // GAM AdUnit path example: '/22631723832/com.example.cpexprebiddemo_small_rectangle'
    val adSize: AdSize, // Requested size example: AdSize(300,50)
    val layoutContainerId: Int // ID of <FrameLayout> example: R.id.smallRectangleContainer
)

class GamRenderingActivity : FragmentActivity() {
    companion object {
        // Magnite server config
        val PBS_HOST = Host.RUBICON
        const val PBS_ACCOUNT_ID = "10900-mobilewrapper-0"
        const val PBS_TIMEOUT_MS = 2000

        // Ad Units definition
        private val adUnits = mapOf(
            "smallRectangle" to AdUnit("10900-imp-rectangle-300-50", "/22631723832/com.example.cpexprebiddemo_small_rectangle", AdSize(300,50), R.id.smallRectangleContainer),
            "bigRectangle" to AdUnit("10900-imp-rectangle-300-250", "/22631723832/com.example.cpexprebiddemo_big_rectangle", AdSize(300,250), R.id.bigRectangleContainer)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.gam_rendering_activity) // Set the XML layout
        initPrebidSDK()
        MobileAds.initialize(this) {} // Init Google SDK
        val adUnits = setupAdUnits() // Map<String, BannerView>

        // Load the ads initially
        adUnits["smallRectangle"]?.loadAd()
        adUnits["bigRectangle"]?.loadAd()

        // Set the "Refresh" button
        val refreshButton = findViewById<Button>(R.id.refreshButton)
        refreshButton.setOnClickListener {
            adUnits["smallRectangle"]?.loadAd()
            adUnits["bigRectangle"]?.loadAd()
        }
    }

    private fun initPrebidSDK() {
        PrebidMobile.setPrebidServerAccountId(PBS_ACCOUNT_ID)
        PrebidMobile.setPrebidServerHost(PBS_HOST)
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

    // Configure single BannerView with GamEventHandler
    private fun createGamBannerAdUnit(pbsConfigId: String, gamAdUnitId : String, adSize: AdSize): BannerView {
        val eventHandler = GamBannerEventHandler(this, gamAdUnitId, adSize)
        return BannerView(this, pbsConfigId, eventHandler)
    }

    // Setup connections between UI and adUnits with String keys for better ad control
    private fun setupAdUnits(): Map<String, BannerView> {
        val adUnitViews = mutableMapOf<String, BannerView>()

        for ((adUnitName, adUnit) in adUnits) {
            val container = findViewById<FrameLayout>(adUnit.layoutContainerId)
            val gamBanner = createGamBannerAdUnit(adUnit.pbsConfigId, adUnit.gamAdUnitId, adUnit.adSize)

            container.addView(gamBanner)
            adUnitViews[adUnitName] = gamBanner
        }

        return adUnitViews
    }
}