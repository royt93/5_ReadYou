package com.mckimquyen.reader.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * minSdk is 26. These theme attributes exist only from a later API level; on an older phone they are ignored at run time, but lint reports
 * them as NewApi errors unless the item says which API it is for (`tools:targetApi`). REL-03 fixed 12 such errors; lint only runs fully with a
 * patched SDK file, so nothing else stops someone from adding the attribute again without the marker. This pins the three known ones.
 */
class ThemeApiLevelAttributesTest {
    private val res = File("src/main/res")
    private val toolsNs = "http://schemas.android.com/tools"

    /** attribute name -> the targetApi value lint expects for it. */
    private val required = mapOf(
        "android:windowLightNavigationBar" to "o_mr1",
        "android:enforceNavigationBarContrast" to "q",
        "android:enforceStatusBarContrast" to "q",
    )

    private val themeFiles = listOf("values/themes.xml", "values-night/themes.xml")

    private fun items(path: String): List<Element> {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val doc = factory.newDocumentBuilder().parse(File(res, path))
        val nodes = doc.getElementsByTagName("item")
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun gated(path: String) = items(path).filter { it.getAttribute("name") in required }

    @Test
    fun theThemeFilesStillContainTheGatedAttributes_soThisTestIsNotVacuous() {
        themeFiles.forEach { assertTrue("$it has no gated attribute left to check", gated(it).isNotEmpty()) }
        assertEquals("values/themes.xml", 8, gated("values/themes.xml").size)
        assertEquals("values-night/themes.xml", 4, gated("values-night/themes.xml").size)
    }

    @Test
    fun everyGatedAttributeCarriesItsTargetApiMarker() {
        val bad = themeFiles.flatMap { path ->
            gated(path).mapNotNull { item ->
                val name = item.getAttribute("name")
                val marker = item.getAttributeNS(toolsNs, "targetApi")
                if (marker == required.getValue(name)) null else "$path: $name has tools:targetApi='$marker', expected '${required.getValue(name)}'"
            }
        }
        assertEquals(emptyList<String>(), bad)
    }

    @Test
    fun theFilesDeclareTheToolsNamespace() {
        themeFiles.forEach { path ->
            val text = File(res, path).readText()
            assertTrue("$path must declare xmlns:tools", text.contains("xmlns:tools=\"$toolsNs\""))
        }
    }
}
