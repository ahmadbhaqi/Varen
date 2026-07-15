package com.agentworkspace.readiness.application

import com.agentworkspace.github.isGitHubProjectPath
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidWorkspaceAccessCheckerTest {
    @Test
    fun `canonical github project path is recognized`() {
        assertTrue(isGitHubProjectPath("github://owner/repo?branch=main"))
    }

    @Test
    fun `github project path requires an owner and repository`() {
        assertFalse(isGitHubProjectPath("github://owner"))
        assertFalse(isGitHubProjectPath("github:///repo"))
        assertFalse(isGitHubProjectPath("github://owner/"))
    }

    @Test
    fun `github project path requires the canonical lowercase scheme`() {
        assertFalse(isGitHubProjectPath("GitHub://owner/repo?branch=main"))
    }

    @Test
    fun `persisted permission must match the exact workspace uri`() {
        assertFalse(
            hasPersistedWorkspaceAccess(
                workspaceUri = WORKSPACE_URI,
                permissions = listOf(permission(uri = "content://other", canRead = true, canWrite = true)),
            ),
        )
    }

    @Test
    fun `read only persisted permission is insufficient`() {
        assertFalse(
            hasPersistedWorkspaceAccess(
                workspaceUri = WORKSPACE_URI,
                permissions = listOf(permission(canRead = true, canWrite = false)),
            ),
        )
    }

    @Test
    fun `write only persisted permission is insufficient`() {
        assertFalse(
            hasPersistedWorkspaceAccess(
                workspaceUri = WORKSPACE_URI,
                permissions = listOf(permission(canRead = false, canWrite = true)),
            ),
        )
    }

    @Test
    fun `matching read and write persisted permission grants access`() {
        assertTrue(
            hasPersistedWorkspaceAccess(
                workspaceUri = WORKSPACE_URI,
                permissions = listOf(permission(canRead = true, canWrite = true)),
            ),
        )
    }

    private fun permission(
        uri: String = WORKSPACE_URI,
        canRead: Boolean,
        canWrite: Boolean,
    ) = PersistedWorkspacePermission(
        uri = uri,
        canRead = canRead,
        canWrite = canWrite,
    )

    private companion object {
        const val WORKSPACE_URI = "content://workspace"
    }
}
