package com.example.cpexprebiddemo

import android.os.Bundle
import kotlinx.coroutines.*
import android.util.Log
import android.content.ContentValues.TAG
import android.widget.Button
import androidx.fragment.app.FragmentActivity

class SasActivity : FragmentActivity() {

    companion object {
        // Ad Units definition
        private val adUnits = listOf(
            AdUnit("rectangle-1", listOf(300, 50), R.id.rectangleContainer_1),
            AdUnit("rectangle-2", listOf(300, 250), R.id.rectangleContainer_2)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sas_activity) // Set XML Layout

        val sasPackage = SasPackage.initialize()


        // Load the ads initially


        // Set the "Refresh" button
        val refreshButton = findViewById<Button>(R.id.refreshButton)
        refreshButton.setOnClickListener {
            // Implement button behaviour here
            val results = runBlocking { sasPackage.requestAds(adUnits) }
            results.forEach { (adUnit, response) ->
                val logMessage = "Response for ${adUnit.name}: $response"
                Log.d(TAG, logMessage)
            }
        }
    }
}