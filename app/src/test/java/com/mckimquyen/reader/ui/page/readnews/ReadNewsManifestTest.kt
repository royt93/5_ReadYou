package com.mckimquyen.reader.ui.page.readnews

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Kiểm tra tĩnh manifest và shortcuts.xml (không có adb/Assistant thì đây là phần xác thực được). */
class ReadNewsManifestTest {

    private fun parse(path: String): Document =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
            .newDocumentBuilder().parse(File(path))

    private val manifest by lazy { parse("src/main/AndroidManifest.xml") }
    private val shortcuts by lazy { parse("src/main/res/xml/shortcuts.xml") }

    private fun activity(name: String): Element? {
        val nodes = manifest.getElementsByTagName("activity")
        for (i in 0 until nodes.length) {
            val e = nodes.item(i) as Element
            if (e.getAttribute("android:name") == name) return e
        }
        return null
    }

    private fun Element.children(tag: String): List<Element> =
        getElementsByTagName(tag).let { list -> (0 until list.length).map { list.item(it) as Element } }

    @Test
    fun readNewsActivity_isExportedTransparentAndDeepLinked() {
        val a = activity(".ui.page.readnews.ReadNewsActivity")
        assertNotNull("manifest phải khai báo ReadNewsActivity", a)
        a!!
        assertEquals("true", a.getAttribute("android:exported"))
        assertEquals("@style/Theme.Reader.Transparent", a.getAttribute("android:theme"))
        assertEquals("true", a.getAttribute("android:excludeFromRecents"))
        assertEquals("true", a.getAttribute("android:noHistory"))

        val filter = a.children("intent-filter").single()
        assertEquals(listOf("android.intent.action.VIEW"), filter.children("action").map { it.getAttribute("android:name") })
        assertEquals(
            setOf("android.intent.category.DEFAULT", "android.intent.category.BROWSABLE"),
            filter.children("category").map { it.getAttribute("android:name") }.toSet(),
        )
        val data = filter.children("data").single()
        assertEquals("rsscathub", data.getAttribute("android:scheme"))
        assertEquals("read-news", data.getAttribute("android:host"))
    }

    @Test
    fun splashActivity_referencesShortcutsXml() {
        val splash = activity(".infrastructure.android.SplashActivity")
        assertNotNull(splash)
        val meta = splash!!.children("meta-data").singleOrNull { it.getAttribute("android:name") == "android.app.shortcuts" }
        assertNotNull("SplashActivity phải trỏ tới shortcuts.xml", meta)
        assertEquals("@xml/shortcuts", meta!!.getAttribute("android:resource"))
    }

    @Test
    fun manifest_requestsNoNewPermissions() {
        val nodes = manifest.getElementsByTagName("uses-permission")
        val names = (0 until nodes.length).map { (nodes.item(it) as Element).getAttribute("android:name") }.toSet()
        assertEquals(
            setOf(
                "android.permission.INTERNET",
                "android.permission.ACCESS_NETWORK_STATE",
                "android.permission.POST_NOTIFICATIONS",
                "android.permission.WAKE_LOCK",
                "android.permission.FOREGROUND_SERVICE",
                "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
                "com.google.android.gms.permission.AD_ID",
            ),
            names,
        )
    }

    @Test
    fun shortcut_pointsToTheDeepLinkAndBindsGetThing() {
        val shortcut = shortcuts.getElementsByTagName("shortcut").let { list ->
            (0 until list.length).map { list.item(it) as Element }.single { it.getAttribute("android:shortcutId") == "read_latest_news" }
        }
        assertEquals("true", shortcut.getAttribute("android:enabled"))
        assertTrue(shortcut.getAttribute("android:shortcutShortLabel").startsWith("@string/"))
        assertTrue(shortcut.getAttribute("android:shortcutLongLabel").startsWith("@string/"))

        val intent = shortcut.children("intent").single()
        assertEquals("android.intent.action.VIEW", intent.getAttribute("android:action"))
        assertEquals("rsscathub://read-news", intent.getAttribute("android:data"))
        assertEquals("com.mckimquyen.reader", intent.getAttribute("android:targetPackage"))
        assertEquals("com.mckimquyen.reader.ui.page.readnews.ReadNewsActivity", intent.getAttribute("android:targetClass"))

        val binding = shortcut.children("capability-binding").single()
        assertEquals("actions.intent.GET_THING", binding.getAttribute("android:key"))
        val param = binding.children("parameter-binding").single()
        assertEquals("thing.name", param.getAttribute("android:key"))
        assertEquals("@array/read_news_thing_names", param.getAttribute("android:value"))
    }

    @Test
    fun capability_declaresGetThingWithTheSameDeepLink() {
        val capability = shortcuts.getElementsByTagName("capability").let { list ->
            (0 until list.length).map { list.item(it) as Element }.single()
        }
        assertEquals("actions.intent.GET_THING", capability.getAttribute("android:name"))
        val intent = capability.children("intent").single()
        assertEquals("rsscathub://read-news", intent.getAttribute("android:data"))
        assertEquals("com.mckimquyen.reader.ui.page.readnews.ReadNewsActivity", intent.getAttribute("android:targetClass"))
    }
}
