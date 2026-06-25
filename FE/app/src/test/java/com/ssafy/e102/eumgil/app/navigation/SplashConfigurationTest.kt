package com.ssafy.e102.eumgil.app.navigation

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class SplashConfigurationTest {
    @Test
    fun `splash image resources use valid Android resource names`() {
        val resourceFiles =
            File("src/main/res")
                .walkTopDown()
                .filter { file -> file.isFile && file.extension == "png" }
                .toList()
        val invalidResourceFiles =
            resourceFiles.filterNot { file ->
                RESOURCE_FILE_NAME.matches(file.name)
            }
        val resourcePaths = resourceFiles.map { file -> file.invariantSeparatorsPath }

        assertTrue(
            "Missing app logo resource.",
            resourcePaths.any { path -> path.endsWith("drawable/app_logo.png") },
        )
        assertTrue(
            "Missing full-screen splash illustration resource.",
            resourcePaths.any { path -> path.endsWith("drawable-nodpi/splash_illustration.png") },
        )
        assertTrue(
            "Missing transparent platform splash icon resource.",
            File("src/main/res/drawable/splash_transparent_icon.xml").exists(),
        )
        assertTrue(
            "Invalid Android resource names: ${invalidResourceFiles.joinToString { it.name }}",
            invalidResourceFiles.isEmpty(),
        )
    }

    @Test
    fun `app module declares AndroidX splash screen dependency`() {
        val buildFile = File("build.gradle.kts").readText()

        assertTrue(
            "AndroidX splash screen dependency must be declared.",
            buildFile.contains("androidx.core:core-splashscreen"),
        )
    }

    @Test
    fun `main activity starts with splash theme`() {
        val manifest = parseXml(File("src/main/AndroidManifest.xml"))
        val application =
            manifest
                .getElementsByTagName("application")
                .asSequence()
                .mapNotNull { node -> node as? Element }
                .single()
        val activity =
            manifest
                .getElementsByTagName("activity")
                .asSequence()
                .mapNotNull { node -> node as? Element }
                .single { element ->
                    element.getAttribute("android:name") == ".app.MainActivity"
                }

        assertEquals("@drawable/app_logo", application.getAttribute("android:icon"))
        assertEquals("@drawable/app_logo", application.getAttribute("android:roundIcon"))
        assertEquals("@style/Theme.BusanEumgil.Splash", activity.getAttribute("android:theme"))
    }

    @Test
    fun `splash theme hides platform icon then restores app theme`() {
        val themeStyle = loadStyle(name = "Theme.BusanEumgil.Splash")
        val themeItems = themeStyle.items

        assertEquals("Theme.SplashScreen", themeStyle.parent)
        assertEquals("@drawable/splash_illustration", themeItems["android:windowBackground"])
        assertEquals("@color/splash_background", themeItems["windowSplashScreenBackground"])
        assertEquals("@drawable/splash_transparent_icon", themeItems["windowSplashScreenAnimatedIcon"])
        assertEquals("@style/Theme.BusanEumgil", themeItems["postSplashScreenTheme"])
    }

    @Test
    fun `platform splash background stays neutral when full-screen image cannot render there`() {
        val colors = loadColors()

        assertEquals(
            "System splash background should stay neutral because Android 12 platform splash backgrounds cannot render the full-screen illustration.",
            "#FFFFFF",
            colors["splash_background"],
        )
    }

    @Test
    fun `app theme keeps splash illustration behind startup rendering`() {
        val themeStyle = loadStyle(name = "Theme.BusanEumgil")
        val themeItems = themeStyle.items

        assertEquals("android:Theme.Material.Light.NoActionBar", themeStyle.parent)
        assertEquals("@drawable/splash_illustration", themeItems["android:windowBackground"])
    }

    @Test
    fun `main activity installs platform splash screen before content`() {
        val mainActivity = File("src/main/java/com/ssafy/e102/eumgil/app/MainActivity.kt").readText()
        val installCallIndex = mainActivity.indexOf("installSplashScreen()")
        val setContentIndex = mainActivity.indexOf("setContent {")

        assertTrue("MainActivity must call installSplashScreen().", installCallIndex >= 0)
        assertTrue(
            "Splash screen must be installed before Compose content is set.",
            installCallIndex in 0 until setContentIndex,
        )
    }

    @Test
    fun `app nav host uses compose safe loading surface while startup route resolves`() {
        val appNavHost = File("src/main/java/com/ssafy/e102/eumgil/app/navigation/AppNavHost.kt").readText()
        val loadingScreenSection =
            appNavHost
                .substringAfter("private fun AppEntryLoadingScreen(")
                .substringBefore("\n}")

        assertTrue(
            "AppNavHost should keep rendering a startup loading screen while the startup route is loading.",
            appNavHost.contains("AppEntryLoadingScreen(modifier = modifier)"),
        )
        assertTrue(
            "Compose startup loading should decode the splash illustration through BitmapFactory.",
            appNavHost.contains("BitmapFactory") &&
                appNavHost.contains("R.drawable.splash_illustration") &&
                appNavHost.contains("asImageBitmap()"),
        )
        assertTrue(
            "Compose startup loading should render the splash illustration with the same crop behavior.",
            loadingScreenSection.contains("Image(") &&
                loadingScreenSection.contains("bitmap = splashImage") &&
                loadingScreenSection.contains("ContentScale.Crop"),
        )
        assertTrue(
            "Compose startup loading should keep a fallback loading state.",
            loadingScreenSection.contains("CircularProgressIndicator(") &&
                loadingScreenSection.contains("R.string.app_entry_loading"),
        )
        assertTrue(
            "Compose startup loading should expose the loading copy in fallback.",
            loadingScreenSection.contains("R.string.app_entry_loading"),
        )
        assertFalse(
            "Compose startup loading must not use painterResource; resource aliases can resolve through unsupported drawable paths on some devices.",
            loadingScreenSection.contains("painterResource("),
        )
        assertFalse(
            "Compose startup loading should avoid loading the app logo through painterResource for the same startup crash class.",
            loadingScreenSection.contains("R.drawable.app_logo"),
        )
    }

    @Test
    fun `startup splash illustration does not announce app name to talkback`() {
        val appNavHost = File("src/main/java/com/ssafy/e102/eumgil/app/navigation/AppNavHost.kt").readText()

        assertFalse(
            "Startup splash illustration is decorative and should not make TalkBack announce the app name.",
            appNavHost.contains("contentDescription = stringResource(id = R.string.app_name)"),
        )
    }

    @Test
    fun `app nav host observes auth state and redirects to login when session is cleared`() {
        val appNavHost = File("src/main/java/com/ssafy/e102/eumgil/app/navigation/AppNavHost.kt").readText()

        assertTrue(
            "AppNavHost should observe auth gate updates after startup.",
            appNavHost.contains("authSessionRepository.observeAuthGateState()"),
        )
        assertTrue(
            "AppNavHost should redirect back to the login route when the authenticated session disappears.",
            appNavHost.contains("navController.navigate(AuthRoute.Login.route)"),
        )
    }

    @Test
    fun `app container defers database backed startup dependencies until needed`() {
        val appContainer = File("src/main/java/com/ssafy/e102/eumgil/app/AppContainer.kt").readText()
        val settingsRepository = File("src/main/java/com/ssafy/e102/eumgil/data/repository/SettingsRepository.kt").readText()

        assertTrue(
            "AppContainer should lazily initialize init settings to avoid opening startup storage before the first frame.",
            appContainer.contains("private val initSettingsLocalDataSource by lazy(LazyThreadSafetyMode.NONE)"),
        )
        assertTrue(
            "Bookmark repository should stay lazy until the feature is opened.",
            appContainer.contains("val bookmarkRepository: BookmarkRepository by lazy(LazyThreadSafetyMode.NONE)"),
        )
        assertTrue(
            "Report repository should stay lazy until reporting flows are used.",
            appContainer.contains("val reportRepository: ReportRepository by lazy(LazyThreadSafetyMode.NONE)"),
        )
        assertTrue(
            "Settings repository should receive init settings datasource without opening Room-backed repositories.",
            settingsRepository.contains("private val initSettingsLocalDataSource: InitSettingsLocalDataSource"),
        )
    }

    @Test
    fun `app container does not seed bookmark mocks in real data flows`() {
        val appContainer = File("src/main/java/com/ssafy/e102/eumgil/app/AppContainer.kt").readText()

        assertFalse(
            "Bookmark screens should load server or local cache data, not debug fixture bookmarks.",
            appContainer.contains("MockBookmarkFixtures.defaultBookmarks"),
        )
    }

    private fun loadColors(): Map<String, String> {
        val document = parseXml(File("src/main/res/values/colors.xml"))

        return document
            .getElementsByTagName("color")
            .asSequence()
            .mapNotNull { node -> node as? Element }
            .associate { element ->
                element.getAttribute("name") to element.textContent.trim()
            }
    }

    private fun loadStyle(name: String): StyleDefinition {
        val document = parseXml(File("src/main/res/values/themes.xml"))
        val style =
            document
                .getElementsByTagName("style")
                .asSequence()
                .mapNotNull { node -> node as? Element }
                .single { element -> element.getAttribute("name") == name }
        val items =
            style
                .getElementsByTagName("item")
                .asSequence()
                .mapNotNull { node -> node as? Element }
                .associate { element ->
                    element.getAttribute("name") to element.textContent
                }

        return StyleDefinition(
            parent = style.getAttribute("parent"),
            items = items,
        )
    }

    private fun parseXml(file: File) =
        DocumentBuilderFactory
            .newInstance()
            .newDocumentBuilder()
            .parse(file)

    private fun org.w3c.dom.NodeList.asSequence(): Sequence<org.w3c.dom.Node> =
        sequence {
            for (index in 0 until length) {
                yield(item(index))
            }
        }

    private data class StyleDefinition(
        val parent: String,
        val items: Map<String, String>,
    )

    private companion object {
        val RESOURCE_FILE_NAME = Regex("[a-z0-9_]+\\.png")
    }
}
