package com.example.cpexprebiddemo

import android.content.ContentValues.TAG
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.fragment.app.FragmentActivity
import org.prebid.mobile.AdSize
import org.prebid.mobile.Host
import org.prebid.mobile.PrebidMobile
import org.prebid.mobile.api.data.InitializationStatus
import org.prebid.mobile.api.rendering.BannerView
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerAdView
import org.prebid.mobile.api.rendering.listeners.BannerViewListener
import org.prebid.mobile.eventhandlers.GamBannerEventHandler

data class AdUnit(
    val pbjsConfigId: String,
    val gamAdUnitId: String,
    val adSize: AdSize,
    val layoutContainerId: Int
)

class GamRenderingActivity : FragmentActivity() {


    companion object {
        // Magnite server config
        val PBS_HOST = Host.RUBICON
        const val PBS_ACCOUNT_ID = "10900-mobilewrapper-0"

        // Ad Units definition
        private val adUnits = mapOf(
            "smallRectangle" to AdUnit("10900-imp-rectangle-300-50", "/22631723832/com.example.cpexprebiddemo_small_rectangle", AdSize(300,50), R.id.smallRectangleContainer),
            "bigRectangle" to AdUnit("10900-imp-rectangle-300-250", "/22631723832/com.example.cpexprebiddemo_big_rectangle", AdSize(300,250), R.id.bigRectangleContainer)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.gam_rendering_activity) // Set the XML layout here
        initPrebidSDK()
        MobileAds.initialize(this) {}
        val adUnits = setupAdUnits()

        // Load the ads initially
        adUnits["smallRectangle"]?.loadAd()
        adUnits["bigRectangle"]?.loadAd()

        // Set the "Refresh" button
        val refreshButton = findViewById<Button>(R.id.refreshButton)
        refreshButton.setOnClickListener {
            Log.d("CPEx", "Calling loadAd()")
            adUnits["smallRectangle"]?.loadAd()
            adUnits["bigRectangle"]?.loadAd()
        }
    }

    private fun initPrebidSDK() {
        PrebidMobile.setPrebidServerAccountId(PBS_ACCOUNT_ID)
        PrebidMobile.setPrebidServerHost(PBS_HOST)
        PrebidMobile.initializeSdk(applicationContext) { status ->
            if (status == InitializationStatus.SUCCEEDED) {
                Log.d(TAG, "Prebid: SDK initialized successfully!")
            } else {
                Log.e(TAG, "Prebid: SDK initialization error: $status\n${status.description}")
            }
        }
        PrebidMobile.setShareGeoLocation(false)
    }

    // Put together a single BannerView with GamEventHandler
    private fun createGamBannerAdUnit(pbsConfigId: String, gamAdUnitId : String, adSize: AdSize): BannerView {
        val eventHandler = GamBannerEventHandler(this, gamAdUnitId, adSize)
        return BannerView(this, pbsConfigId, eventHandler)
    }

    // Setup complete ad units to a string map for better ad control
    private fun setupAdUnits(): Map<String, BannerView> {
        val adUnitViews = mutableMapOf<String, BannerView>()

        for ((adUnitName, adUnit) in adUnits) {
            val container = findViewById<FrameLayout>(adUnit.layoutContainerId)
            val gamBanner = createGamBannerAdUnit(adUnit.pbjsConfigId, adUnit.gamAdUnitId, adUnit.adSize)

            container.addView(gamBanner)
            adUnitViews[adUnitName] = gamBanner
        }

        return adUnitViews
    }
}