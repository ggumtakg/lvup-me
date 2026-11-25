package com.lvupme.app.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lvupme.app.R

/**
 * Full-screen Level-Up Peaks illustrated wallpaper that adapts automatically to Dark/Light theme,
 * with a subtle contrast scrim so cards and text remain razor-sharp.
 */
@Composable
fun LvUpBackground(
    modifier: Modifier = Modifier,
    scrimAlphaOverride: Float? = null
) {
    val isDark = MaterialTheme.game.isDark
    val resId = if (isDark) R.drawable.bg_peaks_dark else R.drawable.bg_peaks_light
    val scrimColor = if (isDark) {
        Color(0xFF0A1419).copy(alpha = scrimAlphaOverride ?: 0.24f)
    } else {
        Color(0xFFFFFFFF).copy(alpha = scrimAlphaOverride ?: 0.14f)
    }

    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = resId),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scrimColor)
        )
    }
}

enum class TactileStyle {
    EMERALD,
    SKY,
    VIOLET,
    GOLD,
    CORAL,
    NEUTRAL
}

/**
 * Iconic Duolingo-inspired chunky 3D button with a darker bottom lip that depresses on press.
 */
@Composable
fun TactileButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: TactileStyle = TactileStyle.EMERALD,
    enabled: Boolean = true,
    height: Dp = 52.dp,
    content: @Composable RowScope.() -> Unit
) {
    val g = MaterialTheme.game
    val (rawFace, rawLip, rawText) = when (style) {
        TactileStyle.EMERALD -> Triple(g.emerald, g.emeraldLip, Color.White)
        TactileStyle.SKY -> Triple(g.sky, g.skyLip, Color.White)
        TactileStyle.VIOLET -> Triple(g.violet, g.violetLip, Color.White)
        TactileStyle.GOLD -> Triple(g.gold, g.goldLip, Color.White)
        TactileStyle.CORAL -> Triple(g.coral, g.coralLip, Color.White)
        TactileStyle.NEUTRAL -> Triple(g.cardBg, g.cardBorder, MaterialTheme.colorScheme.onSurface)
    }

    val faceColor = if (enabled) rawFace else g.trackBg
    val lipColor = if (enabled) rawLip else g.cardLip
    val textColor = if (enabled) rawText else MaterialTheme.colorScheme.onSurfaceVariant

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val lipHeight = 4.dp
    val pressOffset by animateDpAsState(
        targetValue = if (isPressed && enabled) lipHeight else 0.dp,
        animationSpec = tween(durationMillis = 70),
        label = "tactile_press"
    )
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = modifier
            .height(height + lipHeight)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
    ) {
        // Bottom 3D lip
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .align(Alignment.BottomCenter)
                .clip(shape)
                .background(lipColor)
        )
        // Top tactile face
        Surface(
            color = faceColor,
            contentColor = textColor,
            shape = shape,
            border = if (style == TactileStyle.NEUTRAL) BorderStroke(1.5.dp, g.cardBorder) else null,
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .offset(y = pressOffset)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    }
}

/**
 * Chunky gamified card with crisp 2dp border and subtle bottom lip depth.
 */
@Composable
fun GamifiedCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.game.cardBg,
    borderColor: Color = MaterialTheme.game.cardBorder,
    lipColor: Color = borderColor,
    cornerRadius: Dp = 20.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(lipColor)
            .padding(bottom = 3.dp)
            .clip(shape)
            .background(containerColor)
            .border(width = 1.5.dp, color = borderColor, shape = shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                content = content
            )
        }
    }
}

/**
 * Glossy thick gamified progress bar with clean neutral track and top highlight shine.
 */
@Composable
fun ChunkyProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    fillColor: Color = MaterialTheme.game.emerald,
    trackColor: Color = MaterialTheme.game.trackBg,
    height: Dp = 14.dp
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 380),
        label = "chunky_progress"
    )
    val shape = RoundedCornerShape(50)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(trackColor)
    ) {
        if (animated > 0.01f) {
            val visualFraction = animated.coerceAtLeast(0.06f)
            Box(
                modifier = Modifier
                    .fillMaxWidth(visualFraction)
                    .fillMaxHeight()
                    .clip(shape)
                    .background(fillColor)
            ) {
                // Specular gloss highlight stripe
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                        .height(3.5.dp)
                        .clip(shape)
                        .background(Color.White.copy(alpha = 0.28f))
                )
            }
        }
    }
}

enum class ChoiceVisualState {
    IDLE,
    CORRECT,
    WRONG,
    DIMMED
}

/**
 * Tactile A/B/C/D answer choice card for Study, Exam, LevelTest, and LockScreen.
 */
@Composable
fun ChoiceTileCard(
    letter: String,
    text: String,
    state: ChoiceVisualState,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val g = MaterialTheme.game
    val bgColor by animateColorAsState(
        targetValue = when (state) {
            ChoiceVisualState.IDLE -> g.cardBg
            ChoiceVisualState.CORRECT -> g.emeraldSoft
            ChoiceVisualState.WRONG -> g.coralSoft
            ChoiceVisualState.DIMMED -> g.cardBg.copy(alpha = 0.55f)
        },
        label = "choice_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = when (state) {
            ChoiceVisualState.IDLE -> g.cardBorder
            ChoiceVisualState.CORRECT -> g.emerald
            ChoiceVisualState.WRONG -> g.coral
            ChoiceVisualState.DIMMED -> g.cardBorder.copy(alpha = 0.4f)
        },
        label = "choice_border"
    )
    val badgeBg by animateColorAsState(
        targetValue = when (state) {
            ChoiceVisualState.IDLE -> g.trackBg
            ChoiceVisualState.CORRECT -> g.emerald
            ChoiceVisualState.WRONG -> g.coral
            ChoiceVisualState.DIMMED -> g.trackBg.copy(alpha = 0.5f)
        },
        label = "choice_badge"
    )
    val badgeTextColor = when (state) {
        ChoiceVisualState.CORRECT, ChoiceVisualState.WRONG -> Color.White
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(borderColor)
            .padding(bottom = 3.dp)
            .clip(shape)
            .background(bgColor)
            .border(width = 1.5.dp, color = borderColor, shape = shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(badgeBg),
                contentAlignment = Alignment.Center
            ) {
                when (state) {
                    ChoiceVisualState.CORRECT -> Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    ChoiceVisualState.WRONG -> Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    else -> Text(
                        text = letter,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = badgeTextColor
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (state == ChoiceVisualState.DIMMED) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun GamePillBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = CircleShape,
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.25f)),
        modifier = modifier.then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}
