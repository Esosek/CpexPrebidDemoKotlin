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

class GamRenderingActivity : FragmentActivity() {

    // Magnite server config
    companion object {
        val PBS_HOST = Host.RUBICON
        const val PBS_ACCOUNT_ID = "10900-mobilewrapper-0"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.gam_rendering_activity) // Set the XML layout here
        initPrebidSDK()
        MobileAds.initialize(this) {}


        val smallRectangleEventHandler = GamBannerEventHandler(this, "/22631723832/com.example.cpexprebiddemo_small_rectangle", AdSize(300,50))
        val smallRectangle = createGamBannerAdUnit("10900-imp-rectangle-300-50", smallRectangleEventHandler)
        val smallRectangleAdContainer = findViewById<FrameLayout>(R.id.smallRectangleContainer)
        smallRectangleAdContainer.addView(smallRectangle)

        val bigRectangleEventHandler = GamBannerEventHandler(this, "/22631723832/com.example.cpexprebiddemo_big_rectangle", AdSize(300,250))
        val bigRectangle = createGamBannerAdUnit("10900-imp-rectangle-300-250", bigRectangleEventHandler)
        val bigRectangleAdContainer = findViewById<FrameLayout>(R.id.bigRectangleContainer)
        bigRectangleAdContainer.addView(bigRectangle)

        // Load the ads initially
        smallRectangle.loadAd()
        bigRectangle.loadAd()

        // Set the "Refresh" button
        val refreshButton = findViewById<Button>(R.id.refreshButton)
        refreshButton.setOnClickListener {
            Log.d("CPEx", "Calling loadAd()")
            smallRectangle.loadAd()
            bigRectangle.loadAd()
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

    private fun createGamBannerAdUnit(configId: String, eventHandler: GamBannerEventHandler): BannerView {
        return BannerView(this, configId, eventHandler)
    }
}