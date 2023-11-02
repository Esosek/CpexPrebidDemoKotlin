package com.cpex.sas_package

import android.webkit.WebView
import android.widget.FrameLayout

data class AdUnit(
    /** Custom label */
    val name: String,
    /** Only single size pair supported, example: [300,250] */
    val size: List<Int>,
    /** ID of rendering container <FrameLayout>, example: R.id.rectangleContainer_1 */
    val layoutContainerId: Int,
    /** (Optional) Name of the ad unit in Prebid Server, example: 10900-imp-rectangle-300-50 */
    val prebidId: String = "",
    /** (Optional) ID of main content ScrollView,
     * required for interscroller scrolling effect */
    val scrollViewId: Int? = null
) {
    /** Extra targeting information that should be passed to ad sever */
    var targeting: Map<String, String> = emptyMap()
        private set
    /** WebView associated with ad from this AdUnit */
    private var webView: WebView? = null
    fun setTargeting(targeting: Map<String, String>) {
        this.targeting = targeting
    }

    // Store reference to assigned WebView for clearing
    fun setWebView(webView: WebView) {
        this.webView = webView
    }
    fun clearWebView() {
        if(webView == null) {
            return
        }
        val parent = webView!!.parent as? FrameLayout
        parent?.removeView(webView)
        webView!!.clearCache(true)
        webView!!.clearHistory()
        webView!!.destroy()
        webView = null
    }
}