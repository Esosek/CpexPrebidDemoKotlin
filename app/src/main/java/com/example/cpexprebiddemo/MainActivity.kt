package com.example.cpexprebiddemo

import android.content.ContentValues.TAG
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import androidx.fragment.app.FragmentActivity
import com.example.cpexprebiddemo.sas_package.SasActivity
import io.didomi.sdk.Didomi
import io.didomi.sdk.DidomiInitializeParameters

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // Set the XML layout here
        initDidomiSDK()

        // Display CMP UI
        // Always call it, checks internally if it's required
        Didomi.getInstance().setupUI(this)

        // Display and handle dropdown that loads different adapters
        spinnerHandler()

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

    private fun spinnerHandler() {
        val moduleSpinner = findViewById<Spinner>(R.id.moduleSpinner)
        val moduleNames = arrayOf("None", "No ad server", "GAM Rendering API", "SAS Package") // Add more module names
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, moduleNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        moduleSpinner.adapter = adapter

        moduleSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (position != 0) { // Check if "None" is not selected
                    when (moduleNames[position]) {
                        "No ad server" -> startActivity(
                            Intent(
                                this@MainActivity,
                                NoAdServerActivity::class.java
                            )
                        )
                        "GAM Rendering API" -> startActivity(Intent(this@MainActivity, GamRenderingActivity::class.java))
                        "SAS Package" -> startActivity(Intent(this@MainActivity, SasActivity::class.java))

                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Handle case where nothing is selected
            }
        }
    }
}
