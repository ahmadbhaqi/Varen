package com.agentworkspace

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceContractTest {
    @Test
    fun usageDashboardObservesABoundedRecentWindow() {
        val source = projectFile(
            "app/src/main/java/com/agentworkspace/usage/UsageViewModel.kt",
        ).readText()

        assertTrue(source.contains("const val MAX_USAGE_RECORDS = 1_000"))
        assertTrue(source.contains("getRecentUsage(MAX_USAGE_RECORDS)"))
        assertFalse(source.contains("getAllUsage()"))
    }

    @Test
    fun taskConversationDoesNotDeserializeAnUnboundedCheckpointArchive() {
        val dao = projectFile(
            "app/src/main/java/com/agentworkspace/data/db/dao/Daos.kt",
        ).readText()

        assertTrue(dao.contains("const val MAX_TASK_CHECKPOINTS = 20"))
        assertTrue(dao.contains("ORDER BY createdAt DESC LIMIT :limit"))
        assertTrue(dao.contains("limit: Int = MAX_TASK_CHECKPOINTS"))
    }

    @Test
    fun releaseBuildShrinksCodeAndResources() {
        val build = projectFile("app/build.gradle.kts").readText()

        assertTrue(build.contains("isMinifyEnabled = true"))
        assertTrue(build.contains("isShrinkResources = true"))
    }

    @Test
    fun reusableStatusComponentsDoNotRunPermanentAnimationLoops() {
        val premium = projectFile(
            "app/src/main/java/com/agentworkspace/shell/components/PremiumComponents.kt",
        ).readText()
        val modern = projectFile(
            "app/src/main/java/com/agentworkspace/shell/components/modern/ModernComponents.kt",
        ).readText()

        assertFalse(premium.contains("rememberInfiniteTransition"))
        assertFalse(modern.contains("rememberInfiniteTransition"))
    }

    @Test
    fun rootScaffoldDoesNotSubscribeToDrawerOnlyState() {
        val source = projectFile(
            "app/src/main/java/com/agentworkspace/shell/AgentWorkspaceScaffold.kt",
        ).readText()
        val scaffoldBody = source
            .substringAfter("fun AgentWorkspaceScaffold() {")
            .substringBefore("private fun WorkspaceDrawerStateContent")

        assertFalse(scaffoldBody.contains("HomeViewModel"))
        assertFalse(scaffoldBody.contains("collectAsStateWithLifecycle"))
    }

    @Test
    fun homeStateCompositionDoesNotRunDatabaseLookupsInsideCombine() {
        val source = projectFile(
            "app/src/main/java/com/agentworkspace/workspace/home/HomeViewModel.kt",
        ).readText()
        val combineTransform = source
            .substringAfter("usageRepository.getTotalUsage(),")
            .substringBefore("}.collect")

        assertFalse(combineTransform.contains(".first()"))
        assertFalse(combineTransform.contains("getModelById("))
    }

    @Test
    fun conversationLazyListDoesNotAnimateItsEntireLayoutOnEveryUpdate() {
        val home = projectFile(
            "app/src/main/java/com/agentworkspace/workspace/home/HomeScreen.kt",
        ).readText()

        assertFalse(home.contains(".animateContentSize()"))
    }

    @Test
    fun checkpointsAreContextualInsteadOfAStandaloneDestination() {
        val navGraph = projectFile(
            "app/src/main/java/com/agentworkspace/shell/navigation/NavGraph.kt",
        ).readText()
        val drawer = projectFile(
            "app/src/main/java/com/agentworkspace/shell/components/AdaptiveWorkspaceDrawer.kt",
        ).readText()

        assertFalse(navGraph.contains("composable(\"Checkpoints\")"))
        assertFalse(navGraph.contains("checkpoint/{checkpointId}"))
        assertFalse(drawer.contains("\"Checkpoints\","))
    }

    @Test
    fun historyRowsRemainLazyInsteadOfBeingNestedInOneColumn() {
        val history = projectFile(
            "app/src/main/java/com/agentworkspace/history/HistoryScreen.kt",
        ).readText()

        assertFalse(history.contains("entries.forEachIndexed"))
        assertTrue(Regex("""items\(\s*items = entries""").containsMatchIn(history))
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
