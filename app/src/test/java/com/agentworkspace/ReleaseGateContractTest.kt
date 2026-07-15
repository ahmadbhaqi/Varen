package com.agentworkspace

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseGateContractTest {
    @Test
    fun androidBuildTargetsApi35() {
        val build = projectFile("app/build.gradle.kts").readText()

        assertTrue(build.contains("compileSdk = 35"))
        assertTrue(build.contains("targetSdk = 35"))
    }

    @Test
    fun continuousIntegrationRunsTheFullAndroidReleaseGate() {
        val workflow = projectFile(".github/workflows/android-release-gate.yml").readText()

        assertTrue(workflow.contains("actions/checkout@v7"))
        assertTrue(workflow.contains("actions/setup-java@v5"))
        assertTrue(workflow.contains("android-actions/setup-android@v4"))
        assertTrue(workflow.contains("platforms;android-35"))
        assertTrue(workflow.contains("testDebugUnitTest"))
        assertTrue(workflow.contains("compileDebugAndroidTestKotlin"))
        assertTrue(workflow.contains("assembleDebug"))
        assertTrue(workflow.contains("lintDebug"))
        assertTrue(workflow.contains("assembleRelease"))
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
