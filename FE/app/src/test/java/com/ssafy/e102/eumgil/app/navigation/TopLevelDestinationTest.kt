package com.ssafy.e102.eumgil.app.navigation

import com.ssafy.e102.eumgil.R
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test

class TopLevelDestinationTest {
    @Test
    fun `top level destinations match MAP-01 bottom tab routes in design order`() {
        val actualRoutes = TopLevelDestination.entries.map { destination -> destination.route.route }

        assertEquals(
            listOf("map", "saved_route", "report", "my_page"),
            actualRoutes,
        )
    }

    @Test
    fun `report tab navigates through ReportRoute Report`() {
        val reportDestination =
            TopLevelDestination.entries.singleOrNull { destination ->
                destination.route.route == ReportRoute.Report.route
            }

        assertNotNull(reportDestination)
        assertSame(ReportRoute.Report, reportDestination?.route)
    }

    @Test
    fun `top level tab labels match latest MAP-01 design`() {
        val stringValues = loadStringValues()

        assertEquals("홈", stringValues["route_map"])
        assertEquals("북마크", stringValues["route_saved_route"])
        assertEquals("제보", stringValues["route_report"])
        assertEquals("마이페이지", stringValues["route_my_page"])
    }

    @Test
    fun `top level tabs expose matching icons`() {
        val actualIcons = TopLevelDestination.entries.map { destination -> destination.iconRes }

        assertEquals(
            listOf(
                R.drawable.ic_nav_home,
                R.drawable.ic_nav_bookmark_outline,
                R.drawable.ic_nav_report,
                R.drawable.ic_nav_mypage,
            ),
            actualIcons,
        )
    }

    @Test
    fun `top level tabs expose icon sizes aligned with MAP-01 emphasis`() {
        val iconSizeGetter = TopLevelDestination::class.java.getDeclaredMethod("getIconSizeDp")
        val actualIconSizes =
            TopLevelDestination.entries.map { destination ->
                iconSizeGetter.invoke(destination) as Int
            }

        assertEquals(
            listOf(30, 30, 30, 30),
            actualIconSizes,
        )
    }

    private fun loadStringValues(): Map<String, String> {
        val file = File("src/main/res/values/strings.xml")
        val document =
            DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .parse(file)
        val nodes = document.getElementsByTagName("string")

        return buildMap {
            for (index in 0 until nodes.length) {
                val node = nodes.item(index)
                val name = node.attributes.getNamedItem("name").nodeValue
                put(name, node.textContent)
            }
        }
    }
}
