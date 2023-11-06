package com.cpex.sas_package

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ScrollView
import kotlin.math.roundToInt

/**
 * A singleton helper object responsible for rendering ads into LayoutTree
 * Using WebView, resizing ad parent layout and handling interscroller custom format
 */
object SasRendering {
    private const val logTag = "SasRendering"

    /**
     * Exposed method to render an ad in a specified WebView container.
     *
     * @param context The activity where the ad should be rendered
     * @param adUnit The AdUnit data class containing ad information
     * @param response The HTML creative to be rendered
     */
    fun renderAd(context: Activity, adUnit: AdUnit, response: String) {
        val adContainer = context.findViewById<FrameLayout>(adUnit.layoutContainerId)

        // Wraps the HTML creative to control rendered element
        // Resizes the container to reflect format (banner, interscroller)
        val creative = prepareCreative(response, adUnit, adContainer, context)
        val webView = createWebView(context)
        adContainer.addView(webView)
        adUnit.setWebView(webView) // Store reference to AdUnit for clearing purposes

        if (adUnit.size[0] == 480 && adUnit.size[1] == 820) {
            handleInterscroller(adUnit, webView, adContainer, context)
        }
        // Load the HTML content into the WebView
        webView.loadData(creative, "text/html", "UTF-8")
    }

    /** Creates and configures the WebView, handling ad clicks
     * @return WebView */
    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(context: Activity): WebView {
        val webView = WebView(context)
        // Configure WebView settings
        webView.settings.javaScriptEnabled = true
        // Disable scrollbars
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false

        webView.webViewClient = object : WebViewClient() {
            // By default click url is opened inside WebView
            // Force opening browser instead
            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                view?.context?.startActivity(intent)
                return true
            }
        }
        return webView
    }

    /** Wraps the HTML response with basic styling and resizes adContainer to fitWidth
     * - Sets interscroller View %, defaults to 50 %
     * @param response raw HTML creative from ad server
     * @param adUnit AdUnit used for creative size and format detection
     * @param container adContainer<FrameLayout> where creative should be rendered in layout tree
     * @return Finalized HTML creative as String */
    private fun prepareCreative(
        response: String, adUnit: AdUnit, container: FrameLayout, context: Activity
    ): String {
        val displayMetrics = context.resources.displayMetrics
        val density = displayMetrics.density
        val creativeWidth = (adUnit.size[0] * density).roundToInt()
        val sizeRatio = getCreativeResizeRatio(adUnit.size[0] * density, container)

        val cssCreativeWidth = (adUnit.size[0] * sizeRatio).roundToInt()
        var creativeHeight = (adUnit.size[1] * density * sizeRatio).roundToInt()

        val styles = """
        <style>
          body {
            margin: 0;
            overflow: hidden;
          }
          .cpex-wrapper {
            width: ${cssCreativeWidth}px;
          }
          .cpex-wrapper img {
            max-width: 100%;
            height: auto;
          }
        </style>
    """.trimIndent()

        val creative = """
        <head>$styles</head>
        <body>
          <div class="cpex-wrapper">
            $response
          </div>
        </body>
    """.trimIndent()

        if (adUnit.size[0] == 480 && adUnit.size[1] == 820) {
            Log.d(logTag, "Rendering interscroller format in AdUnit: ${adUnit.name}")
            creativeHeight = (displayMetrics.heightPixels * SasPackage.interscrollerHeight).roundToInt()
        }

        val layoutParams = container.layoutParams
        layoutParams.width = creativeWidth
        layoutParams.height = creativeHeight
        container.layoutParams = layoutParams
        return creative
    }

    /** Detects if creative was resized to fit and calculates the resize ratio
     *  @param creativeWidth Actual creative width after resizing to fit
     * @param container adContainer<FrameLayout> where the creative will be rendered
     * @return Creative resize ratio as Float */
    private fun getCreativeResizeRatio(creativeWidth: Float, container: FrameLayout): Float {
        if (creativeWidth > container.width) {
            return container.width.toFloat() / creativeWidth
        }
        return 1.0f
    }

    /** Handles offset and scrolling to interscroller ad
     * @param adUnit AdUnit reference for size and ScrollView settings
     * @param webView WebView in which the ad is rendered
     * @param adContainer adContainer<FrameLayout> in which the WebView is added */
    private fun handleInterscroller(
        adUnit: AdUnit, webView: WebView, adContainer: FrameLayout, context: Activity
    ) {
        if (adUnit.scrollViewId == null) {
            Log.e(logTag, "SAS Package: Interscroller is missing reference to ScrollView, skipping")
        } else {
            val scrollView = context.findViewById<ScrollView>(adUnit.scrollViewId)
            val density = context.resources.displayMetrics.density
            val resizeRatio = getCreativeResizeRatio(adUnit.size[0] * density, adContainer)
            val creativeHeight = (adUnit.size[1] * density * resizeRatio).roundToInt()
            webView.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, creativeHeight
            )

            val offsetToCenter = context.resources.displayMetrics.heightPixels - creativeHeight
            val adjOffset = (offsetToCenter * .75).toFloat()

            // Initial offset
            val adContainerLocation = IntArray(2)
            webView.y = -adContainerLocation[1].toFloat() + adjOffset

            scrollView.setOnScrollChangeListener { _, _, _, _, _ ->
                adContainer.getLocationOnScreen(adContainerLocation)
                webView.y = -adContainerLocation[1].toFloat() + adjOffset
            }
        }
    }
}