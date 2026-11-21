package com.mckimquyen.reader.infrastructure.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The WebDAV password sits in plain text in the `webdav_backup_prefs` SharedPreferences file. A cloud backup or a device transfer would
 * carry it off the phone, so all three backup paths must exclude that file. The file name is read from the real source so a rename is caught.
 */
class WebDavBackupRulesTest {
    private val resXml = File("src/main/res/xml")

    private val prefsFileName: String by lazy {
        val source = File("src/main/java/com/mckimquyen/reader/infrastructure/backup/WebDavBackupManager.kt").readText()
        Regex("""PREFS_NAME\s*=\s*"([^"]+)"""").find(source)?.groupValues?.get(1)
            ?: error("PREFS_NAME not found in WebDavBackupManager.kt")
    }
    private val excluded get() = "sharedpref" to "$prefsFileName.xml"

    private fun excludes(file: String, parentTag: String?): List<Pair<String, String>> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(resXml, file))
        val scope = if (parentTag == null) doc.documentElement else (doc.getElementsByTagName(parentTag).item(0) as Element)
        val nodes = scope.getElementsByTagName("exclude")
        return (0 until nodes.length).map { i ->
            val e = nodes.item(i) as Element
            e.getAttribute("domain") to e.getAttribute("path")
        }
    }

    @Test fun theFileNameReadFromTheSourceIsTheOneTheManagerWrites() = assertEquals("webdav_backup_prefs", prefsFileName)

    @Test fun fullBackupRulesExcludeTheWebDavPrefs() = assertTrue(excludes("backup_rules.xml", null).contains(excluded))

    @Test fun cloudBackupRulesExcludeTheWebDavPrefs() = assertTrue(excludes("data_extraction_rules.xml", "cloud-backup").contains(excluded))

    @Test fun deviceTransferRulesExcludeTheWebDavPrefs() = assertTrue(excludes("data_extraction_rules.xml", "device-transfer").contains(excluded))

    @Test fun thePairingStoreIsStillExcluded() = assertTrue(excludes("backup_rules.xml", null).contains("sharedpref" to "pairing_store.xml"))
}
