package com.agentworkspace.data.security

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialMigratorTest {
    @Test
    fun `migration writes verifies and only then replaces plaintext`() = runTest {
        val records = listOf(
            LegacyCredential("one", CredentialField.API_KEY, "secret-one"),
            LegacyCredential("two", CredentialField.REFRESH_TOKEN, "secret-two"),
        )
        val store = FakeCredentialStore()
        val source = FakeMigrationSource(records)

        val report = CredentialMigrator(store, source, CredentialMutationCoordinator()).execute()

        assertEquals(CredentialMigrationReport(migrated = 2, failed = 0), report)
        assertEquals(records, source.replaced)
        assertTrue(source.remaining.isEmpty())
    }

    @Test
    fun `verification mismatch preserves plaintext for a later retry`() = runTest {
        val record = LegacyCredential("one", CredentialField.ACCESS_TOKEN, "secret")
        val store = FakeCredentialStore(readBackOverride = "different")
        val source = FakeMigrationSource(listOf(record))

        val report = CredentialMigrator(store, source, CredentialMutationCoordinator()).execute()

        assertEquals(CredentialMigrationReport(migrated = 0, failed = 1), report)
        assertTrue(source.replaced.isEmpty())
        assertEquals(listOf(record), source.remaining)
    }

    @Test
    fun `marker replacement failure leaves the source record untouched`() = runTest {
        val record = LegacyCredential("one", CredentialField.API_KEY, "secret")
        val store = FakeCredentialStore()
        val source = FakeMigrationSource(listOf(record), failReplacement = true)

        val report = CredentialMigrator(store, source, CredentialMutationCoordinator()).execute()

        assertEquals(CredentialMigrationReport(migrated = 0, failed = 1), report)
        assertEquals(listOf(record), source.remaining)
    }

    private class FakeCredentialStore(
        private val readBackOverride: String? = null,
    ) : CredentialStore {
        private val values = mutableMapOf<Pair<String, CredentialField>, String>()

        override fun put(connectionId: String, field: CredentialField, value: String) {
            values[connectionId to field] = value
        }

        override fun get(connectionId: String, field: CredentialField): String? =
            readBackOverride ?: values[connectionId to field]

        override fun removeAll(connectionId: String) {
            values.keys.removeAll { it.first == connectionId }
        }
    }

    private class FakeMigrationSource(
        records: List<LegacyCredential>,
        private val failReplacement: Boolean = false,
    ) : CredentialMigrationSource {
        val remaining = records.toMutableList()
        val replaced = mutableListOf<LegacyCredential>()

        override suspend fun findLegacyCredentials(): List<LegacyCredential> = remaining.toList()

        override suspend fun replaceWithMarker(credential: LegacyCredential) {
            if (failReplacement) error("database write failed")
            check(remaining.remove(credential))
            replaced += credential
        }
    }
}
