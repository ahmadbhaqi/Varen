package com.agentworkspace.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.agentworkspace.data.db.migration.MIGRATION_2_3
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration2To3Test {
    private val databaseName = "migration-2-3-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AgentWorkspaceDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrationPreservesLegacyDataAndCreatesRuntimeTables() {
        helper.createDatabase(databaseName, 2).apply {
            execSQL(
                """
                INSERT INTO projects(
                    id, name, path, createdAt, updatedAt, trustMode,
                    preferredModelId, preferredConnectionId, isActive, description
                ) VALUES('p1', 'Demo', 'content://demo', 1, 1, 'MANUAL', NULL, NULL, 1, '')
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(databaseName, 3, true, MIGRATION_2_3).use { db ->
            db.query("SELECT name FROM projects WHERE id = 'p1'").use { cursor ->
                check(cursor.moveToFirst())
                check(cursor.getString(0) == "Demo")
            }
            db.query("SELECT COUNT(*) FROM agent_runs").use { cursor ->
                check(cursor.moveToFirst())
                check(cursor.getInt(0) == 0)
            }
        }
    }
}
