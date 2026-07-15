package com.agentworkspace.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `agent_runs` (
                `id` TEXT NOT NULL,
                `taskId` TEXT NOT NULL,
                `projectId` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `connectionId` TEXT NOT NULL,
                `providerModelId` TEXT NOT NULL,
                `workspaceId` TEXT NOT NULL,
                `configurationJson` TEXT NOT NULL,
                `leaseOwnerId` TEXT,
                `leaseExpiresAt` INTEGER,
                `heartbeatAt` INTEGER,
                `revision` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `lastErrorCode` TEXT,
                `lastErrorMessage` TEXT,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_runs_taskId` ON `agent_runs` (`taskId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_runs_projectId` ON `agent_runs` (`projectId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_runs_status` ON `agent_runs` (`status`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_agent_runs_projectId_status` " +
                "ON `agent_runs` (`projectId`, `status`)",
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `run_commands` (
                `id` TEXT NOT NULL,
                `runId` TEXT NOT NULL,
                `kind` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `payloadJson` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `claimedAt` INTEGER,
                `completedAt` INTEGER,
                `errorMessage` TEXT,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`runId`) REFERENCES `agent_runs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_run_commands_runId` ON `run_commands` (`runId`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_run_commands_status_createdAt` " +
                "ON `run_commands` (`status`, `createdAt`)",
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `run_events` (
                `id` TEXT NOT NULL,
                `runId` TEXT NOT NULL,
                `sequence` INTEGER NOT NULL,
                `kind` TEXT NOT NULL,
                `payloadJson` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`runId`) REFERENCES `agent_runs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_run_events_runId_sequence` " +
                "ON `run_events` (`runId`, `sequence`)",
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `run_messages` (
                `id` TEXT NOT NULL,
                `runId` TEXT NOT NULL,
                `sequence` INTEGER NOT NULL,
                `role` TEXT NOT NULL,
                `content` TEXT NOT NULL,
                `toolCallId` TEXT,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`runId`) REFERENCES `agent_runs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_run_messages_runId_sequence` " +
                "ON `run_messages` (`runId`, `sequence`)",
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `approval_requests` (
                `id` TEXT NOT NULL,
                `runId` TEXT NOT NULL,
                `actionType` TEXT NOT NULL,
                `label` TEXT NOT NULL,
                `reason` TEXT NOT NULL,
                `risk` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `requestedAt` INTEGER NOT NULL,
                `resolvedAt` INTEGER,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`runId`) REFERENCES `agent_runs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_approval_requests_runId` " +
                "ON `approval_requests` (`runId`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_approval_requests_runId_status` " +
                "ON `approval_requests` (`runId`, `status`)",
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `side_effects` (
                `id` TEXT NOT NULL,
                `runId` TEXT NOT NULL,
                `toolCallId` TEXT,
                `idempotencyKey` TEXT NOT NULL,
                `kind` TEXT NOT NULL,
                `target` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `resultJson` TEXT,
                `startedAt` INTEGER,
                `completedAt` INTEGER,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`runId`) REFERENCES `agent_runs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_side_effects_runId` ON `side_effects` (`runId`)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_side_effects_idempotencyKey` " +
                "ON `side_effects` (`idempotencyKey`)",
        )
    }
}
