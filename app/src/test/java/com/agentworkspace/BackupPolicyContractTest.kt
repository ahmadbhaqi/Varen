package com.agentworkspace

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupPolicyContractTest {
    @Test
    fun databaseAndCredentialVaultAreExcludedFromLegacyBackup() {
        val rules = projectFile("app/src/main/res/xml/backup_rules.xml").readText()

        assertTrue(rules.contains("<exclude domain=\"database\" path=\".\""))
        assertTrue(
            rules.contains(
                "<exclude domain=\"sharedpref\" path=\"agentworkspace_credential_vault.xml\"",
            ),
        )
    }

    @Test
    fun databaseAndCredentialVaultAreExcludedFromCloudAndDeviceTransfer() {
        val rules = projectFile("app/src/main/res/xml/data_extraction_rules.xml").readText()

        assertEqualsCount(2, rules, "<exclude domain=\"database\" path=\".\"")
        assertEqualsCount(
            2,
            rules,
            "<exclude domain=\"sharedpref\" path=\"agentworkspace_credential_vault.xml\"",
        )
    }

    private fun assertEqualsCount(expected: Int, text: String, value: String) {
        assertTrue("Expected $expected occurrences of $value", text.split(value).size - 1 == expected)
    }

    private fun projectFile(relativePath: String): File {
        val startDirectory = checkNotNull(System.getProperty("user.dir"))
        var directory = File(startDirectory).absoluteFile
        repeat(6) {
            val candidate = File(directory, relativePath)
            if (candidate.exists()) return candidate
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate $relativePath from $startDirectory")
    }
}
