package com.agentworkspace.data.security

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class CredentialMutationCoordinatorTest {
    @Test
    fun `normal credential update waits for migration critical section`() = runTest {
        val coordinator = CredentialMutationCoordinator()
        val migrationStarted = CompletableDeferred<Unit>()
        val finishMigration = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()

        val migration = launch {
            coordinator.withLock {
                order += "migration-start"
                migrationStarted.complete(Unit)
                finishMigration.await()
                order += "migration-end"
            }
        }
        migrationStarted.await()
        val update = launch {
            coordinator.withLock { order += "connection-update" }
        }

        yield()
        assertEquals(listOf("migration-start"), order)

        finishMigration.complete(Unit)
        migration.join()
        update.join()
        assertEquals(
            listOf("migration-start", "migration-end", "connection-update"),
            order,
        )
    }
}
