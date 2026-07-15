package com.agentworkspace.readiness.application

import android.content.Context
import android.net.Uri
import com.agentworkspace.data.model.Project
import com.agentworkspace.github.isGitHubProjectPath
import com.agentworkspace.readiness.domain.WorkspaceAccessChecker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

internal data class PersistedWorkspacePermission(
    val uri: String,
    val canRead: Boolean,
    val canWrite: Boolean,
)

internal fun hasPersistedWorkspaceAccess(
    workspaceUri: String,
    permissions: Iterable<PersistedWorkspacePermission>,
): Boolean = permissions.any { permission ->
    permission.uri == workspaceUri && permission.canRead && permission.canWrite
}

@Singleton
class AndroidWorkspaceAccessChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) : WorkspaceAccessChecker {
    override fun canAccess(project: Project): Boolean {
        if (isGitHubProjectPath(project.path)) return true

        val uri = runCatching { Uri.parse(project.path) }.getOrNull()
            ?.takeIf { it.scheme.equals("content", ignoreCase = true) }
            ?: return false
        val permissions = context.contentResolver.persistedUriPermissions.map { grant ->
            PersistedWorkspacePermission(
                uri = grant.uri.toString(),
                canRead = grant.isReadPermission,
                canWrite = grant.isWritePermission,
            )
        }
        return hasPersistedWorkspaceAccess(uri.toString(), permissions)
    }
}
