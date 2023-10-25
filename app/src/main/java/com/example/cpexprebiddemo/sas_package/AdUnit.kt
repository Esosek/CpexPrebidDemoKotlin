package com.example.cpexprebiddemo.sas_package

data class AdUnit(
    // Custom label
    val name: String,
    // Only single size pair supported, example: [300,250]
    val size: List<Int>,
    // ID of rendering container <FrameLayout>, example: R.id.rectangleContainer_1
    val layoutContainerId: Int,
    // (Optional) Name of the ad unit in Prebid Server, example: 10900-imp-rectangle-300-50
    val prebidId: String = ""
) {
    // Extra targeting information that should be passed to ad sever
    var targeting: Map<String, String> = emptyMap()
        private set
    fun setTargeting(targeting: Map<String, String>) {
        this.targeting = targeting
    }
}