# CPEx Android Package

Demo showcasing various integrations of ad servers and / with Prebid Mobile SDK for Android platform in Kotlin language.

## Integrations

- SasPackage (SAS + Prebid)
- GAM Rendering API (Google Ad Manager + Prebid)
- No ad server (Prebid only)

## SasPackage

Standalone SAS module with optional Prebid integration. Requires Didomi SDK (CMP).

### Features

- Configurable SAS server
- Prebid with configurable Prebid Server
- Banner and interscroller ad format
- Fully administrated CMP

### Implementation

1. Download module from [this page](https://git.cpex.cz/frontend/cpex-android-package/-/tree/master/sas_package).
2. Import the module to Android Studio project **File > New > Import Module** to project root level (same level as app folder)
3. In app-level **build.gradle** add the module as a dependency

```kotlin
dependencies {
    ... other dependencies
    implementation project(':sas_package')
    }
```

4. Initialize SasPackage object in the first app Activity. Since SasPackage tries to initialize Didomi SDK, that activity has to be of type **FragmentActivity**. You may also initialize Didomi SDK yourself, see [Didomi docs](https://developers.didomi.io/cmp/mobile-sdk/android/setup). Be sure to call `Didomi.getInstance().setupUI(this)` to display CMP UI if it's needed.

**Example of configuration**

```kotlin
import com.cpex.sas_package.SasPackage

class ExampleActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.example_activity)

        SasPackage.initialize(
            context = this,
            instanceUrl = "https://optimics-ads.aimatch.com/optimics",
            enablePrebid = true,
            pbsHost = "https://prebid-server.rubiconproject.com/openrtb2/auction",
            pbsAccountId = "10900-cpex-saswrapper-1",
            cmpVendorId = "c:custom_vendor",
            bidderTable = mapOf(
                "rubicon" to "magnite_hb_app"
                // other bidders ...
            ),
        )
    }
}
```

5. In app activites where ads should be shown
    - import these classes
    ``` kotlin
    import com.cpex.sas_package.AdUnit
    import com.cpex.sas_package.SasPackage
    ```
    - create **FrameLayout** per ad unit in XML layout file
    ```kotlin
    <!-- Ad Container -->
    <FrameLayout
        android:id="@+id/containerName"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_centerHorizontal="true"/>
    ```
    - define **List\<AdUnit>**
    ``` kotlin
    private val adUnits = listOf(
        AdUnit(
            name = "rectangle-1",
            size = listOf(300, 50),
            layoutContainerId = R.id.rectangleContainer_1,
            prebidId = "10900-imp-rectangle-300-50"
            ),
            AdUnit(
            name = "interscroller-1",
            size = listOf(480, 820),
            layoutContainerId = R.id.interscrollerContainer_1,
            scrollViewId = R.id.scrollView
        )
    )
    ```
    - call `SasPackage.requestAds(context: Activity, adUnits: List<AdUnit>)`
    - when refreshing ads or navigating to other activity call first `SasPackage.clearAdUnits(adUnits: List<AdUnit>)`
