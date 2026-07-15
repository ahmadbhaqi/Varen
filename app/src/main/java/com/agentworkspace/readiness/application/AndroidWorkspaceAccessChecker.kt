package com.agentworkspace.readiness.application

import android.content.Context
import android.net.Uri
import com.agentworkspace.data.model.Project
import com.agentworkspace.github.isGitHubProjectPath
import com.agentworkspace.readiness.domain.WorkspaceAccessChecker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidWorkspaceAccessChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) : WorkspaceAccessChecker {
    override fun canAccess(project: Project): Boolean {
        if (isGitHubProjectPath(project.path)) return true

        val uri = runCatching { Uri.parse(project.path) }.getOrNull()
            ?.takeIf { it.scheme.equals("content", ignoreCase = true) }
            ?: return false
        return context.contentResolver.persistedUriPermissions.any { grant ->
            grant.uri == uri && grant.isReadPermission && grant.isWritePermission
        }
    }
}
