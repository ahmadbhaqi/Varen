package com.agentworkspace.shell.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agentworkspace.shell.theme.AppTheme

/**
 * The workspace top bar.
 *
 * Shows the brand mark, the current screen title, and the model
 * status chip. The model is ALWAYS visible per the product rules.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceTopBar(
    currentRoute: String?,
    showModelChip: Boolean,
) {
    val title = when (currentRoute) {
        "Home" -> "Chat"
        "History" -> "Activity"
        "usage" -> "Usage"
        "Settings" -> "Settings"
        "Connections" -> "Connections"
        "Projects" -> "Projects"
        "model_catalog" -> "Models"
        "Mcp" -> "UI Studio"
        else -> "Workspace"
    }

    Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BrandMark(size = 30.dp)
                    Spacer(Modifier.width(11.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = AppTheme.colors.textPrimary,
                        maxLines = 1,
                    )
                }
            },
            actions = {
                if (showModelChip) {
                    Spacer(Modifier.width(8.dp))
                    ModelStatusChip(modifier = Modifier.width(150.dp))
                    Spacer(Modifier.width(12.dp))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = AppTheme.colors.textPrimary,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A thin hairline rule under the top bar. */
@Composable
fun TopBarAccent() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(AppTheme.colors.border),
    )
}
