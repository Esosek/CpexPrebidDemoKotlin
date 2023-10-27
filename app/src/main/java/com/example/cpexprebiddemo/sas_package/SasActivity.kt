package com.example.cpexprebiddemo.sas_package

import android.os.Bundle
import android.widget.Button
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.example.cpexprebiddemo.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class SasActivity : FragmentActivity() {
    companion object {
        // Ad Units definition
        private val adUnits = listOf(
            // Prebid.org testing banner "prebid-ita-banner-320-50"
            AdUnit("rectangle-1", listOf(300, 50), R.id.rectangleContainer_1, "10900-imp-rectangle-300-50"),
            AdUnit("rectangle-2", listOf(300, 250), R.id.rectangleContainer_2, "10900-imp-rectangle-300-250")
        )
    }

    private lateinit var sasPackage: SasPackage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sas_activity) // Set XML Layout

        sasPackage = SasPackage(this)

        // Load the ads initially
        showAds()

        // Set the "Refresh" button
        val refreshButton = findViewById<Button>(R.id.refreshButton)
        refreshButton.setOnClickListener {
            showAds()
        }
    }

    private fun showAds() {
        sasPackage.requestAds(adUnits)
    }
}