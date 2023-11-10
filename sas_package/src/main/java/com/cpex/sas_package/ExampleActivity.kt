package com.cpex.sas_package

import android.os.Bundle
import android.widget.Button
import androidx.fragment.app.FragmentActivity
import io.didomi.sdk.Didomi

class ExampleActivity : FragmentActivity() {
    companion object {
        // Ad Units definition
        private val adUnits = listOf(
            AdUnit(
                name = "rectangle-1",
                size = listOf(300, 50),
                layoutContainerId = R.id.rectangleContainer_1,
                prebidId = "10900-imp-rectangle-300-50"
            ),
            AdUnit(
                "rectangle-2",
                listOf(300, 250),
                R.id.rectangleContainer_2,
                "10900-imp-rectangle-300-250"
            ),
            AdUnit(
                "interscroller-1",
                listOf(480, 820),
                R.id.interscrollerContainer_1,
                scrollViewId = R.id.scrollView
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.example_activity) // Set XML Layout

        SasPackage.initialize(
            context = this,
            instanceUrl = "https://optimics-ads.aimatch.com/optimics",
            appDomain = "https%3A%2F%2Fwww.cpex.cz",
            prebidEnabled = true,
            pbsHost = "https://prebid-server.rubiconproject.com/openrtb2/auction",
            pbsAccountId = "10900-cpex-saswrapper-1",
            cmpVendorId = "570",
            bidderTable = mapOf(
                "rubicon" to "magnite_hb_app"
                // other bidders ...
            ),
            interscrollerHeight = .75
        )
        // Show CMP if needed
        Didomi.getInstance().setupUI(this)

        // Load the ads initially
        showAds()

        // Set the "Refresh" button
        val refreshButton = findViewById<Button>(R.id.refreshButton)
        refreshButton.setOnClickListener {
            showAds()
        }
    }

    override fun onDestroy() {
        SasPackage.clearAdUnits(adUnits)
        super.onDestroy()
    }

    private fun showAds() {
        SasPackage.clearAdUnits(adUnits) // Clear past ads and their WebViews
        SasPackage.requestAds(this, adUnits)
    }
}