package com.mckimquyen.reader.infrastructure.p2psync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The pairing secrets are bound to this device's Keystore key. If they were backed up, a restored copy on a new
 * phone would be undecryptable ciphertext that still leaves the device, so both backup paths must exclude the file.
 */
class BackupRulesTest {
    private val resXml = File("src/main/res/xml")
    private val excludedPath = "${PairingStore.FILE_NAME}.xml"

    private fun excludes(file: String, parentTag: String?): List<Pair<String, String>> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(resXml, file))
        val scope = if (parentTag == null) doc.documentElement else (doc.getElementsByTagName(parentTag).item(0) as Element)
        val nodes = scope.getElementsByTagName("exclude")
        return (0 until nodes.length).map { i ->
            val e = nodes.item(i) as Element
            e.getAttribute("domain") to e.getAttribute("path")
        }
    }

    @Test fun fullBackupRulesExcludeThePairingStore() {
        assertTrue(excludes("backup_rules.xml", null).contains("sharedpref" to excludedPath))
    }

    @Test fun cloudBackupRulesExcludeThePairingStore() {
        assertTrue(excludes("data_extraction_rules.xml", "cloud-backup").contains("sharedpref" to excludedPath))
    }

    @Test fun deviceTransferRulesExcludeThePairingStore() {
        assertTrue(excludes("data_extraction_rules.xml", "device-transfer").contains("sharedpref" to excludedPath))
    }

    @Test fun theExcludedFileNameMatchesTheOneTheStoreWrites() {
        assertEquals("pairing_store", PairingStore.FILE_NAME)
    }
}
