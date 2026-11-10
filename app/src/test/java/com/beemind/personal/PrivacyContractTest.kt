package com.beemind.personal

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element

/** Regression protection for offline operation and private local storage. */
class PrivacyContractTest {
    private val androidNamespace = "http://schemas.android.com/apk/res/android"
    private fun xml(path: String) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }.newDocumentBuilder().parse(File("src/main/$path"))

    @Test fun manifestOnlyRequestsReminderPermissionsAndNoBackup() {
        val manifest = xml("AndroidManifest.xml")
        val permissions = manifest.getElementsByTagName("uses-permission")
        assertEquals(setOf("android.permission.POST_NOTIFICATIONS", "android.permission.SCHEDULE_EXACT_ALARM", "android.permission.RECEIVE_BOOT_COMPLETED"),
            (0 until permissions.length).map { (permissions.item(it) as Element).getAttributeNS(androidNamespace, "name") }.toSet())
        val app = manifest.getElementsByTagName("application").item(0) as Element
        assertEquals("false", app.getAttributeNS(androidNamespace, "allowBackup"))
        assertEquals("true", app.getAttributeNS(androidNamespace, "supportsRtl"))
    }

    @Test fun databaseExcludedFromCloudBackupAndDeviceTransfer() {
        val rules = xml("res/xml/data_extraction_rules.xml")
        for (section in listOf("cloud-backup", "device-transfer")) {
            val element = rules.getElementsByTagName(section).item(0) as Element
            val exclusions = element.getElementsByTagName("exclude")
            assertTrue((0 until exclusions.length).any {
                val exclusion = exclusions.item(it) as Element
                exclusion.getAttribute("domain") == "database" && exclusion.getAttribute("path") == "."
            })
        }
    }
}
