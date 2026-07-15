package com.agentworkspace.shell.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.agentworkspace.shell.theme.AppTheme
import com.agentworkspace.shell.theme.MonoCaption
import com.agentworkspace.shell.theme.MonoMetric

@Composable
fun Modifier.glowBackground(
    color: Color,
    radius: Dp = 120.dp,
    alpha: Float = 0.18f,
): Modifier = this

@Composable
fun GlowCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
    glow: Color = Color.White,
    glowAlpha: Float = 0.1f,
    content: @Composable ColumnScope.() -> Unit,
) {
    EnterprisePanel(
        modifier = modifier,
        accent = glow.copy(alpha = glowAlpha),
        content = content,
        contentPadding = PaddingValues(14.dp),
    )
}

@Composable
fun EnterprisePanel(
    modifier: Modifier = Modifier,
    accent: Color = AppTheme.colors.border,
    selected: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val border = if (selected) AppTheme.colors.borderStrong else AppTheme.colors.border
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) AppTheme.colors.elevated else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (selected) accent.copy(alpha = 0.55f) else border),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            content = content,
        )
    }
}

@Composable
fun EnterpriseHeader(
    eyebrow: String,
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    accent: Color = AppTheme.colors.accent,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    EnterprisePanel(
        modifier = modifier,
        accent = accent,
        selected = true,
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon = icon, accent = accent, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = eyebrow,
                    style = MaterialTheme.typography.labelSmall,
                    color = AppTheme.colors.textSecondary,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = AppTheme.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = trailing,
            )
        }
    }
}

@Composable
fun IconTile(
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(MaterialTheme.shapes.small)
            .background(accent.copy(alpha = 0.12f))
            .border(1.dp, accent.copy(alpha = 0.32f), MaterialTheme.shapes.small),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(size * 0.46f),
        )
    }
}

@Composable
fun InsightStrip(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, AppTheme.colors.border, MaterialTheme.shapes.medium)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun InsightStat(
    label: String,
    value: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, AppTheme.colors.border, MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = label,
                style = MonoCaption,
                color = AppTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun CapabilityChip(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.28f), MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Icon(it, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = text,
            style = MonoCaption,
            color = color,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun EmptyStatePanel(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    accent: Color = AppTheme.colors.accent,
    action: @Composable (() -> Unit)? = null,
) {
    EnterprisePanel(modifier = modifier, accent = accent, selected = false, contentPadding = PaddingValues(24.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            IconTile(icon = icon, accent = accent, size = 44.dp)
            Spacer(Modifier.size(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = AppTheme.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            action?.let {
                Spacer(Modifier.size(14.dp))
                it()
            }
        }
    }
}

@Composable
fun MetricTile(
    icon: ImageVector,
    label: String,
    value: String,
    accent: Color = AppTheme.colors.accent,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, AppTheme.colors.border, MaterialTheme.shapes.medium)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon = icon, accent = accent, size = 26.dp)
            Spacer(Modifier.width(6.dp))
            Text(text = label, style = MonoCaption, color = AppTheme.colors.textSecondary, maxLines = 1)
        }
        Spacer(Modifier.size(8.dp))
        Text(text = value, style = MonoMetric, color = AppTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun BrandMark(modifier: Modifier = Modifier, size: Dp = 32.dp) {
    WorkspaceBrandMark(modifier = modifier, size = size)
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    WorkspaceSectionTitle(text = text, modifier = modifier)
}

@Composable
fun StatusPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    pulse: Boolean = false,
) {
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.28f), MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (pulse) {
            PulsingDot(color = color, size = 6.dp)
        } else {
            Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        }
        Spacer(Modifier.width(6.dp))
        Text(text = text, style = MonoCaption, color = color, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
fun PulsingDot(color: Color, size: Dp = 8.dp, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        0.90f,
        1.12f,
        infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        "pulseScale",
    )
    val alpha by transition.animateFloat(
        1f,
        0.4f,
        infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Reverse),
        "pulseAlpha",
    )
    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .alpha(alpha)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
fun ShimmerBox(modifier: Modifier = Modifier, cornerRadius: Dp = 8.dp) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        0.45f,
        0.78f,
        infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        "loadingAlpha",
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)),
    )
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    EnterprisePanel(modifier = modifier, contentPadding = PaddingValues(12.dp), content = content)
}

