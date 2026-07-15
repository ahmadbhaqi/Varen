package com.agentworkspace

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandIdentityContractTest {
    @Test
    fun publicProductIdentityIsVaren() {
        assertTrue(projectFile("app/src/main/res/values/strings.xml").readText().contains(">Varen</string>"))
        assertTrue(projectFile("README.md").readText().startsWith("# Varen"))
        assertTrue(projectFile("settings.gradle.kts").readText().contains("rootProject.name = \"Varen\""))

        val drawer = projectFile(
            "app/src/main/java/com/agentworkspace/shell/components/AdaptiveWorkspaceDrawer.kt",
        ).readText()
        assertTrue(drawer.contains("text = \"Varen\""))
        assertFalse(drawer.contains("text = \"Agent Workspace\""))
    }

    @Test
    fun compatibilityIdentifiersRemainStable() {
        val build = projectFile("app/build.gradle.kts").readText()
        val manifest = projectFile("app/src/main/AndroidManifest.xml").readText()
        val database = projectFile(
            "app/src/main/java/com/agentworkspace/data/db/AgentWorkspaceDatabase.kt",
        ).readText()

        assertTrue(build.contains("applicationId = \"com.agentworkspace\""))
        assertTrue(manifest.contains("android:scheme=\"agentworkspace\""))
        assertTrue(database.contains("abstract class AgentWorkspaceDatabase"))
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
