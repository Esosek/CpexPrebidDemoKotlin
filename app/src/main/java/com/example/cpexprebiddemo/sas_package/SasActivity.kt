package com.example.cpexprebiddemo.sas_package

import android.os.Bundle
import android.widget.Button
import androidx.fragment.app.FragmentActivity
import com.example.cpexprebiddemo.R
import org.prebid.mobile.Host

class SasActivity : FragmentActivity() {
    companion object {
        // Ad Units definition
        private val adUnits = listOf(
            // Prebid.org testing banner "prebid-ita-banner-320-50"
            AdUnit("rectangle-1", listOf(300, 50), R.id.rectangleContainer_1, "10900-imp-rectangle-300-50"),
            AdUnit("rectangle-2", listOf(300, 250), R.id.rectangleContainer_2, "10900-imp-rectangle-300-250")
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sas_activity) // Set XML Layout

        SasPackage.initialize(
            context = this,
            instanceUrl = "https://optimics-ads.aimatch.com/optimics",
            site = "com.example.cpexprebiddemo",
            enablePrebid = true,
            pbsHost = Host.RUBICON,
            pbsAccountId = "10900-mobilewrapper-0",
            pbsTimeoutMs = 1000
        )
        // Prebid.org testing server config
//        pbsHost =
//            Host.createCustomHost("https://prebid-server-test-j.prebid.org/openrtb2/auction")
//        private const val pbsAccountId = "0689a263-318d-448b-a3d4-b02e8a709d9d"
//        private const val pbsTimeoutMs = 1000

        // Load the ads initially
        showAds()

        // Set the "Refresh" button
        val refreshButton = findViewById<Button>(R.id.refreshButton)
        refreshButton.setOnClickListener {
            showAds()
        }
    }

    private fun showAds() {
        SasPackage.requestAds(adUnits)
    }
}