@Composable
fun GradientButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val container = if (enabled) AppTheme.colors.accent else MaterialTheme.colorScheme.surfaceVariant
    val content = if (enabled) AppTheme.colors.textOnBrand else AppTheme.colors.textDisabled
    val border = if (enabled) AppTheme.colors.accent else AppTheme.colors.border
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(MaterialTheme.shapes.small)
            .background(container)
            .border(1.dp, border, MaterialTheme.shapes.small)
            .bounceClick(enabled = enabled, pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = content, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ProviderLogo(
    providerName: String,
    modifier: Modifier = Modifier,
) {
    val name = providerName.lowercase()
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, AppTheme.colors.border, MaterialTheme.shapes.small)
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f

            when {
                // Gemini/Google Sparkle Star
                name.contains("gemini") || name.contains("google") -> {
                    val path = Path().apply {
                        moveTo(cx, 0f)
                        cubicTo(cx, cy * 0.5f, cx * 1.5f, cy, w, cy)
                        cubicTo(cx * 1.5f, cy, cx, cy * 1.5f, cx, h)
                        cubicTo(cx, cy * 1.5f, cx * 0.5f, cy, 0f, cy)
                        cubicTo(cx * 0.5f, cy, cx, cy * 0.5f, cx, 0f)
                        close()
                    }
                    drawPath(path, Color.White)
                }
                // OpenAI logo: Spiral/Flower geometry
                name.contains("openai") || name.contains("chatgpt") -> {
                    for (i in 0 until 6) {
                        val angle = i * (Math.PI / 3)
                        val rx = cx + (cx * 0.28f * Math.cos(angle)).toFloat()
                        val ry = cy + (cy * 0.28f * Math.sin(angle)).toFloat()
                        drawCircle(
                            color = Color.White,
                            radius = w * 0.16f,
                            center = Offset(rx, ry),
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }
                    drawCircle(color = Color.White, radius = w * 0.10f)
                }
                // Anthropic A logo
                name.contains("anthropic") || name.contains("claude") -> {
                    val path = Path().apply {
                        moveTo(cx, h * 0.15f)
                        lineTo(w * 0.85f, h * 0.85f)
                        lineTo(w * 0.70f, h * 0.85f)
                        lineTo(cx * 1.25f, h * 0.58f)
                        lineTo(cx * 0.75f, h * 0.58f)
                        lineTo(w * 0.30f, h * 0.85f)
                        lineTo(w * 0.15f, h * 0.85f)
                        close()
                    }
                    drawPath(path, Color.White)

                    drawLine(
                        color = Color.Black,
                        start = Offset(cx * 0.74f, h * 0.60f),
                        end = Offset(cx * 1.26f, h * 0.60f),
                        strokeWidth = h * 0.12f
                    )
                    drawLine(
                        color = Color.White,
                        start = Offset(cx * 0.70f, h * 0.64f),
                        end = Offset(cx * 1.30f, h * 0.64f),
                        strokeWidth = 1.5.dp.toPx()
                    )
                }
                // Cohere Logo (stylized cell/honeycomb outline)
                name.contains("cohere") -> {
                    val path = Path().apply {
                        moveTo(cx, h * 0.15f)
                        lineTo(w * 0.8f, h * 0.35f)
                        lineTo(w * 0.8f, h * 0.65f)
                        lineTo(cx, h * 0.85f)
                        lineTo(w * 0.2f, h * 0.65f)
                        lineTo(w * 0.2f, h * 0.35f)
                        close()
                    }
                    drawPath(path, Color.White, style = Stroke(width = 1.5.dp.toPx()))
                    drawCircle(color = Color.White, radius = w * 0.08f, center = Offset(cx, cy))
                }
                // Groq (speedbolt/flash)
                name.contains("groq") -> {
                    val path = Path().apply {
                        moveTo(w * 0.65f, 0f)
                        lineTo(w * 0.25f, h * 0.55f)
                        lineTo(w * 0.55f, h * 0.55f)
                        lineTo(w * 0.35f, h)
                        lineTo(w * 0.75f, h * 0.45f)
                        lineTo(w * 0.45f, h * 0.45f)
                        close()
                    }
                    drawPath(path, Color.White)
                }
                // Custom / Local API bracket </>
                else -> {
                    val p1 = Path().apply {
                        moveTo(w * 0.3f, h * 0.25f)
                        lineTo(w * 0.1f, cy)
                        lineTo(w * 0.3f, h * 0.75f)
                    }
                    val p2 = Path().apply {
                        moveTo(w * 0.7f, h * 0.25f)
                        lineTo(w * 0.9f, cy)
                        lineTo(w * 0.7f, h * 0.75f)
                    }
                    drawPath(p1, Color.White, style = Stroke(width = 1.5.dp.toPx()))
                    drawPath(p2, Color.White, style = Stroke(width = 1.5.dp.toPx()))
                    drawLine(
                        color = Color.White,
                        start = Offset(w * 0.6f, h * 0.2f),
                        end = Offset(w * 0.4f, h * 0.8f),
                        strokeWidth = 1.5.dp.toPx()
                    )
                }
            }
        }
    }
}
