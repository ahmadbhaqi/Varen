package com.agentworkspace.data.security

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialVaultPersistenceContractTest {
    @Test
    fun `encrypted payload is durably committed before migration can verify it`() {
        val source = projectFile(
            "app/src/main/java/com/agentworkspace/data/security/CredentialVault.kt",
        ).readText()
        val putBody = source
            .substringAfter("override fun put")
            .substringBefore("override fun get")

        assertTrue(putBody.contains(".commit()"))
        assertFalse(putBody.contains(".apply()"))
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
